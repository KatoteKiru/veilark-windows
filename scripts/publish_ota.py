from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import struct
import subprocess
import uuid
from urllib.request import Request, urlopen

import paramiko
from cryptography.hazmat.primitives import serialization


ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parent
OTA_DIR = ROOT / "build" / "ota"
REMOTE_DIR = "/var/www/html/veilark/windows"
UPDATE_ORIGIN = "https://nl2.senyasenyavski.uk:2096"
UPLOAD_CHUNK_SIZE = 256 * 1024
UPLOAD_ATTEMPTS = 8
# Must equal UpdateClient.MAX_NOTES_LENGTH. The client counts Kotlin/Java
# String length, i.e. UTF-16 code units, and truncates before verifying the
# notes signature; the publisher must therefore measure the same unit.
MAX_NOTES_LENGTH = 4_000
MAX_VERSION_COMPONENT = 100
CANONICAL_VERSION_NAME = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)")
# UpdateClient.SAFE_VERSION_NAME
CLIENT_SAFE_VERSION_NAME = re.compile(r"[0-9A-Za-z][0-9A-Za-z._-]{0,63}")
PE_FIXED_FILE_INFO_SIGNATURE = 0xFEEF04BD
PE_RESOURCE_DIRECTORY_INDEX = 2
KNOWN_HOSTS = WORKSPACE / "secrets" / "vpn-production-known-hosts"
DEPLOY_KEY = WORKSPACE / "secrets" / "veilark-automation-ed25519-20260831"
CLIENT_SOURCE = ROOT / "shared" / "src" / "jvmMain" / "kotlin" / "uk" / "senyasenyavski" / "veilark" / "update" / "UpdateClient.kt"


