import base64
import io
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

sys.path.insert(0, str(Path(__file__).resolve().parent))

import publish_ota

CLIENT_CONTRACT = (
    "\nprivate const val MAX_NOTES_LENGTH = 4_000\n"
    'notes = json.optString("notes").take(MAX_NOTES_LENGTH),\n'
)


class ReleaseLineageTest(unittest.TestCase):
    def setUp(self):
        self.key = Ed25519PrivateKey.generate()
        self.public_der = self.key.public_key().public_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.client_source = Path(self.temporary.name) / "UpdateClient.kt"
        self.client_source.write_text(
            'private const val PUBLIC_KEY = "'
            + base64.b64encode(self.public_der).decode("ascii")
            + '"'
            + CLIENT_CONTRACT,
            encoding="utf-8",
        )

    def manifest(self):
        url = f"{publish_ota.UPDATE_ORIGIN}/veilark/windows/Veilark-0.3.18.exe"
        payload = f"318\n0.3.18\n{url}\nDEADBEEF\n123".encode("utf-8")
        notes = "Previous release"
        return {
            "versionCode": 318,
            "versionName": "0.3.18",
            "installerUrl": url,
            "sha256": "DEADBEEF",
            "size": 123,
            "notes": notes,
            "signature": base64.b64encode(self.key.sign(payload)).decode("ascii"),
            "notesSignature": base64.b64encode(
                self.key.sign(payload + b"\n" + notes.encode("utf-8"))
            ).decode("ascii"),
        }

    def verify(self, manifest, candidate=319):
        body = io.BytesIO(json.dumps(manifest).encode("utf-8"))
        with patch.object(publish_ota, "CLIENT_SOURCE", self.client_source), patch.object(
            publish_ota, "urlopen", return_value=body
        ):
            publish_ota.verify_release_lineage(self.key, candidate)

    def test_valid_predecessor(self):
        self.verify(self.manifest())

    def test_rejects_non_increasing_version(self):
        with self.assertRaisesRegex(RuntimeError, "non-increasing"):
            self.verify(self.manifest(), candidate=318)

    def test_rejects_changed_signed_notes(self):
        manifest = self.manifest()
        manifest["notes"] = "tampered"
        with self.assertRaises(Exception):
            self.verify(manifest)

    def test_rejects_foreign_client_trust_root(self):
        self.client_source.write_text(
            'private const val PUBLIC_KEY = "'
            + base64.b64encode(
                Ed25519PrivateKey.generate().public_key().public_bytes(
                    encoding=serialization.Encoding.DER,
                    format=serialization.PublicFormat.SubjectPublicKeyInfo,
                )
            ).decode("ascii")
            + '"'
            + CLIENT_CONTRACT,
            encoding="utf-8",
        )
        with self.assertRaisesRegex(RuntimeError, "trust root"):
            self.verify(self.manifest())

    def test_rejects_client_notes_limit_drift(self):
        source = self.client_source.read_text(encoding="utf-8")
        self.client_source.write_text(source.replace("4_000", "3_000"), encoding="utf-8")
        with self.assertRaisesRegex(RuntimeError, "MAX_NOTES_LENGTH"):
            self.verify(self.manifest())


class RealClientContractTest(unittest.TestCase):
    def test_repository_client_matches_publisher(self):
        publish_ota.verify_client_contract(publish_ota.CLIENT_SOURCE.read_text(encoding="utf-8"))


