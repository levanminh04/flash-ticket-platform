import subprocess
from pathlib import Path
from typing import Tuple

class SshRunner:
    def __init__(self, ssh_host: str = "ft119"):
        self.ssh_host = ssh_host

    def execute_remote_sql(self, local_sql_file) -> Tuple[int, str]:
        local_path = Path(local_sql_file)
        remote_tmp = f"/tmp/{local_path.name}"
        
        # 1. SCP file lên server
        scp_cmd = ["scp", str(local_path), f"{self.ssh_host}:{remote_tmp}"]
        scp_res = subprocess.run(scp_cmd, capture_output=True, text=True)
        if scp_res.returncode != 0:
            return scp_res.returncode, f"SCP_ERROR: {scp_res.stderr or scp_res.stdout}"

        # 2. SSH chạy psql
        psql_cmd = ["ssh", self.ssh_host, f"psql -U postgres -v ON_ERROR_STOP=1 -f {remote_tmp} && rm -f {remote_tmp}"]
        res = subprocess.run(psql_cmd, capture_output=True, text=True)
        combined_output = f"{res.stdout}\n{res.stderr}".strip()
        return res.returncode, combined_output
