import uuid
from datetime import datetime, timezone
from tools.ticket_data_pipeline.models.flashticket_models import Event, TicketType
from tools.ticket_data_pipeline.generators.order_generator import OrderGenerator

def test_order_generator_does_not_exceed_capacity():
    now = datetime.now(timezone.utc)
    ev_id = uuid.uuid4()
    t = TicketType(
        id=uuid.uuid4(), event_id=ev_id, name="VIP",
        unit_price=100000, configured_quantity=5, color_code="#1677FF"
    )
    ev = Event(
        id=ev_id, organizer_subject="org-1", category_id=uuid.uuid4(), venue_id=uuid.uuid4(),
        title="Ev", description="D", starts_at=now, ends_at=now, sale_starts_at=now, sale_ends_at=now,
        checkin_starts_at=now, checkin_ends_at=now, approved_by="admin", approved_at=now,
        ticket_types=[t]
    )
    generator = OrderGenerator()
    orders, tickets, payments = generator.generate_orders_for_event(ev, count=10, completed_ratio=1.0)
    # Tổng vé đã tạo không được vượt quá số lượng configured (5)
    assert len(tickets) <= 5
    assert len(orders) > 0
    assert len(payments) == len(orders)
    assert orders[0].lifecycle_state == "COMPLETED"
    assert orders[0].accepted_charge_id is not None
