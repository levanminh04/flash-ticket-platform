import httpx
from typing import List, Dict, Any
from tools.ticket_data_pipeline.crawlers.base import BaseScraper
from tools.ticket_data_pipeline.models.raw_models import RawEventDetail, RawTicketType

class TicketmasterScraper(BaseScraper):
    def __init__(self, client: httpx.Client = None):
        self.client = client or httpx.Client(
            headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"},
            timeout=10.0
        )

    def parse_detail(self, item: Dict[str, Any]) -> RawEventDetail:
        tickets = []
        for t in item.get("priceRanges", item.get("ticket_types", [])):
            price_val = t.get("min", t.get("price", 0))
            try:
                price_num = int(price_val)
            except (ValueError, TypeError):
                price_num = 0
            tickets.append(RawTicketType(
                name=t.get("type", t.get("name", "Tier Pass")),
                price_text=str(price_val),
                price_number=price_num,
                quota=int(t.get("quota", 200))
            ))
        venue = item.get("venue", {})
        return RawEventDetail(
            source="ticketmaster",
            source_id=str(item.get("id", "")),
            title=item.get("name", item.get("title", "")),
            description=item.get("description", ""),
            banner_url=item.get("banner_url"),
            venue_name=venue.get("name", "Sân vận động Quốc tế"),
            venue_address=venue.get("address", "Đông Nam Á"),
            category_name=item.get("category", "World Tour Concert"),
            ticket_types=tickets
        )

    def fetch_events(self, limit: int = 10) -> List[RawEventDetail]:
        events = []
        samples = [
            {
                "id": "tm-01",
                "name": "Coldplay: Music of the Spheres World Tour - Live in Hanoi",
                "description": "Siêu tour diễn toàn cầu sử dụng 100% năng lượng tái tạo cùng trải nghiệm vòng đeo tay phát sáng kỳ ảo",
                "banner_url": "https://images.unsplash.com/photo-1470225620780-dba8ba36b745",
                "venue": {
                    "name": "Sân vận động Quốc gia Mỹ Đình",
                    "address": "Đường Lê Đức Thọ, Mỹ Đình, Nam Từ Liêm, Hà Nội"
                },
                "category": "World Stadium Tour",
                "ticket_types": [
                    {"name": "CAT 5 (Khán đài C/D)", "price": 1200000, "quota": 400},
                    {"name": "CAT 2 (Khán đài A/B Tầng 2)", "price": 2800000, "quota": 250},
                    {"name": "Standing Floor GA (Sân vận động)", "price": 3500000, "quota": 500},
                    {"name": "Infinity VIP Lounge", "price": 6500000, "quota": 80}
                ]
            },
            {
                "id": "tm-02",
                "name": "BLACKPINK World Tour [Born Pink Encore] - Vietnam Stadium",
                "description": "Đêm nhạc bùng nổ của nhóm nhạc nữ toàn cầu với sân khấu âm thanh ánh sáng đỉnh cao quốc tế",
                "banner_url": "https://images.unsplash.com/photo-1501386761578-eac5c94b800a",
                "venue": {
                    "name": "Sân vận động Quân khu 7",
                    "address": "202 Hoàng Văn Thụ, Phường 9, Phú Nhuận, TP.HCM"
                },
                "category": "K-Pop World Tour",
                "ticket_types": [
                    {"name": "CAT 4 (Seated)", "price": 1800000, "quota": 300},
                    {"name": "Standing GA (Fanzone)", "price": 4200000, "quota": 350},
                    {"name": "VIP Soundcheck Package", "price": 7800000, "quota": 60}
                ]
            }
        ]
        for s in samples[:limit]:
            events.append(self.parse_detail(s))
        return events

