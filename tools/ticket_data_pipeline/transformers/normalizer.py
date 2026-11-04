import re
import uuid
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
        return 50000

    def normalize_bundle(self, raw_events: List[RawEventDetail], shift_future: bool = True) -> FlashTicketBundle:
        categories_map: Dict[str, Category] = {}
        venues_map: Dict[str, Venue] = {}
        events: List[Event] = []

        now = datetime.now(timezone.utc)
        color_palette = ["#1677FF", "#52C41A", "#FAAD14", "#F5222D", "#722ED1"]

        for raw in raw_events:
            # 1. Category
            cat_name = raw.category_name.strip() if raw.category_name else "Sự kiện"
            if cat_name not in categories_map:
                categories_map[cat_name] = Category(id=uuid.uuid4(), name=cat_name)
            category = categories_map[cat_name]

            # 2. Venue
            ven_name = raw.venue_name.strip() if raw.venue_name else "Trung tâm sự kiện"
            ven_addr = raw.venue_address.strip() if raw.venue_address else "Việt Nam"
            ven_key = f"{ven_name}_{ven_addr}"
            if ven_key not in venues_map:
                venues_map[ven_key] = Venue(id=uuid.uuid4(), name=ven_name, address=ven_addr)
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
                    name=raw_t.name[:50] if raw_t.name else f"Hạng vé {idx+1}",
                    sales_mode="QUANTITY",
                    unit_price=price,
                    currency="VND",
                    configured_quantity=raw_t.quota or 100,
                    color_code=color
                ))

            if not ticket_types:
                ticket_types.append(TicketType(
                    id=uuid.uuid4(),
                    event_id=event_id,
                    name="Vé tiêu chuẩn",
                    sales_mode="QUANTITY",
                    unit_price=100000,
                    currency="VND",
                    configured_quantity=100,
                    color_code="#1677FF"
                ))

            events.append(Event(
                id=event_id,
                organizer_subject=self.config.organizer_subject,
                category_id=category.id,
                venue_id=venue.id,
                title=raw.title[:200] if raw.title else "Sự kiện",
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

