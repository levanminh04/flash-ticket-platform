import uuid
from datetime import datetime, timezone
from tools.ticket_data_pipeline.models.flashticket_models import Category, Venue, Event, TicketType, FlashTicketBundle
from tools.ticket_data_pipeline.exporters.sql_builder import SqlBuilder

def test_sql_builder_escapes_special_characters_and_roles():
    now = datetime.now(timezone.utc)
    cat = Category(id=uuid.uuid4(), name="O'Reilly Tech")
    ven = Venue(id=uuid.uuid4(), name="Grand Hall", address="123 Street")
    ev_id = uuid.uuid4()
    t = TicketType(id=uuid.uuid4(), event_id=ev_id, name="VIP", unit_price=100000, color_code="#1677FF")
    ev = Event(
        id=ev_id, organizer_subject="org-1", category_id=cat.id, venue_id=ven.id,
        title="Rock 'N' Roll Night", description="It's amazing!", starts_at=now, ends_at=now,
        sale_starts_at=now, sale_ends_at=now, checkin_starts_at=now, checkin_ends_at=now,
        approved_by="admin", approved_at=now, ticket_types=[t]
    )
    bundle = FlashTicketBundle(categories=[cat], venues=[ven], events=[ev])
    builder = SqlBuilder()
    sql = builder.build_sql_script(bundle, orders=[], tickets=[], payments=[])
    
    assert "\\connect event_db" in sql
    assert "SET LOCAL ROLE event_owner;" in sql
    assert "Rock ''N'' Roll Night" in sql
    assert "\\connect booking_db" in sql
    assert "SET LOCAL ROLE booking_owner;" in sql
    assert "\\connect payment_db" in sql
    assert "SET LOCAL ROLE payment_owner;" in sql
    assert "\\connect ticket_db" in sql
    assert "SET LOCAL ROLE ticket_owner;" in sql

