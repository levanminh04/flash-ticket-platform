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
