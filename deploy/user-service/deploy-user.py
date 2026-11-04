#!/usr/bin/env python3
"""Deploy one User container; never mutate shared infrastructure or data."""
import argparse
from contextlib import ExitStack
import fcntl
import json
import os
from pathlib import Path
import pwd
import re
import socket
import subprocess
import sys
import time
import urllib.request
import uuid

LOCK_ROOT = Path("/run/lock")
STATE_ROOT = Path(pwd.getpwuid(os.geteuid()).pw_dir) / ".flash-ticket-user-deploy"


def command(args, environment=None):
    process = subprocess.run(args, env=environment, capture_output=True, text=True, timeout=180)
    if process.returncode:
        raise RuntimeError("command_failed_" + args[0])
    return process.stdout.strip()


def dotenv(path):
    values = {}
    for line in path.read_text().splitlines():
        match = re.match(r"\s*([A-Z][A-Z0-9_]*)\s*=\s*(.*)$", line)
        if not match:
            continue
        name, value = match.groups()
        if value.startswith("'") and value.endswith("'"):
            value = value[1:-1].replace("\\'", "'")
        elif value.startswith('"') and value.endswith('"'):
            value = json.loads(value)
        values[name] = value
    return values


def private_directory(path):
    if path.is_symlink():
        raise RuntimeError("unsafe_release_directory")
    path.mkdir(mode=0o700, parents=True, exist_ok=True)
    info = path.stat()
    if info.st_uid != os.geteuid() or info.st_mode & 0o077:
        raise RuntimeError("release_directory_must_be_private")


def private_write(path, content):
    temporary = path.with_name(path.name + "." + uuid.uuid4().hex)
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as stream:
        stream.write(content)
    os.replace(temporary, path)


