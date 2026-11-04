import os
from pathlib import Path
from pydantic import BaseModel

BASE_DIR = Path(__file__).resolve().parent

class PipelineConfig(BaseModel):
    default_currency: str = "VND"
    default_fee_rate: float = 0.05
    ssh_host: str = os.getenv("FT_SSH_HOST", "ft119")
    organizer_subject: str = os.getenv("FT_ORGANIZER_SUBJECT", "ae3a05cd-95c2-47ec-a041-9c9de198c4a1")
    admin_subject: str = os.getenv("FT_ADMIN_SUBJECT", "01388e8e-721c-42d5-81b6-3f98f2c521df")
    raw_storage_dir: Path = BASE_DIR / "storage" / "raw"
    processed_storage_dir: Path = BASE_DIR / "storage" / "processed"
    sql_storage_dir: Path = BASE_DIR / "storage" / "sql"
    reports_storage_dir: Path = BASE_DIR / "storage" / "reports"

    def ensure_directories(self) -> None:
        self.raw_storage_dir.mkdir(parents=True, exist_ok=True)
        self.processed_storage_dir.mkdir(parents=True, exist_ok=True)
        self.sql_storage_dir.mkdir(parents=True, exist_ok=True)
        self.reports_storage_dir.mkdir(parents=True, exist_ok=True)