class NotesLengthTest(unittest.TestCase):
    def test_counts_utf16_code_units(self):
        self.assertEqual(publish_ota.utf16_length("abc"), 3)
        self.assertEqual(publish_ota.utf16_length("\U0001F680"), 2)
        self.assertEqual(publish_ota.utf16_length("Привет"), 6)

    def test_accepts_limit_in_code_units(self):
        publish_ota.validate_notes("a" * publish_ota.MAX_NOTES_LENGTH)
        publish_ota.validate_notes("\U0001F680" * (publish_ota.MAX_NOTES_LENGTH // 2))

    def test_rejects_astral_notes_that_python_counts_as_short(self):
        notes = "\U0001F680" * 2_001  # 2 001 code points, 4 002 UTF-16 units
        self.assertLessEqual(len(notes), publish_ota.MAX_NOTES_LENGTH)
        with self.assertRaisesRegex(ValueError, "UTF-16"):
            publish_ota.validate_notes(notes)

    def test_rejects_unpaired_surrogate(self):
        with self.assertRaisesRegex(ValueError, "surrogate"):
            publish_ota.validate_notes("broken \ud800")

    def test_client_truncation_matches_kotlin_take(self):
        notes = "a" * 3_999 + "\U0001F680"
        truncated = publish_ota.client_truncated_notes(notes)
        self.assertNotEqual(truncated, notes)
        self.assertEqual(publish_ota.utf16_length(truncated), publish_ota.MAX_NOTES_LENGTH)

    def manifest(self, **overrides):
        manifest = {
            "versionCode": 322,
            "versionName": "0.3.22",
            "installerUrl": f"{publish_ota.UPDATE_ORIGIN}/veilark/windows/Veilark-0.3.22.exe",
            "sha256": "A" * 64,
            "size": 10,
            "notes": "Notes\nline",
        }
        manifest.update(overrides)
        return manifest

    def test_valid_manifest_is_client_verifiable(self):
        publish_ota.assert_client_verifiable(self.manifest())

    def test_rejects_fields_the_client_would_normalise(self):
        for overrides in (
            {"notes": "\U0001F680" * 2_001},
            {"versionName": "0.3.22 "},
            {"installerUrl": " " + self.manifest()["installerUrl"]},
            {"sha256": "a" * 64},
        ):
            with self.subTest(overrides=overrides), self.assertRaisesRegex(ValueError, "alter"):
                publish_ota.assert_client_verifiable(self.manifest(**overrides))


class VersionIdentityTest(unittest.TestCase):
    def test_formula_matches_ota_version_script(self):
        for name, code in (("0.3.19", 319), ("0.3.20", 320), ("0.3.21", 321), ("0.4.0", 400), ("1.0.0", 10_000)):
            with self.subTest(name=name):
                self.assertEqual(publish_ota.version_code_for(name), code)
                publish_ota.validate_release_identity(code, name)

    def test_rejects_invalid_names(self):
        for name in ("0.4", "00.4.0", "0.100.0", "0.4.100", "0.0.0", "999999.0.0", " 0.3.22", "0.3.22-rc1"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                publish_ota.version_code_for(name)

    def test_rejects_mismatched_code(self):
        with self.assertRaisesRegex(ValueError, "expected 322"):
            publish_ota.validate_release_identity(3022, "0.3.22")
        with self.assertRaisesRegex(ValueError, "expected 400"):
            publish_ota.validate_release_identity(40, "0.4.0")


def minimal_pe(resource: bytes, trailing: bytes = b"") -> bytes:
    """Builds a tiny PE32+ image with one section holding the resource directory."""
    pe_offset = 0x40
    optional_size = 112 + 16 * 8
    section_table = pe_offset + 4 + 20 + optional_size
    raw_pointer = 0x200
    data = bytearray(raw_pointer)
    data[0:2] = b"MZ"
    struct.pack_into("<I", data, 0x3C, pe_offset)
    data[pe_offset : pe_offset + 4] = b"PE\0\0"
    struct.pack_into("<HHIIIHH", data, pe_offset + 4, 0x8664, 1, 0, 0, 0, optional_size, 0x22)
    optional = pe_offset + 24
    struct.pack_into("<H", data, optional, 0x20B)
    struct.pack_into("<I", data, optional + 108, 16)
    struct.pack_into("<II", data, optional + 112 + 2 * 8, 0x1000, len(resource))
    struct.pack_into("<8sIIIIIIHHI", data, section_table, b".rsrc", len(resource), 0x1000, len(resource), raw_pointer, 0, 0, 0, 0, 0)
    return bytes(data) + resource + trailing


def fixed_file_info(major, minor, build, revision) -> bytes:
    return struct.pack(
        "<IIII", publish_ota.PE_FIXED_FILE_INFO_SIGNATURE, 0x10000, (major << 16) | minor, (build << 16) | revision
    ) + b"\0" * 36


class InstallerVersionTest(unittest.TestCase):
    def write(self, content: bytes) -> Path:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "Veilark-0.3.22.exe"
        path.write_bytes(content)
        return path

    def test_reads_wrapper_file_version(self):
        path = self.write(minimal_pe(b"\0" * 16 + fixed_file_info(0, 3, 22, 0)))
        self.assertEqual(publish_ota.validate_installer_version(path, "0.3.22", False), "0.3.22.0")

    def test_rejects_mismatched_file_version(self):
        path = self.write(minimal_pe(fixed_file_info(0, 3, 21, 0)))
        with self.assertRaisesRegex(RuntimeError, "does not match"):
            publish_ota.validate_installer_version(path, "0.3.22", False)

    def test_ignores_versions_outside_resource_section(self):
        # The embedded jpackage payload is a managed resource after .rsrc here.
        path = self.write(minimal_pe(b"\0" * 64, trailing=fixed_file_info(0, 3, 22, 0)))
        with self.assertRaisesRegex(RuntimeError, "no Win32 file version"):
            publish_ota.validate_installer_version(path, "0.3.22", False)
        self.assertIsNone(publish_ota.validate_installer_version(path, "0.3.22", True))

    def test_rejects_non_pe(self):
        path = self.write(b"not an executable")
        with self.assertRaises(ValueError):
            publish_ota.validate_installer_version(path, "0.3.22", False)


if __name__ == "__main__":
    unittest.main()
