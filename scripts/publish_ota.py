from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
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
MAX_NOTES_LENGTH = 4_000
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


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest().upper()


def verify_release_lineage(private_key, candidate_code: int) -> None:
    """Refuse a rollback, foreign signing key, or unverified live predecessor."""
    source = CLIENT_SOURCE.read_text(encoding="utf-8")
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
    if args.version_code < 1 or not args.version_name:
        raise ValueError("Invalid version")
    if len(args.notes) > MAX_NOTES_LENGTH:
        raise ValueError("Release notes exceed the client limit")
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
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
