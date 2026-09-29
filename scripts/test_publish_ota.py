import base64
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

import publish_ota


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
            + '"',
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
            + '"',
            encoding="utf-8",
        )
        with self.assertRaisesRegex(RuntimeError, "trust root"):
            self.verify(self.manifest())


if __name__ == "__main__":
    unittest.main()