def read_env(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip().strip("\"'")
    return result


def connect_node(env: dict[str, str]) -> paramiko.SSHClient:
    client = paramiko.SSHClient()
    if KNOWN_HOSTS.is_file():
        client.load_host_keys(str(KNOWN_HOSTS))
    client.load_system_host_keys()
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    client.connect(
        env["NETHERLANDS_NEW_HOST"],
        username=env["NETHERLANDS_NEW_USER"],
        key_filename=str(DEPLOY_KEY) if DEPLOY_KEY.is_file() else None,
        password=None if DEPLOY_KEY.is_file() else env["NETHERLANDS_NEW_PASSWORD"],
        look_for_keys=False,
        allow_agent=False,
        timeout=20,
        banner_timeout=20,
        auth_timeout=20,
    )
    client.get_transport().set_keepalive(10)
    return client


def authenticode_status(path: Path) -> str:
    quoted_path = str(path).replace("'", "''")
    command = (
        "Import-Module Microsoft.PowerShell.Security -ErrorAction Stop; "
        f"(Get-AuthenticodeSignature -LiteralPath '{quoted_path}').Status.ToString()"
    )
    environment = os.environ.copy()
    environment["PSModulePath"] = str(
        Path(os.environ.get("SystemRoot", r"C:\Windows"))
        / "System32" / "WindowsPowerShell" / "v1.0" / "Modules"
    )
    result = subprocess.run(
        ["powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", command],
        check=True,
        capture_output=True,
        text=True,
        env=environment,
    )
    return result.stdout.strip()


def version_code_for(version_name: str) -> int:
    """Mirror of scripts/ota-version.ps1 Get-VeilarkVersionCode."""
    match = CANONICAL_VERSION_NAME.fullmatch(version_name)
    if not match:
        raise ValueError("Expected a canonical major.minor.patch version name")
    major, minor, patch = (int(part) for part in match.groups())
    if minor >= MAX_VERSION_COMPONENT or patch >= MAX_VERSION_COMPONENT:
        raise ValueError("Minor and patch must be below 100 for the OTA counter schema")
    code = major * 10_000 + minor * 100 + patch
    if code < 1 or code > 2**31 - 1:
        raise ValueError("OTA counter out of range")
    return code


def validate_release_identity(version_code: int, version_name: str) -> None:
    expected = version_code_for(version_name)
    if version_code != expected:
        raise ValueError(
            f"--version-code {version_code} does not match {version_name} (expected {expected})"
        )
    if not CLIENT_SAFE_VERSION_NAME.fullmatch(version_name):
        raise ValueError("Version name is not accepted by the Windows client")


def utf16_length(text: str) -> int:
    """Length as Kotlin/Java String.length reports it (UTF-16 code units)."""
    return len(text.encode("utf-16-le", errors="surrogatepass")) // 2


def client_truncated_notes(notes: str) -> str:
    """Exactly what UpdateClient does: optString("notes").take(MAX_NOTES_LENGTH)."""
    units = notes.encode("utf-16-le", errors="surrogatepass")[: MAX_NOTES_LENGTH * 2]
    return units.decode("utf-16-le", errors="surrogatepass")


def validate_notes(notes: str) -> None:
    try:
        notes.encode("utf-8")
    except UnicodeEncodeError as error:
        # Kotlin would replace an unpaired surrogate with '?' before signing
        # verification, so the signed bytes could never match on clients.
        raise ValueError("Release notes contain an unpaired surrogate") from error
    if utf16_length(notes) > MAX_NOTES_LENGTH:
        raise ValueError(
            f"Release notes exceed the client limit of {MAX_NOTES_LENGTH} UTF-16 code units "
            f"({utf16_length(notes)})"
        )


def client_view(manifest: dict) -> dict:
    """Fields after UpdateClient.parseAndVerify normalisation, before signature checks."""
    return {
        "versionCode": int(manifest["versionCode"]),
        "versionName": manifest["versionName"].strip(),
        "installerUrl": manifest["installerUrl"].strip(),
        "sha256": manifest["sha256"].upper(),
        "size": int(manifest["size"]),
        "notes": client_truncated_notes(manifest.get("notes", "")),
    }


def assert_client_verifiable(manifest: dict) -> None:
    """Refuse a manifest whose signed fields a client would alter before verifying."""
    view = client_view(manifest)
    for field, value in view.items():
        if manifest.get(field, "") != value:
            raise ValueError(f"Windows client would alter manifest field {field!r} before verification")


def verify_client_contract(source: str) -> None:
    """Fail if UpdateClient.kt limits drift away from this publisher."""
    match = re.search(r"private const val MAX_NOTES_LENGTH\s*=\s*([0-9_]+)", source)
    if not match or int(match.group(1).replace("_", "")) != MAX_NOTES_LENGTH:
        raise RuntimeError("Publisher notes limit does not match UpdateClient.MAX_NOTES_LENGTH")
    if ".take(MAX_NOTES_LENGTH)" not in source:
        raise RuntimeError("UpdateClient notes normalisation changed; review publish_ota.client_view")


def _read_struct(data: bytes, offset: int, fmt: str):
    size = struct.calcsize(fmt)
    if offset < 0 or offset + size > len(data):
        raise ValueError("Truncated PE file")
    return struct.unpack_from(fmt, data, offset)


def pe_file_version(data: bytes) -> str | None:
    """Return VS_FIXEDFILEINFO file version from the PE resource section, if present.

    Only the Win32 resource directory is searched: the OTA wrapper embeds the
    jpackage installer as a managed resource, whose own version must not be
    mistaken for the wrapper's.
    """
    if data[:2] != b"MZ":
        raise ValueError("Installer is not a PE file")
    (pe_offset,) = _read_struct(data, 0x3C, "<I")
    if data[pe_offset : pe_offset + 4] != b"PE\0\0":
        raise ValueError("Installer is not a PE file")
    coff = pe_offset + 4
    _, sections, _, _, _, optional_size, _ = _read_struct(data, coff, "<HHIIIHH")
    optional = coff + 20
    (magic,) = _read_struct(data, optional, "<H")
    if magic == 0x10B:
        directories = optional + 96
    elif magic == 0x20B:
        directories = optional + 112
    else:
        raise ValueError("Unsupported PE optional header")
    (directory_count,) = _read_struct(data, directories - 4, "<I")
    if directory_count <= PE_RESOURCE_DIRECTORY_INDEX:
        return None
    resource_rva, resource_size = _read_struct(
        data, directories + PE_RESOURCE_DIRECTORY_INDEX * 8, "<II"
    )
    if resource_rva == 0 or resource_size == 0:
        return None
    section_table = optional + optional_size
    for index in range(sections):
        header = section_table + index * 40
        virtual_size, virtual_address, raw_size, raw_pointer = _read_struct(data, header + 8, "<IIII")
        span = max(virtual_size, raw_size)
        if virtual_address <= resource_rva < virtual_address + span:
            start = raw_pointer + (resource_rva - virtual_address)
            end = min(start + resource_size, raw_pointer + raw_size, len(data))
            break
    else:
        raise ValueError("PE resource directory is outside every section")
    signature = struct.pack("<I", PE_FIXED_FILE_INFO_SIGNATURE)
    position = data.find(signature, start, end)
    if position < 0:
        return None
    # VS_FIXEDFILEINFO: dwSignature, dwStrucVersion, dwFileVersionMS, dwFileVersionLS
    file_ms, file_ls = _read_struct(data, position + 8, "<II")
    return f"{file_ms >> 16}.{file_ms & 0xFFFF}.{file_ls >> 16}.{file_ls & 0xFFFF}"


def validate_installer_version(installer: Path, version_name: str, allow_missing: bool) -> str | None:
    """The OTA wrapper stamps AssemblyFileVersion "<version>.0" (build-ota-bootstrap.ps1)."""
    file_version = pe_file_version(installer.read_bytes())
    if file_version is None:
        if allow_missing:
            return None
        raise RuntimeError("Installer has no Win32 file version; refusing to publish")
    if file_version not in (version_name, f"{version_name}.0"):
        raise RuntimeError(
            f"Installer file version {file_version} does not match --version-name {version_name}"
        )
    return file_version


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest().upper()


def verify_release_lineage(private_key, candidate_code: int) -> None:
    """Refuse a rollback, foreign signing key, or unverified live predecessor."""
    source = CLIENT_SOURCE.read_text(encoding="utf-8")
    verify_client_contract(source)
    match = re.search(r'private const val PUBLIC_KEY\s*=\s*"([^"]+)"', source)
    if not match:
        raise RuntimeError("Client OTA public key was not found")
    client_key = base64.b64decode(match.group(1), validate=True)
    signing_key = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    if client_key != signing_key:
        raise RuntimeError("OTA signing key does not match the installed client trust root")

    request = Request(f"{UPDATE_ORIGIN}/veilark/windows/manifest.json")
    with urlopen(request, timeout=30) as response:
        data = response.read(128 * 1024 + 1)
    if len(data) > 128 * 1024:
        raise RuntimeError("Live OTA manifest is too large")
    live = json.loads(data)
    version = int(live["versionCode"])
    if version >= candidate_code:
        raise RuntimeError(f"Refusing non-increasing OTA version: live={version}, candidate={candidate_code}")
    expected_url = f"{UPDATE_ORIGIN}/veilark/windows/Veilark-{live['versionName']}.exe"
    if live["installerUrl"] != expected_url:
        raise RuntimeError("Live OTA installer URL is outside the release channel")
    payload = (
        f"{version}\n{live['versionName']}\n{live['installerUrl']}\n"
        f"{live['sha256']}\n{live['size']}"
    ).encode("utf-8")
    private_key.public_key().verify(base64.b64decode(live["signature"], validate=True), payload)
    if "notesSignature" in live:
        notes_payload = payload + b"\n" + live["notes"].encode("utf-8")
        private_key.public_key().verify(
            base64.b64decode(live["notesSignature"], validate=True), notes_payload
        )


def remote_sha256(client: paramiko.SSHClient, path: str) -> str:
    _, stdout, stderr = client.exec_command(f"sha256sum -- {shlex.quote(path)}")
    digest = stdout.read().decode("ascii", errors="replace").split(maxsplit=1)
    errors = stderr.read()
    if errors or stdout.channel.recv_exit_status() != 0 or not digest:
        return ""
    return digest[0].upper()


def upload_resumable(
    env: dict[str, str],
    local: Path,
    remote_temporary: str,
) -> None:
    expected_size = local.stat().st_size
    last_error: Exception | None = None
    for _ in range(UPLOAD_ATTEMPTS):
        client = None
        try:
            client = connect_node(env)
            sftp = client.open_sftp()
            try:
                try:
                    offset = sftp.stat(remote_temporary).st_size
                except OSError:
                    offset = 0
                if offset > expected_size:
                    sftp.remove(remote_temporary)
                    offset = 0
                with local.open("rb") as source:
                    source.seek(offset)
                    with sftp.file(remote_temporary, "ab") as destination:
                        destination.set_pipelined(True)
                        while chunk := source.read(UPLOAD_CHUNK_SIZE):
                            destination.write(chunk)
                        destination.flush()
                if sftp.stat(remote_temporary).st_size == expected_size:
                    return
            finally:
                sftp.close()
        except Exception as error:
            last_error = error
        finally:
            if client is not None:
                client.close()
    raise RuntimeError("Resumable Windows installer upload failed") from last_error


def main() -> None:
    parser = argparse.ArgumentParser(description="Sign and atomically publish Veilark Windows OTA")
    parser.add_argument("--installer", type=Path, required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--notes", required=True)
    parser.add_argument(
        "--allow-installer-without-file-version",
        action="store_true",
        help="Publish an installer that carries no Win32 file version resource.",
    )
    parser.add_argument(
        "--allow-unsigned-windows-publisher",
        action="store_true",
        help=(
            "Publish an installer without a trusted Authenticode identity. "
            "The Ed25519 manifest signature and SHA-256 checks remain mandatory."
        ),
    )
    args = parser.parse_args()

    installer = args.installer.resolve()
    if not installer.is_file():
        raise FileNotFoundError(installer)
    validate_release_identity(args.version_code, args.version_name)
    validate_notes(args.notes)
    validate_installer_version(installer, args.version_name, args.allow_installer_without_file_version)
    publisher_status = authenticode_status(installer)
    if publisher_status != "Valid":
        if not args.allow_unsigned_windows_publisher:
            raise RuntimeError(f"Authenticode status: {publisher_status}")
        if publisher_status != "NotSigned":
            raise RuntimeError(
                "Only a clean unsigned installer may use the explicit publisher override; "
                f"current status: {publisher_status}"
            )

    installer_name = f"Veilark-{args.version_name}.exe"
    sha256 = file_sha256(installer)
    size = installer.stat().st_size
    installer_url = f"{UPDATE_ORIGIN}/veilark/windows/{installer_name}"
    payload = (
        f"{args.version_code}\n{args.version_name}\n{installer_url}\n{sha256}\n{size}"
    ).encode("utf-8")

    private_key = serialization.load_pem_private_key(
        (WORKSPACE / "secrets" / "veilark-update-ed25519.pem").read_bytes(),
        password=None,
    )
    verify_release_lineage(private_key, args.version_code)
    signature = private_key.sign(payload)
    private_key.public_key().verify(signature, payload)
    notes_payload = payload + b"\n" + args.notes.encode("utf-8")
    notes_signature = private_key.sign(notes_payload)
    private_key.public_key().verify(notes_signature, notes_payload)
    manifest = {
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "installerUrl": installer_url,
        "sha256": sha256,
        "size": size,
        "notes": args.notes,
        "signature": base64.b64encode(signature).decode("ascii"),
        "notesSignature": base64.b64encode(notes_signature).decode("ascii"),
    }

    assert_client_verifiable(manifest)

    OTA_DIR.mkdir(parents=True, exist_ok=True)
    local_installer = OTA_DIR / installer_name
    local_manifest = OTA_DIR / "manifest.json"
    if installer != local_installer.resolve():
        shutil.copyfile(installer, local_installer)
    local_manifest.write_text(
        json.dumps(manifest, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    env = read_env(WORKSPACE / "secrets" / "netherlands-new.env")
    client = connect_node(env)
    try:
        _, stdout, stderr = client.exec_command(
            f"install -d -m 0755 -- {shlex.quote(REMOTE_DIR)}"
        )
        if stdout.channel.recv_exit_status() != 0:
            raise RuntimeError(stderr.read().decode(errors="replace"))
    finally:
        client.close()

    remote_installer = f"{REMOTE_DIR}/{installer_name}"
    remote_installer_tmp = f"{REMOTE_DIR}/.{installer_name}.uploading"
    client = connect_node(env)
    try:
        remote_matches = remote_sha256(client, remote_installer) == sha256
    finally:
        client.close()
    if not remote_matches:
        upload_resumable(env, local_installer, remote_installer_tmp)
        client = connect_node(env)
        try:
            if remote_sha256(client, remote_installer_tmp) != sha256:
                raise RuntimeError("Remote installer SHA-256 mismatch")
            sftp = client.open_sftp()
            try:
                sftp.posix_rename(remote_installer_tmp, remote_installer)
            finally:
                sftp.close()
        finally:
            client.close()

    remote_manifest_tmp = f"{REMOTE_DIR}/.manifest.json.{uuid.uuid4().hex}.tmp"
    client = connect_node(env)
    try:
        backup_name = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
        backup_path = f"/var/backups/veilark/windows/manifest-before-{args.version_code}-{backup_name}.json"
        _, stdout, stderr = client.exec_command(
            "install -d -m 0700 /var/backups/veilark/windows && "
            f"cp -- {shlex.quote(REMOTE_DIR + '/manifest.json')} {shlex.quote(backup_path)} && "
            f"chmod 0600 -- {shlex.quote(backup_path)}"
        )
        if stdout.channel.recv_exit_status() != 0:
            raise RuntimeError("Mandatory OTA rollback snapshot failed: " + stderr.read().decode(errors="replace"))
        sftp = client.open_sftp()
        try:
            sftp.put(str(local_manifest), remote_manifest_tmp)
            if sftp.stat(remote_installer).st_size != size:
                raise RuntimeError("Remote installer size mismatch")
            sftp.posix_rename(remote_manifest_tmp, f"{REMOTE_DIR}/manifest.json")
        finally:
            sftp.close()
    finally:
        client.close()

    request = Request(installer_url, method="HEAD")
    with urlopen(request, timeout=30) as response:
        if response.status != 200 or int(response.headers["Content-Length"]) != size:
            raise RuntimeError("Published installer verification failed")
    range_request = Request(installer_url, headers={"Range": "bytes=0-1023"})
    with urlopen(range_request, timeout=30) as response:
        if response.status != 206 or len(response.read()) != min(1024, size):
            raise RuntimeError("Published installer Range verification failed")
    with urlopen(f"{UPDATE_ORIGIN}/veilark/windows/manifest.json", timeout=30) as response:
        if json.load(response) != manifest:
            raise RuntimeError("Published manifest verification failed")

    print(
        json.dumps(
            {
                "versionCode": args.version_code,
                "versionName": args.version_name,
                "installerUrl": installer_url,
                "sha256": sha256,
                "size": size,
                "authenticode": publisher_status,
                "rollbackManifest": backup_path,
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
