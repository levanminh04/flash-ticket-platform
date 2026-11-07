from datetime import datetime
from typing import List, Optional
from uuid import UUID
from pydantic import BaseModel

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

