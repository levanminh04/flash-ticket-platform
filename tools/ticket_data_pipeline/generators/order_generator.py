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
            "01388e8e-721c-42d5-81b6-3f98f2c521df",
            "ae3a05cd-95c2-47ec-a041-9c9de198c4a1"
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
            if not event.ticket_types:
                break
            target_ticket = event.ticket_types[i % len(event.ticket_types)]
            available = inventory_remain.get(target_ticket.id, 0)
            if available <= 0:
                continue

            qty = min(2, available)
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

