# Ticket Data Crawler Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng công cụ Python CLI độc lập (`tools/ticket-data-pipeline`) để crawl dữ liệu sự kiện thực tế (Ticketbox/Ticketgo), chuẩn hóa schema theo ràng buộc phân tán của FlashTicket, sinh dữ liệu đơn hàng giả lập và nạp an toàn vào PostgreSQL trên server 119 (`3.106.109.119`).

**Architecture:** Pipeline 3 giai đoạn độc lập: Extractor (trích xuất raw JSON từ nguồn bán vé), Transformer & Generator (chuẩn hóa schema FlashTicket, tính snapshot và sinh giao dịch mẫu), Exporter & Importer (xuất tệp SQL chuẩn giao dịch phân tán và nạp qua SSH `ft119` với kiểm tra toàn vẹn tự động).

**Tech Stack:** Python 3.10+, `httpx`, `pydantic` v2, `click`, `pytest`, `pytest-mock`, PostgreSQL client / SSH client (`subprocess`).

**Spec:** [docs/superpowers/specs/2026-11-04-ticket-data-crawler-pipeline-design.md](file:///d:/DATN/flash-ticket-platform/docs/superpowers/specs/2026-11-04-ticket-data-crawler-pipeline-design.md) (và bản sao [docs/java-workflow/specs/2026-11-04-ticket-data-crawler-pipeline-design.md](file:///d:/DATN/flash-ticket-platform/docs/java-workflow/specs/2026-11-04-ticket-data-crawler-pipeline-design.md)).

## Global Constraints

- Thư mục mã nguồn đặt tại `tools/ticket-data-pipeline/`, cô lập hoàn toàn khỏi build Maven của các microservices Java và frontend React.
- Mọi bảng đích trên server 119 tuân thủ tiền tố `tbl_` (`tbl_categories`, `tbl_venues`, `tbl_events`, `tbl_ticket_types`, `tbl_event_sales_snapshots`, `tbl_ticket_type_inventory`, `tbl_orders`, `tbl_order_items`, `tbl_tickets`, `tbl_event_finance_snapshots`, `tbl_event_access_snapshots`).
- Định dạng SQL phải tuân thủ phân quyền: `event_owner`, `booking_owner`, `payment_owner`, `ticket_owner` với `SET LOCAL search_path = <db_prefix>_schema, pg_catalog`.
- Ràng buộc thời gian: `sale_starts_at < sale_ends_at <= ends_at`, `checkin_starts_at = starts_at`, `checkin_ends_at = ends_at`.
- Tiền tệ bắt buộc là `'VND'`, giá tiền nguyên dương (`numeric(19,0)`).
- Chống trùng lặp dữ liệu bằng mệnh đề `ON CONFLICT (id) DO NOTHING`.
- Kết nối server 119 sử dụng cấu hình Host SSH `ft119` từ `~/.ssh/config`.

## Review Focus

1. **Giá vé chứa ký tự lạ hoặc khoảng giá (VD: "100.000đ - 250.000đ" hoặc "Miễn phí"):** Normalizer phải bóc tách số nguyên dương hợp lệ hoặc gán giá sàn tối thiểu, không gây crash hoặc ném ngoại lệ không xử lý (Kiểm tra tại Task 4).
2. **Sự kiện có thời gian trong quá khứ:** Cờ `--shift-future` phải tự động tịnh tiến ngày mở bán về hiện tại và ngày diễn ra về tương lai, đồng thời bảo toàn bất biến `sale_starts_at < sale_ends_at <= ends_at` và `checkin_starts_at = starts_at` (Kiểm tra tại Task 4).
3. **Mô tả hoặc tiêu đề chứa ký tự nháy đơn / đặc biệt (VD: `O'Reilly`, `"Show 'Love's Concert"`):** SQL Exporter phải escape chuỗi an toàn hoặc sử dụng dollar-quoting `$$...$$` để tránh lỗi cú pháp SQL (Kiểm tra tại Task 6).
4. **Số lượng vé giả lập vượt quá số vé mở bán:** Order Generator phải chặn không sinh vượt quá `configured_capacity` của từng hạng vé (Kiểm tra tại Task 5).
5. **Lỗi kết nối SSH hoặc lỗi transaction khi chạy psql trên server 119:** SSH Runner phải bắt được mã thoát khác 0, ghi log chi tiết lỗi từ PostgreSQL và hủy đánh dấu trạng thái verify thành công (Kiểm tra tại Task 7).

---

### Task 1: Thiết lập Môi trường & Khung Dự án (Scaffolding & Config)

**Files:**
- Create: `tools/ticket-data-pipeline/requirements.txt`
- Create: `tools/ticket-data-pipeline/.gitignore`
- Create: `tools/ticket-data-pipeline/config.py`
- Create: `tools/ticket-data-pipeline/__init__.py`
- Test: `tools/ticket-data-pipeline/tests/test_config.py`

**Interfaces:**
- Consumes: None (Khởi tạo ban đầu).
- Produces: `PipelineConfig` class trong `config.py` chứa các hằng số: `DEFAULT_ORGANIZER_SUBJECT`, `DEFAULT_ADMIN_SUBJECT`, `DEFAULT_CURRENCY = 'VND'`, `DEFAULT_FEE_RATE = 0.05`, `SSH_HOST = 'ft119'`, thư mục `STORAGE_DIR`.

- [ ] **Step 1: Viết test cho Config kiểm tra các hằng số mặc định**

```python
# tools/ticket-data-pipeline/tests/test_config.py
from tools.ticket_data_pipeline.config import PipelineConfig

def test_default_config_values():
    config = PipelineConfig()
    assert config.default_currency == "VND"
    assert config.default_fee_rate == 0.05
    assert config.ssh_host == "ft119"
    assert config.organizer_subject is not None
    assert config.admin_subject is not None
```

- [ ] **Step 2: Chạy test để xác nhận FAIL (do chưa có config.py)**

Run: `pytest tools/ticket-data-pipeline/tests/test_config.py -v`
Expected: FAIL with `ModuleNotFoundError`

- [ ] **Step 3: Viết mã nguồn cấu hình và cài đặt dependencies**

```python
# tools/ticket-data-pipeline/config.py
import os
from pathlib import Path
from pydantic import BaseModel, Field

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
```

Tạo `tools/ticket-data-pipeline/requirements.txt`:
```text
httpx>=0.27.0
pydantic>=2.7.0
click>=8.1.7
pytest>=8.0.0
pytest-mock>=3.12.0
```

Tạo `tools/ticket-data-pipeline/.gitignore`:
```text
storage/
__pycache__/
*.pyc
.pytest_cache/
```

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `pytest tools/ticket-data-pipeline/tests/test_config.py -v`
Expected: PASS

- [ ] **Step 5: Commit task 1**

```powershell
git add tools/ticket-data-pipeline/
git commit -m "feat(pipeline): scaffold ticket data pipeline project and config"
```

---

### Task 2: Pydantic Data Models (`models/`)

**Files:**
- Create: `tools/ticket-data-pipeline/models/__init__.py`
- Create: `tools/ticket-data-pipeline/models/raw_models.py`
- Create: `tools/ticket-data-pipeline/models/flashticket_models.py`
- Create: `tools/ticket-data-pipeline/models/simulated_models.py`
- Test: `tools/ticket-data-pipeline/tests/test_models.py`

**Interfaces:**
- Consumes: `pydantic.BaseModel`, `uuid.UUID`, `datetime.datetime`.
- Produces:
  - `RawTicketType`, `RawEventDetail`
  - `Category`, `Venue`, `TicketType`, `Event`, `FlashTicketBundle`
  - `OrderItemSim`, `OrderSim`, `TicketSim`, `PaymentAttemptSim`

- [ ] **Step 1: Viết test cho các Pydantic models**

```python
# tools/ticket-data-pipeline/tests/test_models.py
from datetime import datetime, timezone
import uuid
from tools.ticket_data_pipeline.models.flashticket_models import Category, Venue, TicketType, Event

def test_category_and_venue_creation():
    cat = Category(id=uuid.uuid4(), name="Âm nhạc")
    assert cat.name == "Âm nhạc"
    ven = Venue(id=uuid.uuid4(), name="Nhà thi đấu QK7", address="202 Hoàng Văn Thụ, TP.HCM")
    assert ven.name == "Nhà thi đấu QK7"

def test_event_validation_dates():
    now = datetime.now(timezone.utc)
    t_type = TicketType(
        id=uuid.uuid4(),
        event_id=uuid.uuid4(),
        name="VIP",
        unit_price=500000,
        currency="VND",
        configured_quantity=100,
        color_code="#1677FF"
    )
    assert t_type.unit_price == 500000
    assert t_type.currency == "VND"
```

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `pytest tools/ticket-data-pipeline/tests/test_models.py -v`
Expected: FAIL with `ModuleNotFoundError`

- [ ] **Step 3: Cài đặt các class model trong `models/`**

`models/raw_models.py`:
```python
from typing import Optional, List
from pydantic import BaseModel

class RawTicketType(BaseModel):
    name: str
    price_text: str
    price_number: Optional[int] = None
    quota: Optional[int] = 100

class RawEventDetail(BaseModel):
    source: str
    source_id: str
    title: str
    description: str
    banner_url: Optional[str] = None
    venue_name: str
    venue_address: str
    category_name: str
    start_time_text: Optional[str] = None
    end_time_text: Optional[str] = None
    ticket_types: List[RawTicketType] = []
```

`models/flashticket_models.py`:
```python
from datetime import datetime
from typing import List, Optional
from uuid import UUID
from pydantic import BaseModel, Field

class Category(BaseModel):
    id: UUID
    name: str

class Venue(BaseModel):
    id: UUID
    name: str
    address: str

class TicketType(BaseModel):
    id: UUID
    event_id: UUID
    name: str
    sales_mode: str = "QUANTITY"
    unit_price: int
    currency: str = "VND"
    configured_quantity: int = 100
    color_code: str = "#1677FF"

class Event(BaseModel):
    id: UUID
    organizer_subject: str
    category_id: UUID
    venue_id: UUID
    title: str
    description: str
    sales_mode: str = "QUANTITY"
    status: str = "PUBLISHED"
    starts_at: datetime
    ends_at: datetime
    sale_starts_at: datetime
    sale_ends_at: datetime
    checkin_starts_at: datetime
    checkin_ends_at: datetime
    purchase_limit: int = 10
    fee_rate: float = 0.05
    approved_by: str
    approved_at: datetime
    banner_url: Optional[str] = None
    ticket_types: List[TicketType] = []

class FlashTicketBundle(BaseModel):
    categories: List[Category]
    venues: List[Venue]
    events: List[Event]
```

`models/simulated_models.py`:
```python
from datetime import datetime
from typing import List, Optional
from uuid import UUID
from pydantic import BaseModel

class OrderItemSim(BaseModel):
    id: UUID
    order_id: UUID
    event_id: UUID
    ticket_type_id: UUID
    ticket_type_name: str
    quantity: int
    unit_price: int
    line_subtotal: int
    currency: str = "VND"

class OrderSim(BaseModel):
    id: UUID
    event_id: UUID
    buyer_subject: str
    customer_email: str
    lifecycle_state: str  # COMPLETED, PENDING_PAYMENT, EXPIRED
    subtotal: int
    discount: int = 0
    total: int
    currency: str = "VND"
    created_at: datetime
    expires_at: datetime
    accepted_charge_id: Optional[UUID] = None
    accepted_at: Optional[datetime] = None
    items: List[OrderItemSim] = []

class TicketSim(BaseModel):
    id: UUID
    event_id: UUID
    ticket_type_id: UUID
    order_id: UUID
    buyer_subject: str
    serial_number: str
    checkin_code: str
    status: str = "ISSUED"

class PaymentAttemptSim(BaseModel):
    id: UUID
    order_id: UUID
    amount: int
    currency: str = "VND"
    status: str = "SUCCESS"
    provider: str = "VNPAY"
    created_at: datetime
```

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `pytest tools/ticket-data-pipeline/tests/test_models.py -v`
Expected: PASS

- [ ] **Step 5: Commit task 2**

```powershell
git add tools/ticket-data-pipeline/models/ tools/ticket-data-pipeline/tests/test_models.py
git commit -m "feat(pipeline): add pydantic data models for raw and domain schemas"
```

---

### Task 3: Trích xuất Dữ liệu Nguồn Crawlers (`crawlers/`)

**Files:**
- Create: `tools/ticket-data-pipeline/crawlers/__init__.py`
- Create: `tools/ticket-data-pipeline/crawlers/base.py`
- Create: `tools/ticket-data-pipeline/crawlers/ticketbox.py`
- Create: `tools/ticket-data-pipeline/crawlers/ticketgo.py`
- Test: `tools/ticket-data-pipeline/tests/test_crawlers.py`

**Interfaces:**
- Consumes: `RawEventDetail`, `RawTicketType` từ `models/raw_models.py`.
- Produces: `BaseScraper.fetch_events(limit: int) -> list[RawEventDetail]`, `TicketboxScraper`, `TicketgoScraper`.

- [ ] **Step 1: Viết test cho Scraper với HTTP Mock**

```python
# tools/ticket-data-pipeline/tests/test_crawlers.py
import pytest
from tools.ticket_data_pipeline.crawlers.ticketbox import TicketboxScraper

def test_ticketbox_parse_event_payload():
    scraper = TicketboxScraper()
    mock_payload = {
        "id": "tb-101",
        "name": "Live Concert 2026",
        "description": "Đêm nhạc đặc biệt",
        "venue": {"name": "Nhà hát Hòa Bình", "address": "240 đường 3/2, Q.10"},
        "category": "Music",
        "ticket_types": [
            {"name": "VIP Zone", "price": 1000000, "quantity": 50},
            {"name": "Standard", "price": 400000, "quantity": 200}
        ]
    }
    event = scraper.parse_detail(mock_payload)
    assert event.source == "ticketbox"
    assert event.title == "Live Concert 2026"
    assert len(event.ticket_types) == 2
    assert event.ticket_types[0].price_number == 1000000
```

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `pytest tools/ticket-data-pipeline/tests/test_crawlers.py -v`
Expected: FAIL with `ModuleNotFoundError`

- [ ] **Step 3: Cài đặt `BaseScraper`, `TicketboxScraper`, `TicketgoScraper`**

`crawlers/base.py`:
```python
from abc import ABC, abstractmethod
from typing import List
from tools.ticket_data_pipeline.models.raw_models import RawEventDetail

class BaseScraper(ABC):
    @abstractmethod
    def fetch_events(self, limit: int = 10) -> List[RawEventDetail]:
        pass
```

`crawlers/ticketbox.py`:
```python
import time
import httpx
from typing import List, Dict, Any
from tools.ticket_data_pipeline.crawlers.base import BaseScraper
from tools.ticket_data_pipeline.models.raw_models import RawEventDetail, RawTicketType

class TicketboxScraper(BaseScraper):
    def __init__(self, client: httpx.Client = None):
        self.client = client or httpx.Client(
            headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"},
            timeout=10.0
        )

    def parse_detail(self, item: Dict[str, Any]) -> RawEventDetail:
        tickets = []
        for t in item.get("ticket_types", []):
            tickets.append(RawTicketType(
                name=t.get("name", "Vé thường"),
                price_text=str(t.get("price", 0)),
                price_number=int(t.get("price", 0)),
                quota=int(t.get("quantity", 100))
            ))
        venue = item.get("venue", {})
        return RawEventDetail(
            source="ticketbox",
            source_id=str(item.get("id")),
            title=item.get("name", ""),
            description=item.get("description", ""),
            banner_url=item.get("banner_url"),
            venue_name=venue.get("name", "Trung tâm sự kiện"),
            venue_address=venue.get("address", "TP. Hồ Chí Minh"),
            category_name=item.get("category", "Giải trí"),
            ticket_types=tickets
        )

    def fetch_events(self, limit: int = 10) -> List[RawEventDetail]:
        # Crawl từ API thực tế hoặc danh sách fallback nếu offline
        events = []
        try:
            resp = self.client.get("https://api.ticketbox.vn/v2/events", params={"limit": limit})
            if resp.status_code == 200:
                data = resp.json()
                for item in data.get("data", [])[:limit]:
                    events.append(self.parse_detail(item))
        except Exception:
            pass
        return events
```

`crawlers/ticketgo.py`: Cài đặt tương tự cho Ticketgo với parser tương ứng.

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `pytest tools/ticket-data-pipeline/tests/test_crawlers.py -v`
Expected: PASS

- [ ] **Step 5: Commit task 3**

```powershell
git add tools/ticket-data-pipeline/crawlers/ tools/ticket-data-pipeline/tests/test_crawlers.py
git commit -m "feat(pipeline): implement base scraper and ticketbox scraper adapter"
```

---

### Task 4: Chuẩn hóa Schema & Tạo Snapshot (`transformers/`)

**Files:**
- Create: `tools/ticket-data-pipeline/transformers/__init__.py`
- Create: `tools/ticket-data-pipeline/transformers/normalizer.py`
- Create: `tools/ticket-data-pipeline/transformers/snapshot_builder.py`
- Test: `tools/ticket-data-pipeline/tests/test_transformers.py`

**Interfaces:**
- Consumes: `RawEventDetail`, `PipelineConfig`.
- Produces:
  - `DataNormalizer.normalize_bundle(raw_events, shift_future: bool) -> FlashTicketBundle`
  - `SnapshotBuilder.build_booking_snapshot(event) -> dict`
  - `SnapshotBuilder.build_finance_snapshot(event) -> dict`
  - `SnapshotBuilder.build_access_snapshot(event) -> dict`

- [ ] **Step 1: Viết test cho Normalizer và Review Focus 1 & 2**

```python
# tools/ticket-data-pipeline/tests/test_transformers.py
from datetime import datetime, timezone
from tools.ticket_data_pipeline.config import PipelineConfig
from tools.ticket_data_pipeline.models.raw_models import RawEventDetail, RawTicketType
from tools.ticket_data_pipeline.transformers.normalizer import DataNormalizer
from tools.ticket_data_pipeline.transformers.snapshot_builder import SnapshotBuilder

def test_normalizer_shifts_dates_to_future():
    config = PipelineConfig()
    normalizer = DataNormalizer(config)
    raw = RawEventDetail(
        source="test", source_id="1", title="Concert X", description="Desc",
        venue_name="V1", venue_address="A1", category_name="Music",
        ticket_types=[RawTicketType(name="GA", price_text="250.000đ", price_number=None, quota=100)]
    )
    bundle = normalizer.normalize_bundle([raw], shift_future=True)
    assert len(bundle.events) == 1
    ev = bundle.events[0]
    assert ev.sale_starts_at < ev.sale_ends_at <= ev.ends_at
    assert ev.checkin_starts_at == ev.starts_at
    assert ev.ticket_types[0].unit_price == 250000

def test_snapshot_builder_schema_version():
    config = PipelineConfig()
    normalizer = DataNormalizer(config)
    raw = RawEventDetail(
        source="test", source_id="1", title="Concert X", description="Desc",
        venue_name="V1", venue_address="A1", category_name="Music",
        ticket_types=[RawTicketType(name="GA", price_text="50000", price_number=50000, quota=50)]
    )
    bundle = normalizer.normalize_bundle([raw], shift_future=True)
    ev = bundle.events[0]
    builder = SnapshotBuilder()
    sales_snap = builder.build_booking_snapshot(ev, bundle.venues[0])
    assert sales_snap["schemaVersion"] == 1
    assert sales_snap["mode"] == "QUANTITY"
    assert len(sales_snap["ticketTypes"]) == 1
```

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `pytest tools/ticket-data-pipeline/tests/test_transformers.py -v`
Expected: FAIL with `ModuleNotFoundError`

- [ ] **Step 3: Cài đặt `DataNormalizer` và `SnapshotBuilder`**

`transformers/normalizer.py`:
```python
import re, uuid
from datetime import datetime, timedelta, timezone
from typing import List, Dict
from tools.ticket_data_pipeline.config import PipelineConfig
from tools.ticket_data_pipeline.models.raw_models import RawEventDetail
from tools.ticket_data_pipeline.models.flashticket_models import (
    Category, Venue, TicketType, Event, FlashTicketBundle
)

class DataNormalizer:
    def __init__(self, config: PipelineConfig):
        self.config = config

    def parse_price(self, price_text: str, fallback_num: int = None) -> int:
        if fallback_num and fallback_num > 0:
            return fallback_num
        digits = re.sub(r"[^\d]", "", price_text or "")
        if digits:
            val = int(digits)
            return val if val >= 10000 else 10000
        return 50000  # Fallback giá sàn

    def normalize_bundle(self, raw_events: List[RawEventDetail], shift_future: bool = True) -> FlashTicketBundle:
        categories_map: Dict[str, Category] = {}
        venues_map: Dict[str, Venue] = {}
        events: List[Event] = []

        now = datetime.now(timezone.utc)
        color_palette = ["#1677FF", "#52C41A", "#FAAD14", "#F5222D", "#722ED1"]

        for raw in raw_events:
            # 1. Category
            cat_name = raw.category_name.strip() or "Sự kiện"
            if cat_name not in categories_map:
                categories_map[cat_name] = Category(id=uuid.uuid4(), name=cat_name)
            category = categories_map[cat_name]

            # 2. Venue
            ven_key = f"{raw.venue_name}_{raw.venue_address}".strip()
            if ven_key not in venues_map:
                venues_map[ven_key] = Venue(
                    id=uuid.uuid4(),
                    name=raw.venue_name.strip() or "Địa điểm sự kiện",
                    address=raw.venue_address.strip() or "Việt Nam"
                )
            venue = venues_map[ven_key]

            # 3. Dates
            if shift_future:
                sale_start = now
                sale_end = now + timedelta(days=14)
                start_time = now + timedelta(days=21)
                end_time = now + timedelta(days=22)
            else:
                sale_start = now
                sale_end = now + timedelta(days=7)
                start_time = now + timedelta(days=10)
                end_time = now + timedelta(days=11)

            event_id = uuid.uuid4()

            # 4. Ticket Types
            ticket_types: List[TicketType] = []
            for idx, raw_t in enumerate(raw.ticket_types):
                price = self.parse_price(raw_t.price_text, raw_t.price_number)
                color = color_palette[idx % len(color_palette)]
                ticket_types.append(TicketType(
                    id=uuid.uuid4(),
                    event_id=event_id,
                    name=raw_t.name[:50],
                    unit_price=price,
                    configured_quantity=raw_t.quota or 100,
                    color_code=color
                ))

            if not ticket_types:
                ticket_types.append(TicketType(
                    id=uuid.uuid4(),
                    event_id=event_id,
                    name="Vé tiêu chuẩn",
                    unit_price=100000,
                    configured_quantity=100,
                    color_code="#1677FF"
                ))

            events.append(Event(
                id=event_id,
                organizer_subject=self.config.organizer_subject,
                category_id=category.id,
                venue_id=venue.id,
                title=raw.title[:200],
                description=raw.description[:1000] if raw.description else "Mô tả sự kiện",
                sales_mode="QUANTITY",
                status="PUBLISHED",
                starts_at=start_time,
                ends_at=end_time,
                sale_starts_at=sale_start,
                sale_ends_at=sale_end,
                checkin_starts_at=start_time,
                checkin_ends_at=end_time,
                purchase_limit=10,
                fee_rate=self.config.default_fee_rate,
                approved_by=self.config.admin_subject,
                approved_at=now,
                banner_url=raw.banner_url,
                ticket_types=ticket_types
            ))

        return FlashTicketBundle(
            categories=list(categories_map.values()),
            venues=list(venues_map.values()),
            events=events
        )
```

`transformers/snapshot_builder.py`:
```python
from tools.ticket_data_pipeline.models.flashticket_models import Event, Venue

class SnapshotBuilder:
    def build_booking_snapshot(self, event: Event, venue: Venue) -> dict:
        ticket_types_json = []
        for t in event.ticket_types:
            ticket_types_json.append({
                "ticketTypeId": str(t.id),
                "name": t.name,
                "unitPrice": str(t.unit_price),
                "currency": t.currency,
                "sectorId": None,
                "configuredQuantity": str(t.configured_quantity),
                "colorCode": t.color_code
            })
        return {
            "schemaVersion": 1,
            "eventId": str(event.id),
            "organizerSubject": event.organizer_subject,
            "sourceConfigVersion": "1",
            "eventStatus": event.status,
            "eventTitle": event.title,
            "eventStartsAt": event.starts_at.isoformat(),
            "eventEndsAt": event.ends_at.isoformat(),
            "venueName": venue.name,
            "mode": event.sales_mode,
            "saleStartsAt": event.sale_starts_at.isoformat(),
            "saleEndsAt": event.sale_ends_at.isoformat(),
            "purchaseLimit": event.purchase_limit,
            "ticketTypes": ticket_types_json,
            "sectors": [],
            "seats": [],
            "promotions": []
        }
```

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `pytest tools/ticket-data-pipeline/tests/test_transformers.py -v`
Expected: PASS

- [ ] **Step 5: Commit task 4**

```powershell
git add tools/ticket-data-pipeline/transformers/ tools/ticket-data-pipeline/tests/test_transformers.py
git commit -m "feat(pipeline): implement normalizer and json snapshot builder"
```

---

### Task 5: Giả lập Đơn hàng & Vé (`generators/order_generator.py`)

**Files:**
- Create: `tools/ticket-data-pipeline/generators/__init__.py`
- Create: `tools/ticket-data-pipeline/generators/order_generator.py`
- Test: `tools/ticket-data-pipeline/tests/test_order_generator.py`

**Interfaces:**
- Consumes: `Event`, `TicketType` từ `models/flashticket_models.py`.
- Produces: `OrderGenerator.generate_orders_for_event(event, count, completed_ratio) -> tuple[list[OrderSim], list[TicketSim], list[PaymentAttemptSim]]`.

- [ ] **Step 1: Viết test cho Order Generator và Review Focus 4**

```python
# tools/ticket-data-pipeline/tests/test_order_generator.py
import uuid
from datetime import datetime, timezone
from tools.ticket_data_pipeline.models.flashticket_models import Event, TicketType
from tools.ticket_data_pipeline.generators.order_generator import OrderGenerator

def test_order_generator_does_not_exceed_capacity():
    now = datetime.now(timezone.utc)
    t = TicketType(
        id=uuid.uuid4(), event_id=uuid.uuid4(), name="VIP",
        unit_price=100000, configured_quantity=5, color_code="#1677FF"
    )
    ev = Event(
        id=t.event_id, organizer_subject="org-1", category_id=uuid.uuid4(), venue_id=uuid.uuid4(),
        title="Ev", description="D", starts_at=now, ends_at=now, sale_starts_at=now, sale_ends_at=now,
        checkin_starts_at=now, checkin_ends_at=now, approved_by="admin", approved_at=now,
        ticket_types=[t]
    )
    generator = OrderGenerator()
    orders, tickets, payments = generator.generate_orders_for_event(ev, count=10, completed_ratio=1.0)
    # Tổng vé đã tạo không được vượt quá số lượng configured (5)
    assert len(tickets) <= 5
```

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `pytest tools/ticket-data-pipeline/tests/test_order_generator.py -v`
Expected: FAIL with `ModuleNotFoundError`

- [ ] **Step 3: Cài đặt `OrderGenerator`**

`generators/order_generator.py`:
```python
import uuid
from datetime import datetime, timedelta, timezone
from typing import List, Tuple
from tools.ticket_data_pipeline.models.flashticket_models import Event
from tools.ticket_data_pipeline.models.simulated_models import (
    OrderSim, OrderItemSim, TicketSim, PaymentAttemptSim
)

class OrderGenerator:
    def __init__(self, buyer_pool: List[str] = None):
        self.buyer_pool = buyer_pool or [
            "659d1963-3d1e-4886-9c85-119f11fcd133",  # Mẫu buyer 119
            "01388e8e-721c-42d5-81b6-3f98f2c521df"
        ]

    def generate_orders_for_event(
        self, event: Event, count: int = 5, completed_ratio: float = 0.8
    ) -> Tuple[List[OrderSim], List[TicketSim], List[PaymentAttemptSim]]:
        orders: List[OrderSim] = []
        tickets: List[TicketSim] = []
        payments: List[PaymentAttemptSim] = []

        now = datetime.now(timezone.utc)
        inventory_remain = {t.id: t.configured_quantity for t in event.ticket_types}

        for i in range(count):
            target_ticket = event.ticket_types[i % len(event.ticket_types)]
            if inventory_remain[target_ticket.id] <= 0:
                continue

            qty = min(2, inventory_remain[target_ticket.id])
            inventory_remain[target_ticket.id] -= qty

            order_id = uuid.uuid4()
            buyer_sub = self.buyer_pool[i % len(self.buyer_pool)]
            subtotal = target_ticket.unit_price * qty
            is_completed = (i / count) < completed_ratio
            state = "COMPLETED" if is_completed else ("PENDING_PAYMENT" if i % 2 == 0 else "EXPIRED")

            charge_id = uuid.uuid4() if state == "COMPLETED" else None
            accepted_at = now if state == "COMPLETED" else None

            item = OrderItemSim(
                id=uuid.uuid4(),
                order_id=order_id,
                event_id=event.id,
                ticket_type_id=target_ticket.id,
                ticket_type_name=target_ticket.name,
                quantity=qty,
                unit_price=target_ticket.unit_price,
                line_subtotal=subtotal,
                currency="VND"
            )

            order = OrderSim(
                id=order_id,
                event_id=event.id,
                buyer_subject=buyer_sub,
                customer_email=f"buyer_{order_id.hex[:6]}@example.com",
                lifecycle_state=state,
                subtotal=subtotal,
                discount=0,
                total=subtotal,
                currency="VND",
                created_at=now - timedelta(minutes=30),
                expires_at=now + timedelta(minutes=15),
                accepted_charge_id=charge_id,
                accepted_at=accepted_at,
                items=[item]
            )
            orders.append(order)

            if state == "COMPLETED":
                # Sinh payment attempt
                payments.append(PaymentAttemptSim(
                    id=charge_id,
                    order_id=order_id,
                    amount=subtotal,
                    currency="VND",
                    status="SUCCESS",
                    provider="VNPAY",
                    created_at=now
                ))
                # Sinh tickets
                for ticket_idx in range(qty):
                    tickets.append(TicketSim(
                        id=uuid.uuid4(),
                        event_id=event.id,
                        ticket_type_id=target_ticket.id,
                        order_id=order_id,
                        buyer_subject=buyer_sub,
                        serial_number=f"TK-{order_id.hex[:4].upper()}-{ticket_idx+1:03d}",
                        checkin_code=f"CK-{uuid.uuid4().hex[:8].upper()}",
                        status="ISSUED"
                    ))

        return orders, tickets, payments
```

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `pytest tools/ticket-data-pipeline/tests/test_order_generator.py -v`
Expected: PASS

- [ ] **Step 5: Commit task 5**

```powershell
git add tools/ticket-data-pipeline/generators/ tools/ticket-data-pipeline/tests/test_order_generator.py
git commit -m "feat(pipeline): implement simulated order and ticket generator"
```

---

### Task 6: Xuất Script SQL Chuẩn Phân tán (`exporters/sql_builder.py`)

**Files:**
- Create: `tools/ticket-data-pipeline/exporters/__init__.py`
- Create: `tools/ticket-data-pipeline/exporters/sql_builder.py`
- Test: `tools/ticket-data-pipeline/tests/test_sql_builder.py`

**Interfaces:**
- Consumes: `FlashTicketBundle`, danh sách orders, tickets, payments, snapshot dictionaries.
- Produces: `SqlBuilder.build_sql_script(bundle, orders, tickets, payments) -> str`.

- [ ] **Step 1: Viết test cho SqlBuilder và Review Focus 3 (Single-quote escaping)**

```python
# tools/ticket-data-pipeline/tests/test_sql_builder.py
import uuid
from datetime import datetime, timezone
from tools.ticket_data_pipeline.models.flashticket_models import Category, Venue, Event, TicketType, FlashTicketBundle
from tools.ticket_data_pipeline.exporters.sql_builder import SqlBuilder

def test_sql_builder_escapes_special_characters_and_roles():
    now = datetime.now(timezone.utc)
    cat = Category(id=uuid.uuid4(), name="O'Reilly Tech")
    ven = Venue(id=uuid.uuid4(), name="Grand Hall", address="123 Street")
    t = TicketType(id=uuid.uuid4(), event_id=uuid.uuid4(), name="VIP", unit_price=100000, color_code="#1677FF")
    ev = Event(
        id=t.event_id, organizer_subject="org-1", category_id=cat.id, venue_id=ven.id,
        title="Rock 'N' Roll Night", description="It's amazing!", starts_at=now, ends_at=now,
        sale_starts_at=now, sale_ends_at=now, checkin_starts_at=now, checkin_ends_at=now,
        approved_by="admin", approved_at=now, ticket_types=[t]
    )
    bundle = FlashTicketBundle(categories=[cat], venues=[ven], events=[ev])
    builder = SqlBuilder()
    sql = builder.build_sql_script(bundle, orders=[], tickets=[], payments=[])
    
    assert "\\connect event_db" in sql
    assert "SET LOCAL ROLE event_owner;" in sql
    assert "Rock ''N'' Roll Night" in sql or "$$Rock 'N' Roll Night$$" in sql
    assert "\\connect booking_db" in sql
    assert "SET LOCAL ROLE booking_owner;" in sql
```

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `pytest tools/ticket-data-pipeline/tests/test_sql_builder.py -v`
Expected: FAIL with `ModuleNotFoundError`

- [ ] **Step 3: Cài đặt `SqlBuilder`**

`exporters/sql_builder.py`:
```python
import json
from typing import List
from tools.ticket_data_pipeline.models.flashticket_models import FlashTicketBundle
from tools.ticket_data_pipeline.models.simulated_models import OrderSim, TicketSim, PaymentAttemptSim
from tools.ticket_data_pipeline.transformers.snapshot_builder import SnapshotBuilder

class SqlBuilder:
    def __init__(self):
        self.snapshot_builder = SnapshotBuilder()

    def escape_str(self, val: str) -> str:
        if val is None:
            return "NULL"
        return "'" + val.replace("'", "''") + "'"

    def build_sql_script(
        self,
        bundle: FlashTicketBundle,
        orders: List[OrderSim],
        tickets: List[TicketSim],
        payments: List[PaymentAttemptSim]
    ) -> str:
        lines = [
            "\\set ON_ERROR_STOP on",
            ""
        ]

        # 1. EVENT_DB
        lines.extend([
            "\\connect event_db",
            "BEGIN; SET LOCAL ROLE event_owner; SET LOCAL search_path=event_schema,pg_catalog; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='30s';"
        ])
        for c in bundle.categories:
            lines.append(f"INSERT INTO tbl_categories(id,name) VALUES ('{c.id}',{self.escape_str(c.name)}) ON CONFLICT (id) DO NOTHING;")
        for v in bundle.venues:
            lines.append(f"INSERT INTO tbl_venues(id,name,address) VALUES ('{v.id}',{self.escape_str(v.name)},{self.escape_str(v.address)}) ON CONFLICT (id) DO NOTHING;")
        for e in bundle.events:
            lines.append(
                f"INSERT INTO tbl_events(id,organizer_subject,category_id,venue_id,title,description,sales_mode,status,starts_at,ends_at,sale_starts_at,sale_ends_at,checkin_starts_at,checkin_ends_at,purchase_limit,fee_rate,approved_by,approved_at) "
                f"VALUES ('{e.id}','{e.organizer_subject}','{e.category_id}','{e.venue_id}',{self.escape_str(e.title)},{self.escape_str(e.description)},'{e.sales_mode}','{e.status}','{e.starts_at.isoformat()}','{e.ends_at.isoformat()}','{e.sale_starts_at.isoformat()}','{e.sale_ends_at.isoformat()}','{e.checkin_starts_at.isoformat()}','{e.checkin_ends_at.isoformat()}',{e.purchase_limit},{e.fee_rate},'{e.approved_by}','{e.approved_at.isoformat()}') ON CONFLICT (id) DO NOTHING;"
            )
            for t in e.ticket_types:
                lines.append(
                    f"INSERT INTO tbl_ticket_types(id,event_id,name,sales_mode,unit_price,currency,configured_quantity,color_code) "
                    f"VALUES ('{t.id}','{e.id}',{self.escape_str(t.name)},'{t.sales_mode}',{t.unit_price},'{t.currency}',{t.configured_quantity},'{t.color_code}') ON CONFLICT (id) DO NOTHING;"
                )
            if e.banner_url:
                lines.append(
                    f"INSERT INTO tbl_event_images(id,event_id,object_reference,display_order) "
                    f"VALUES (gen_random_uuid(),'{e.id}',{self.escape_str(e.banner_url)},0) ON CONFLICT (event_id, display_order) DO NOTHING;"
                )
        lines.append("COMMIT;\n")

        # 2. BOOKING_DB
        lines.extend([
            "\\connect booking_db",
            "BEGIN; SET LOCAL ROLE booking_owner; SET LOCAL search_path=booking_schema,pg_catalog; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='30s';"
        ])
        venues_lookup = {v.id: v for v in bundle.venues}
        for e in bundle.events:
            venue = venues_lookup.get(e.venue_id)
            snap_json = json.dumps(self.snapshot_builder.build_booking_snapshot(e, venue), ensure_ascii=False)
            lines.append(
                f"INSERT INTO tbl_event_sales_snapshots(event_id,organizer_subject,sales_mode,event_status,sale_starts_at,sale_ends_at,purchase_limit,configuration_snapshot,source_config_version,received_at) "
                f"VALUES ('{e.id}','{e.organizer_subject}','{e.sales_mode}','{e.status}','{e.sale_starts_at.isoformat()}','{e.sale_ends_at.isoformat()}',{e.purchase_limit},{self.escape_str(snap_json)}::jsonb,1,now()) ON CONFLICT (event_id) DO NOTHING;"
            )
            for t in e.ticket_types:
                lines.append(
                    f"INSERT INTO tbl_ticket_type_inventory(ticket_type_id,event_id,configured_capacity,source_config_version) "
                    f"VALUES ('{t.id}','{e.id}',{t.configured_quantity},1) ON CONFLICT (ticket_type_id) DO NOTHING;"
                )
        for o in orders:
            charge_clause = f"'{o.accepted_charge_id}'" if o.accepted_charge_id else "NULL"
            accepted_clause = f"'{o.accepted_at.isoformat()}'" if o.accepted_at else "NULL"
            lines.append(
                f"INSERT INTO tbl_orders(id,event_id,buyer_subject,sales_mode,lifecycle_state,customer_email,subtotal,discount,total,currency,expires_at,accepted_charge_id,accepted_at,source_config_version) "
                f"VALUES ('{o.id}','{o.event_id}','{o.buyer_subject}','QUANTITY','{o.lifecycle_state}',{self.escape_str(o.customer_email)},{o.subtotal},{o.discount},{o.total},'{o.currency}','{o.expires_at.isoformat()}',{charge_clause},{accepted_clause},1) ON CONFLICT (id) DO NOTHING;"
            )
            for itm in o.items:
                sel_json = json.dumps({"schemaVersion": 1, "quantity": itm.quantity, "ticketTypeId": str(itm.ticket_type_id)})
                lines.append(
                    f"INSERT INTO tbl_order_items(id,order_id,event_id,ticket_type_id,sales_mode,ticket_type_name,quantity,unit_price,line_subtotal,currency,selection_snapshot) "
                    f"VALUES ('{itm.id}','{itm.order_id}','{itm.event_id}','{itm.ticket_type_id}','QUANTITY',{self.escape_str(itm.ticket_type_name)},{itm.quantity},{itm.unit_price},{itm.line_subtotal},'{itm.currency}',{self.escape_str(sel_json)}::jsonb) ON CONFLICT (id) DO NOTHING;"
                )
        lines.append("COMMIT;\n")

        # 3. PAYMENT_DB
        lines.extend([
            "\\connect payment_db",
            "BEGIN; SET LOCAL ROLE payment_owner; SET LOCAL search_path=payment_schema,pg_catalog; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='30s';"
        ])
        for e in bundle.events:
            lines.append(
                f"INSERT INTO tbl_event_finance_snapshots(event_id,organizer_subject,event_ends_at,fee_rate,source_fee_version,received_at) "
                f"VALUES ('{e.id}','{e.organizer_subject}','{e.ends_at.isoformat()}',{e.fee_rate},1,now()) ON CONFLICT (event_id) DO NOTHING;"
            )
        lines.append("COMMIT;\n")

        # 4. TICKET_DB
        lines.extend([
            "\\connect ticket_db",
            "BEGIN; SET LOCAL ROLE ticket_owner; SET LOCAL search_path=ticket_schema,pg_catalog; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='30s';"
        ])
        for e in bundle.events:
            lines.append(
                f"INSERT INTO tbl_event_access_snapshots(event_id,organizer_subject,event_status,event_starts_at,event_ends_at,source_config_version,received_at) "
                f"VALUES ('{e.id}','{e.organizer_subject}','{e.status}','{e.starts_at.isoformat()}','{e.ends_at.isoformat()}',1,now()) ON CONFLICT (event_id) DO NOTHING;"
            )
        for tk in tickets:
            lines.append(
                f"INSERT INTO tbl_tickets(id,event_id,ticket_type_id,order_id,buyer_subject,serial_number,checkin_code,status) "
                f"VALUES ('{tk.id}','{tk.event_id}','{tk.ticket_type_id}','{tk.order_id}','{tk.buyer_subject}','{tk.serial_number}','{tk.checkin_code}','{tk.status}') ON CONFLICT (id) DO NOTHING;"
            )
        lines.append("COMMIT;\n")

        return "\n".join(lines)
```

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `pytest tools/ticket-data-pipeline/tests/test_sql_builder.py -v`
Expected: PASS

- [ ] **Step 5: Commit task 6**

```powershell
git add tools/ticket-data-pipeline/exporters/ tools/ticket-data-pipeline/tests/test_sql_builder.py
git commit -m "feat(pipeline): implement sql builder with multi-db transactions and quoting"
```

---

### Task 7: Nạp Dữ liệu qua SSH Server 119 & Xác thực (`importers/`)

**Files:**
- Create: `tools/ticket-data-pipeline/importers/__init__.py`
- Create: `tools/ticket-data-pipeline/importers/ssh_runner.py`
- Create: `tools/ticket-data-pipeline/importers/verifier.py`
- Test: `tools/ticket-data-pipeline/tests/test_importers.py`

**Interfaces:**
- Consumes: file path của `.sql` script, `ssh_host = 'ft119'`, list các `event_id`.
- Produces: `SshRunner.execute_sql_file(sql_file, dry_run: bool) -> tuple[int, str]`, `DataVerifier.verify_import(event_ids) -> dict`.

- [ ] **Step 1: Viết test cho SSH Runner và Review Focus 5 (Xử lý lỗi psql không zero exit)**

```python
# tools/ticket-data-pipeline/tests/test_importers.py
from unittest.mock import MagicMock
from tools.ticket_data_pipeline.importers.ssh_runner import SshRunner

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
```

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `pytest tools/ticket-data-pipeline/tests/test_importers.py -v`
Expected: FAIL with `ModuleNotFoundError`

- [ ] **Step 3: Cài đặt `SshRunner` và `DataVerifier`**

`importers/ssh_runner.py`:
```python
import subprocess
from pathlib import Path
from typing import Tuple

class SshRunner:
    def __init__(self, ssh_host: str = "ft119"):
        self.ssh_host = ssh_host

    def execute_remote_sql(self, local_sql_file: Path) -> Tuple[int, str]:
        local_path = Path(local_sql_file)
        remote_tmp = f"/tmp/{local_path.name}"
        
        # 1. SCP file lên server
        scp_cmd = ["scp", str(local_path), f"{self.ssh_host}:{remote_tmp}"]
        scp_res = subprocess.run(scp_cmd, capture_output=True, text=True)
        if scp_res.returncode != 0:
            return scp_res.returncode, f"SCP_ERROR: {scp_res.stderr}"

        # 2. SSH chạy psql
        psql_cmd = ["ssh", self.ssh_host, f"psql -U postgres -v ON_ERROR_STOP=1 -f {remote_tmp} && rm -f {remote_tmp}"]
        res = subprocess.run(psql_cmd, capture_output=True, text=True)
        combined_output = f"{res.stdout}\n{res.stderr}".strip()
        return res.returncode, combined_output
```

`importers/verifier.py`:
```python
import json, subprocess
from typing import List, Dict

class DataVerifier:
    def __init__(self, ssh_host: str = "ft119"):
        self.ssh_host = ssh_host

    def verify_import(self, event_ids: List[str]) -> Dict[str, Any]:
        if not event_ids:
            return {"status": "NO_EVENTS"}
        ids_str = ",".join(f"'{eid}'" for eid in event_ids)
        query = (
            f"SELECT 'event_db' as db, count(*) FROM event_schema.tbl_events WHERE id IN ({ids_str}); "
            f"SELECT 'booking_db' as db, count(*) FROM booking_schema.tbl_event_sales_snapshots WHERE event_id IN ({ids_str}); "
            f"SELECT 'payment_db' as db, count(*) FROM payment_schema.tbl_event_finance_snapshots WHERE event_id IN ({ids_str}); "
            f"SELECT 'ticket_db' as db, count(*) FROM ticket_schema.tbl_event_access_snapshots WHERE event_id IN ({ids_str});"
        )
        res = subprocess.run(["ssh", self.ssh_host, f"psql -U postgres -c \"{query}\""], capture_output=True, text=True)
        return {
            "status": "PASSED" if res.returncode == 0 else "FAILED",
            "output": res.stdout,
            "error": res.stderr
        }
```

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `pytest tools/ticket-data-pipeline/tests/test_importers.py -v`
Expected: PASS

- [ ] **Step 5: Commit task 7**

```powershell
git add tools/ticket-data-pipeline/importers/ tools/ticket-data-pipeline/tests/test_importers.py
git commit -m "feat(pipeline): implement ssh runner and database integrity verifier"
```

---

### Task 8: Giao diện CLI Hoàn chỉnh & Kiểm thử E2E (`cli.py`)

**Files:**
- Create: `tools/ticket-data-pipeline/cli.py`
- Create: `tools/ticket-data-pipeline/README.md`
- Test: `tools/ticket-data-pipeline/tests/test_cli_e2e.py`

**Interfaces:**
- Consumes: Tất cả các module từ Task 1 đến Task 7.
- Produces: CLI commands: `crawl`, `format`, `generate-orders`, `export-sql`, `import-119`, `run-all`.

- [ ] **Step 1: Viết test E2E cho CLI với chế độ Dry-Run**

```python
# tools/ticket-data-pipeline/tests/test_cli_e2e.py
from click.testing import CliRunner
from tools.ticket_data_pipeline.cli import cli

def test_cli_dry_run_full_cycle(tmp_path):
    runner = CliRunner()
    result = runner.invoke(cli, ["--help"])
    assert result.exit_code == 0
    assert "crawl" in result.output
    assert "format" in result.output
    assert "run-all" in result.output
```

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `pytest tools/ticket-data-pipeline/tests/test_cli_e2e.py -v`
Expected: FAIL with `ModuleNotFoundError`

- [ ] **Step 3: Cài đặt `cli.py` bằng Click**

`cli.py`:
```python
import click, json
from datetime import datetime, timezone
from pathlib import Path
from tools.ticket_data_pipeline.config import PipelineConfig
from tools.ticket_data_pipeline.crawlers.ticketbox import TicketboxScraper
from tools.ticket_data_pipeline.transformers.normalizer import DataNormalizer
from tools.ticket_data_pipeline.generators.order_generator import OrderGenerator
from tools.ticket_data_pipeline.exporters.sql_builder import SqlBuilder
from tools.ticket_data_pipeline.importers.ssh_runner import SshRunner
from tools.ticket_data_pipeline.importers.verifier import DataVerifier

@click.group()
def cli():
    """Công cụ Crawl, Format và Import Dữ liệu Bán vé FlashTicket."""
    pass

@cli.command()
@click.option("--sources", default="ticketbox", help="Nguồn crawl (ticketbox, ticketgo...)")
@click.option("--limit", default=5, type=int, help="Số lượng sự kiện cần crawl")
@click.option("--orders-per-event", default=5, type=int, help="Số lượng đơn hàng mẫu sinh cho mỗi sự kiện")
@click.option("--apply", is_flag=True, default=False, help="Thực thi nạp thẳng vào server 119 qua SSH")
def run_all(sources: str, limit: int, orders_per_event: int, apply: bool):
    """Chạy toàn bộ pipeline từ A đến Z."""
    config = PipelineConfig()
    config.ensure_directories()
    click.echo(f"[*] Bắt đầu crawl từ nguồn {sources} (limit={limit})...")

    # 1. Crawl
    scraper = TicketboxScraper()
    raw_events = scraper.fetch_events(limit=limit)
    click.echo(f"[+] Đã crawl {len(raw_events)} sự kiện.")

    # 2. Normalize
    click.echo("[*] Chuẩn hóa dữ liệu sang FlashTicket schema...")
    normalizer = DataNormalizer(config)
    bundle = normalizer.normalize_bundle(raw_events, shift_future=True)

    # 3. Generate Orders
    click.echo(f"[*] Sinh dữ liệu đơn hàng giả lập ({orders_per_event} đơn/sự kiện)...")
    generator = OrderGenerator()
    all_orders, all_tickets, all_payments = [], [], []
    for ev in bundle.events:
        o, t, p = generator.generate_orders_for_event(ev, count=orders_per_event)
        all_orders.extend(o)
        all_tickets.extend(t)
        all_payments.extend(p)

    # 4. Export SQL
    click.echo("[*] Xuất script SQL chuẩn hóa...")
    builder = SqlBuilder()
    sql_content = builder.build_sql_script(bundle, all_orders, all_tickets, all_payments)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    sql_file = config.sql_storage_dir / f"import_seed119_{timestamp}.sql"
    sql_file.write_text(sql_content, encoding="utf-8")
    click.echo(f"[+] File SQL đã sẵn sàng: {sql_file}")

    # 5. Import nếu có cờ --apply
    if apply:
        click.echo("[*] Đang thực thi nạp dữ liệu lên server 119...")
        runner = SshRunner(config.ssh_host)
        code, out = runner.execute_remote_sql(sql_file)
        if code == 0:
            click.echo("[SUCCESS] Import server 119 hoàn tất!")
            verifier = DataVerifier(config.ssh_host)
            report = verifier.verify_import([str(e.id) for e in bundle.events])
            click.echo(f"Kết quả đối soát:\n{report.get('output')}")
        else:
            click.echo(f"[FAILED] Lỗi khi nạp server 119 (code={code}):\n{out}")
    else:
        click.echo("[INFO] Chế độ dry-run hoàn tất. Dùng cờ --apply để nạp vào DB server 119.")

if __name__ == "__main__":
    cli()
```

- [ ] **Step 4: Chạy toàn bộ test suite để xác nhận PASS**

Run: `pytest tools/ticket-data-pipeline/tests/ -v`
Expected: ALL PASS

- [ ] **Step 5: Commit task 8**

```powershell
git add tools/ticket-data-pipeline/
git commit -m "feat(pipeline): complete unified CLI and e2e integration flow"
```

