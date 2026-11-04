from unittest.mock import MagicMock
from tools.ticket_data_pipeline.importers.ssh_runner import SshRunner
from tools.ticket_data_pipeline.importers.verifier import DataVerifier

def test_ssh_runner_handles_failure(monkeypatch):
    runner = SshRunner(ssh_host="ft119")
    mock_run = MagicMock()
    mock_run.return_value.returncode = 1
    mock_run.return_value.stderr = "ERROR: relation tbl_events does not exist"
    mock_run.return_value.stdout = ""
    monkeypatch.setattr("subprocess.run", mock_run)
    
    code, out = runner.execute_remote_sql("/path/to/mock.sql")
    assert code == 1
    assert "ERROR" in out

def test_data_verifier_constructs_query(monkeypatch):
    verifier = DataVerifier(ssh_host="ft119")
    mock_run = MagicMock()
    mock_run.return_value.returncode = 0
    mock_run.return_value.stdout = "count\n5"
    mock_run.return_value.stderr = ""
    monkeypatch.setattr("subprocess.run", mock_run)

    report = verifier.verify_import(["event-uuid-1", "event-uuid-2"])
    assert report["status"] == "PASSED"
    assert "count" in report["output"]

