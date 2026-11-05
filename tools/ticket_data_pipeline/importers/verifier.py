import subprocess
from typing import List, Dict, Any

class DataVerifier:
    def __init__(self, ssh_host: str = "ft119"):
        self.ssh_host = ssh_host

    def verify_import(self, event_ids: List[str]) -> Dict[str, Any]:
        if not event_ids:
            return {"status": "NO_EVENTS", "output": "", "error": ""}
        ids_str = ",".join(f"'{eid}'" for eid in event_ids)
        query = (
            f"SELECT 'event_db' as db, count(*) FROM event_schema.tbl_events WHERE id IN ({ids_str}); "
            f"SELECT 'booking_db' as db, count(*) FROM booking_schema.tbl_event_sales_snapshots WHERE event_id IN ({ids_str}); "
            f"SELECT 'payment_db' as db, count(*) FROM payment_schema.tbl_event_finance_snapshots WHERE event_id IN ({ids_str}); "
            f"SELECT 'ticket_db' as db, count(*) FROM ticket_schema.tbl_event_access_snapshots WHERE event_id IN ({ids_str});"
        )
        res = subprocess.run(
            ["ssh", self.ssh_host, f"docker exec -i ft119-infra-postgres-1 psql -U postgres -c \"{query}\""],
            capture_output=True,
            text=True
        )
        return {
            "status": "PASSED" if res.returncode == 0 else "FAILED",
            "output": res.stdout,
            "error": res.stderr
        }

