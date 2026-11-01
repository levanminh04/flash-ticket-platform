"""Exercise the real tunnel launcher against a local dummy SSH listener."""
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import unittest

SCRIPT = Path(__file__).with_name("start-user-tunnel.sh").resolve()

class TunnelTests(unittest.TestCase):
    def launch(self, enabled=None):
        with tempfile.TemporaryDirectory(prefix="ft-tunnel-test-") as directory:
            root = Path(directory)
            key = root / "fixture-key"
            key.touch(mode=0o600)
            known = root / "known_hosts"
            known.touch()
            with socket.socket() as listener:
                listener.bind(("127.0.0.1", 0))
                listener.listen()
                env = dict(os.environ, USER_TUNNEL_SSH_HOST="127.0.0.1", USER_TUNNEL_SSH_USER="fixture",
                           USER_TUNNEL_KEY_FILE=str(key), USER_TUNNEL_KNOWN_HOSTS_FILE=str(known),
                           USER_TUNNEL_SSH_PORT=str(listener.getsockname()[1]))
                env.pop("USER_MONGO_TUNNEL_ENABLED", None)
                if enabled is not None:
                    env["USER_MONGO_TUNNEL_ENABLED"] = enabled
                child = subprocess.Popen(["bash", str(SCRIPT)], env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                try:
                    for _ in range(100):
                        if child.poll() is not None:
                            return child.returncode, []
                        args = (Path("/proc") / str(child.pid) / "cmdline").read_bytes().split(b"\0")
                        if args[0] == b"/usr/bin/ssh":
                            return None, [value.decode() for value in args if value]
                        time.sleep(0.01)
                    self.fail("Launcher never reached SSH")
                finally:
                    if child.poll() is None:
                        child.terminate()
                    child.communicate(timeout=5)

    def test_default_preserves_mongo_and_config_forwarding(self):
        _, args = self.launch()
        forwards = [args[i+1] for i, value in enumerate(args) if value == "-L"]
        self.assertEqual(forwards, ["127.0.0.1:17017:127.0.0.1:27017", "127.0.0.1:18888:127.0.0.1:8888"])

    def test_local_mongo_disables_only_mongo_forward(self):
        _, args = self.launch("false")
        forwards = [args[i+1] for i, value in enumerate(args) if value == "-L"]
        self.assertEqual(forwards, ["127.0.0.1:18888:127.0.0.1:8888"])

    def test_invalid_toggle_is_rejected_before_ssh(self):
        code, args = self.launch("flase")
        self.assertNotEqual(code, None)
        self.assertNotEqual(code, 0)
        self.assertEqual(args, [])

if __name__ == "__main__":
    unittest.main(verbosity=2)