def lock_file(stack, name):
    LOCK_ROOT.mkdir(parents=True, exist_ok=True)
    path = LOCK_ROOT / ("flash-ticket-user-" + name + ".lock")
    descriptor = os.open(path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    stream = stack.enter_context(os.fdopen(descriptor, "a"))
    if os.fstat(descriptor).st_uid != os.geteuid():
        raise RuntimeError("deployment_lock_owner_mismatch")
    try:
        fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        raise RuntimeError("deployment_already_in_progress") from None


def compose_command(project, release):
    return ["docker", "compose", "--project-name", project, "--env-file", str(release / "runtime.env"), "-f", str(release / "compose.yaml")]


def canonical_release(directory, source, values, image):
    text = source.read_text()
    keys = set(re.findall(r"\$\{([A-Z][A-Z0-9_]*)", text))
    effective = {key: value for key, value in values.items() if key in keys}
    effective["USER_IMAGE"] = image
    if any("\n" in value or "\r" in value for value in effective.values()):
        raise RuntimeError("multiline_runtime_value_not_supported")
    private_write(directory / "compose.yaml", text)
    private_write(directory / "runtime.env", "\n".join(key + "='" + value.replace("'", "\\'") + "'" for key, value in effective.items()) + "\n")
    # Compose gives shell values precedence over --env-file. Strip every referenced
    # runtime key, including inherited aliases, so the validated release is effective.
    environment = {key: value for key, value in os.environ.items() if key not in keys}
    environment["USER_IMAGE"] = image
    return environment


def release_environment(release, image):
    text = (release / "compose.yaml").read_text()
    keys = set(re.findall(r"\$\{([A-Z][A-Z0-9_]*)", text))
    environment = {key: value for key, value in os.environ.items() if key not in keys}
    environment["USER_IMAGE"] = image
    return environment


def verify_running(compose, environment, project, expected, address, port, revision=None):
    for _ in range(90):
        try:
            with urllib.request.urlopen(f"http://{address}:{port}/actuator/health", timeout=3) as response:
                if json.load(response).get("status") == "UP":
                    break
        except Exception:
            pass
        time.sleep(2)
    else:
        raise RuntimeError("user_health_timeout")
    container = command(compose + ["ps", "-q", "user-service"], environment)
    if len(container.splitlines()) != 1:
        raise RuntimeError("ambiguous_running_container")
    actual = json.loads(command(["docker", "inspect", container]))[0]
    if (actual["Image"] != expected or not actual["State"]["Running"]
            or actual["State"].get("OOMKilled")
            or actual["Config"].get("Labels", {}).get("com.docker.compose.project") != project):
        raise RuntimeError("image_or_memory_verification_failed")
    versions = [value.split("=", 1)[1] for value in actual["Config"].get("Env", []) if value.startswith("USER_SERVICE_VERSION=")]
    if revision is not None and versions != [revision]:
        raise RuntimeError("running_service_version_mismatch")
    return container, actual


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image")
    parser.add_argument("--revision", help="Expected full Git commit SHA; must match the pulled image label")
    parser.add_argument("--env-file", required=True, type=Path)
    parser.add_argument("--compose-file", type=Path, default=Path(__file__).with_name("docker-compose.user.yml"))
    parser.add_argument("--project", default="flash-ticket-user")
    parser.add_argument("--predeploy", action="store_true")
    parser.add_argument("--local-image", action="store_true",
                        help="Deploy an immutable image ID verified and loaded over SSH; no registry pull")
    args = parser.parse_args()
    if args.revision is not None and not re.fullmatch(r"[a-f0-9]{40}", args.revision):
        raise RuntimeError("invalid_expected_revision")
    repository = os.environ.get("USER_IMAGE_REPOSITORY", "ghcr.io/levanminh04/flash-ticket-platform/user-service")
    official = re.fullmatch(re.escape(repository) + r"@sha256:[a-f0-9]{64}", args.image)
    local = (args.predeploy or args.local_image) and re.fullmatch(r"sha256:[a-f0-9]{64}", args.image)
    if not (official or local):
        raise RuntimeError("unverified_image_reference")
    if args.predeploy:
        if not re.fullmatch(r"flash-ticket-user-predeploy-[a-f0-9]{32}", args.project):
            raise RuntimeError("unsafe_predeploy_project")
    elif args.project != "flash-ticket-user":
        raise RuntimeError("unexpected_project")
    if args.env_file.is_symlink() or args.env_file.stat().st_mode & 0o077:
        raise RuntimeError("env_file_must_be_private")
    values = dotenv(args.env_file)
    port = int(values.get("USER_SERVICE_PORT", "8085"))
    if not 1024 <= port <= 65535:
        raise RuntimeError("invalid_port")
    address = values.get("USER_SERVER_ADDRESS", "127.0.0.1")
    if address != "127.0.0.1":
        raise RuntimeError("independent_rollout_requires_loopback")
    with ExitStack() as stack:
        lock_file(stack, args.project)
        lock_file(stack, "port-" + str(port))
        private_directory(STATE_ROOT)
        state = STATE_ROOT / args.project
        private_directory(state)
        release = state / ("release-" + uuid.uuid4().hex)
        private_directory(release)
        environment = canonical_release(release, args.compose_file, values, args.image)
        compose = compose_command(args.project, release)
        command(compose + ["config", "--quiet"], environment)
        current = command(["docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=" + args.project, "--filter", "label=com.docker.compose.service=user-service"])
        if len(current.splitlines()) > 1:
            raise RuntimeError("ambiguous_existing_container")
        previous = None
        if current:
            old = json.loads(command(["docker", "inspect", current]))[0]
            pointer = state / "last-good.json"
            if not pointer.is_file() or pointer.is_symlink():
                raise RuntimeError("existing_release_snapshot_missing")
            previous = json.loads(pointer.read_text())
            if not re.fullmatch(r"release-[a-f0-9]{32}", previous.get("release", "")):
                raise RuntimeError("invalid_previous_release")
            previous_release = state / previous["release"]
            if previous_release.is_symlink() or old["Image"] != previous["imageId"]:
                raise RuntimeError("existing_container_differs_from_release_snapshot")
        try:
            with socket.create_connection((address, port), timeout=1):
                if not current or not old["State"]["Running"] or str(port) not in [x.split("=", 1)[1] for x in old["Config"]["Env"] if x.startswith("USER_SERVICE_PORT=")]:
                    raise RuntimeError("port_owned_by_another_process")
        except OSError:
            pass
        url = values["CONFIG_SERVER_URL"].rstrip("/") + "/user-service/default"
        with urllib.request.urlopen(url, timeout=10) as response:
            configuration = json.load(response)
        properties = {}
        for source in reversed(configuration["propertySources"]):
            properties.update(source["source"])
        if not properties.get("spring.security.oauth2.resourceserver.jwt.audiences[0]"):
            raise RuntimeError("config_server_missing_user_audience")
        if "logging.structured.json.add.serviceName" in properties:
            raise RuntimeError("config_server_conflicting_logging_fields")
        if official:
            command(compose + ["pull", "user-service"], environment)
        image = json.loads(command(["docker", "image", "inspect", args.image]))[0]
        expected = image["Id"]
        revision = (image["Config"].get("Labels") or {}).get("org.opencontainers.image.revision")
        if not isinstance(revision, str) or not re.fullmatch(r"[a-f0-9]{40}", revision):
            raise RuntimeError("invalid_image_revision")
        if args.revision is not None and revision != args.revision:
            raise RuntimeError("image_revision_mismatch")
        # The image is the source of version metadata. Never reuse a stale version
        # from the server .env or the caller's shell; leave the original .env intact.
        values["USER_SERVICE_VERSION"] = revision
        environment = canonical_release(release, release / "compose.yaml", values, args.image)
        command(compose + ["config", "--quiet"], environment)
        print("PREVIOUS user-service " + (previous["image"] if previous else "none"), flush=True)
        try:
            command(compose + ["up", "-d", "--no-deps", "--no-build", "user-service"], environment)
            container, actual = verify_running(compose, environment, args.project, expected, address, port, revision)
        except Exception:
            if previous:
                previous_release = state / previous["release"]
                restored_compose = compose_command(args.project, previous_release)
                restored_environment = release_environment(previous_release, previous["image"])
                command(restored_compose + ["up", "-d", "--no-deps", "--no-build", "user-service"], restored_environment)
                restored_version = dotenv(previous_release / "runtime.env").get("USER_SERVICE_VERSION")
                verify_running(restored_compose, restored_environment, args.project, previous["imageId"], "127.0.0.1", previous["port"], restored_version)
                print("ROLLBACK_SUCCESS user-service", flush=True)
            else:
                command(compose + ["stop", "user-service"], environment)
            raise RuntimeError("deployment_failed_after_replacement") from None
        private_write(state / "last-good.json", json.dumps({"image": args.image, "imageId": expected, "revision": revision, "release": release.name, "port": port}))
        print(json.dumps({"event": "DEPLOY_SUCCESS", "project": args.project, "container": container, "imageId": expected, "revision": revision, "health": "UP", "port": port, "restartCount": actual["RestartCount"], "predeploy": args.predeploy}), flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Provider output/configuration may contain secrets. Return only a known stage code.
        code = str(error) if isinstance(error, RuntimeError) and re.fullmatch(r"[a-z_]+", str(error)) else type(error).__name__
        print("DEPLOY_FAILED " + code, file=sys.stderr)
        sys.exit(1)
