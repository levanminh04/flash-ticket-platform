from datetime import datetime, timezone
import uuid
from tools.ticket_data_pipeline.models.flashticket_models import Category, Venue, TicketType, Event
from tools.ticket_data_pipeline.models.raw_models import RawTicketType, RawEventDetail
from tools.ticket_data_pipeline.models.simulated_models import OrderSim, TicketSim

def test_category_and_venue_creation():
    cat = Category(id=uuid.uuid4(), name="Âm nhạc")
    assert cat.name == "Âm nhạc"
    ven = Venue(id=uuid.uuid4(), name="Nhà thi đấu QK7", address="202 Hoàng Văn Thụ, TP.HCM")
    assert ven.name == "Nhà thi đấu QK7"

def test_event_and_ticket_types():
    now = datetime.now(timezone.utc)
    ev_id = uuid.uuid4()
    t_type = TicketType(
        id=uuid.uuid4(),
        event_id=ev_id,
        name="VIP",
        unit_price=500000,
        currency="VND",
        configured_quantity=100,
        color_code="#1677FF"
    )
    assert t_type.unit_price == 500000
    assert t_type.currency == "VND"

def test_raw_models():
    raw_t = RawTicketType(name="GA", price_text="200.000", price_number=200000, quota=50)
    assert raw_t.price_number == 200000
    raw_ev = RawEventDetail(
        source="test",
        source_id="123",
        title="Event 1",
        description="Desc",
        venue_name="Venue",
        venue_address="Address",
        category_name="Music",
        ticket_types=[raw_t]
    )
    assert len(raw_ev.ticket_types) == 1
