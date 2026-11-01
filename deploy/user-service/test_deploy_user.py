"""Failure-path tests: all Docker/HTTP effects are simulated; no credentials needed."""
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("user_deploy", Path(__file__).with_name("deploy-user.py"))
deploy = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(deploy)
GOOD = "sha256:" + "a" * 64
BAD = "sha256:" + "b" * 64
PROJECT = "flash-ticket-user-predeploy-" + "1" * 32


class DockerAndHttp:
    def __init__(self):
        self.current = None
        self.fail_up_once = False
        self.wrong_image = False
        self.environments = []
        self.block_config = False
        self.entered = threading.Event()
        self.resume = threading.Event()
        self.blocked_once = False
        self.project = PROJECT

    def command(self, args, environment=None):
        if args[:3] == ["docker", "ps", "-aq"]:
            return "owned-container" if self.current else ""
        if args[:3] == ["docker", "inspect", "owned-container"]:
            image = self.current["image"]
            if self.wrong_image and image == BAD:
                image = "sha256:" + "c" * 64
            return json.dumps([{"Image": image, "Config": {"Image": self.current["image"], "Env": [k + "=" + v for k, v in self.current["values"].items()], "Labels": {"com.docker.compose.project": self.project}}, "State": {"Running": True, "OOMKilled": False}, "RestartCount": 0}])
        if args[:3] == ["docker", "image", "inspect"]:
            return args[3]
        if args[:2] != ["docker", "compose"]:
            raise AssertionError("Unexpected operation: " + repr(args))
        env_path = Path(args[args.index("--env-file") + 1])
        values = deploy.dotenv(env_path)
        self.environments.append(dict(environment or {}))
        effective = dict(values)
        for key in list(values):
            if environment and key in environment:
                effective[key] = environment[key]
        image = environment["USER_IMAGE"]
        if "config" in args:
            if self.block_config and not self.blocked_once:
                self.blocked_once = True
                self.entered.set()
                self.resume.wait(5)
            return ""
        if "up" in args:
            self.current = {"image": image, "values": effective}
            if self.fail_up_once and image == BAD:
                self.fail_up_once = False
                raise RuntimeError("command_failed_docker")
            return ""
        if "stop" in args:
            self.current = None
            return ""
        if "ps" in args:
            return "owned-container" if self.current else ""
        raise AssertionError("Unexpected Compose operation")

    def urlopen(self, url, timeout=0):
        if url.endswith("/user-service/default"):
            result = {"propertySources": [{"source": {"spring.security.oauth2.resourceserver.jwt.audiences[0]": "flash-ticket-api"}}]}
        else:
            healthy = self.current and "bad-host" not in self.current["values"]["USER_MONGO_URI"]
            result = {"status": "UP" if healthy else "DOWN"}
        return io.BytesIO(json.dumps(result).encode())


class DeploymentFailureTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="ft-user-guard-test-")
        self.root = Path(self.directory.name)
        self.env = self.root / "first" / "runtime.env"
        self.other_env = self.root / "second" / "runtime.env"
        self.compose = self.root / "compose.yaml"
        self.compose.write_text("services:\n  user-service:\n    image: ${USER_IMAGE}\n    environment:\n      USER_SERVICE_PORT: ${USER_SERVICE_PORT}\n      USER_SERVER_ADDRESS: ${USER_SERVER_ADDRESS}\n      CONFIG_SERVER_URL: ${CONFIG_SERVER_URL}\n      USER_MONGO_URI: ${USER_MONGO_URI}\n")
        self.values = {"USER_SERVICE_PORT": "18085", "USER_SERVER_ADDRESS": "127.0.0.1", "CONFIG_SERVER_URL": "http://approved-config:18888", "USER_MONGO_URI": "mongodb://fixture-user:fixture-only@approved-host/fixture"}
        self.write_env(self.env, self.values)
        self.write_env(self.other_env, self.values)
        self.fake = DockerAndHttp()
        self.patches = [patch.object(deploy, "command", self.fake.command), patch.object(deploy.urllib.request, "urlopen", self.fake.urlopen), patch.object(deploy.socket, "create_connection", side_effect=ConnectionRefusedError), patch.object(deploy.time, "sleep", return_value=None), patch.object(deploy, "LOCK_ROOT", self.root / "locks", create=True), patch.object(deploy, "STATE_ROOT", self.root / "releases", create=True)]
        for item in self.patches:
            item.start()

    def tearDown(self):
        self.fake.resume.set()
        for item in reversed(self.patches):
            item.stop()
        self.directory.cleanup()

    @staticmethod
    def write_env(path, values):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("\n".join(key + "='" + value + "'" for key, value in values.items()) + "\n")
        path.chmod(0o600)

    def arguments(self, image=GOOD, env=None):
        return ["deploy-user.py", image, "--predeploy", "--project", PROJECT, "--env-file", str(env or self.env), "--compose-file", str(self.compose)]

    def execute(self, image=GOOD):
        with patch("sys.argv", self.arguments(image)):
            deploy.main()

    def test_shell_variables_cannot_bypass_loopback_or_config_gate(self):
        with patch.dict(os.environ, {"USER_SERVER_ADDRESS": "0.0.0.0", "CONFIG_SERVER_URL": "http://unapproved-config"}):
            self.execute()
        self.assertEqual(self.fake.current["values"]["USER_SERVER_ADDRESS"], "127.0.0.1")
        self.assertEqual(self.fake.current["values"]["CONFIG_SERVER_URL"], "http://approved-config:18888")

    def test_default_compose_uses_service_named_file_next_to_deploy_script(self):
        self.compose.rename(self.root / "docker-compose.user.yml")
        args = self.arguments()
        del args[args.index("--compose-file"):]
        with patch.object(deploy, "__file__", str(self.root / "deploy-user.py")):
            with patch("sys.argv", args):
                deploy.main()
        self.assertEqual(self.fake.current["image"], GOOD)

    def test_official_local_image_requires_explicit_mode_and_immutable_id(self):
        args = ["deploy-user.py", GOOD, "--env-file", str(self.env), "--compose-file", str(self.compose)]
        with patch("sys.argv", args):
            with self.assertRaisesRegex(RuntimeError, "unverified_image_reference"):
                deploy.main()
        self.fake.project = "flash-ticket-user"
        with patch("sys.argv", args + ["--local-image"]):
            deploy.main()
        self.assertEqual(self.fake.current["image"], GOOD)
        for image in ["latest", "flash-ticket/user-service:latest"]:
            with patch("sys.argv", [args[0], image] + args[2:] + ["--local-image"]):
                with self.assertRaisesRegex(RuntimeError, "unverified_image_reference"):
                    deploy.main()

    def test_replace_failure_restores_previous_release(self):
        self.execute()
        self.fake.fail_up_once = True
        with self.assertRaises(RuntimeError):
            self.execute(BAD)
        self.assertEqual(self.fake.current["image"], GOOD)

    def test_image_verification_failure_restores_previous_release(self):
        self.execute()
        self.fake.wrong_image = True
        with self.assertRaises(RuntimeError):
            self.execute(BAD)
        self.assertEqual(self.fake.current["image"], GOOD)

    def test_health_failure_restores_previous_environment_not_only_image(self):
        self.execute()
        previous = dict(self.fake.current["values"])
        self.write_env(self.env, dict(self.values, USER_MONGO_URI="mongodb://fixture@bad-host/fixture"))
        with self.assertRaises(RuntimeError):
            self.execute(BAD)
        self.assertEqual(self.fake.current["image"], GOOD)
        self.assertEqual(self.fake.current["values"], previous)

    def test_project_lock_is_shared_across_environment_directories(self):
        self.fake.block_config = True
        first_errors = []
        def first():
            try:
                deploy.main()
            except Exception as error:
                first_errors.append(error)
        with patch("sys.argv", self.arguments()):
            thread = threading.Thread(target=first)
            thread.start()
            self.assertTrue(self.fake.entered.wait(2))
            try:
                with patch("sys.argv", self.arguments(env=self.other_env)):
                    with self.assertRaises((RuntimeError, BlockingIOError)):
                        deploy.main()
            finally:
                self.fake.resume.set()
                thread.join(3)
        self.assertFalse(thread.is_alive())
        self.assertEqual(first_errors, [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
