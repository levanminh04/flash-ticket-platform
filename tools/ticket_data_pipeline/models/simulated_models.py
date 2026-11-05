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

