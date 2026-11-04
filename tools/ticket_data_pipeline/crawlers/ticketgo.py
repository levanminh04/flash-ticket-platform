import time
import httpx
from typing import List, Dict, Any
from tools.ticket_data_pipeline.crawlers.base import BaseScraper
from tools.ticket_data_pipeline.models.raw_models import RawEventDetail, RawTicketType

class TicketgoScraper(BaseScraper):
    def __init__(self, client: httpx.Client = None):
        self.client = client or httpx.Client(
            headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"},
            timeout=10.0
        )

    def parse_detail(self, item: Dict[str, Any]) -> RawEventDetail:
        tickets = []
        for p in item.get("prices", []):
            price_val = p.get("price_value", 0)
            try:
                price_num = int(price_val)
            except (ValueError, TypeError):
                price_num = 0
            tickets.append(RawTicketType(
                name=p.get("title", "Vé sự kiện"),
                price_text=str(price_val),
                price_number=price_num,
                quota=int(p.get("stock", 100))
            ))
        return RawEventDetail(
            source="ticketgo",
            source_id=str(item.get("id", "")),
            title=item.get("title", ""),
            description=item.get("description", ""),
            banner_url=item.get("banner_url"),
            venue_name=item.get("location", "Trung tâm sự kiện"),
            venue_address=item.get("location", "Hà Nội"),
            category_name=item.get("category_title", "Văn hóa"),
            ticket_types=tickets
        )

    def fetch_events(self, limit: int = 10) -> List[RawEventDetail]:
        events = []
        samples = [
            {
                "id": "tg-sample-01",
                "title": "Nhạc Kịch 'Chuyện Phố Cổ Hà Nội'",
                "description": "Vở kịch tái hiện không gian văn hóa Hà Nội xưa đầy hoài niệm và cảm xúc",
                "banner_url": "https://images.unsplash.com/photo-1507676184212-d03ab07a01bf",
                "location": "Nhà hát Lớn Hà Nội, 1 Tràng Tiền, Hoàn Kiếm, Hà Nội",
                "category_title": "Sân khấu & Kịch",
                "prices": [
                    {"title": "Hạng Phổ Thông", "price_value": 350000, "stock": 120},
                    {"title": "Hạng Thượng Khách", "price_value": 800000, "stock": 60}
                ]
            },
            {
                "id": "tg-sample-02",
                "title": "Workshop Sáng Tạo Gốm Thủ Công",
                "description": "Trải nghiệm tự tay làm gốm vuốt men truyền thống cùng nghệ nhân",
                "banner_url": "https://images.unsplash.com/photo-1565193566173-7a0ee3dbe261",
                "location": "Làng gốm Bát Tràng, Gia Lâm, Hà Nội",
                "category_title": "Workshop",
                "prices": [
                    {"title": "Vé Cá Nhân", "price_value": 250000, "stock": 40},
                    {"title": "Vé Cặp Đôi", "price_value": 450000, "stock": 20}
                ]
            }
        ]
        for s in samples[:limit]:
            events.append(self.parse_detail(s))
        return events

