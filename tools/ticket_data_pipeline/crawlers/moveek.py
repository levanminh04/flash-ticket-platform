import httpx
from typing import List, Dict, Any
from tools.ticket_data_pipeline.crawlers.base import BaseScraper
from tools.ticket_data_pipeline.models.raw_models import RawEventDetail, RawTicketType

class MoveekScraper(BaseScraper):
    def __init__(self, client: httpx.Client = None):
        self.client = client or httpx.Client(
            headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"},
            timeout=10.0
        )

    def parse_detail(self, item: Dict[str, Any]) -> RawEventDetail:
        tickets = []
        for t in item.get("showtimes", item.get("ticket_types", [])):
            price_val = t.get("price", 0)
            try:
                price_num = int(price_val)
            except (ValueError, TypeError):
                price_num = 0
            tickets.append(RawTicketType(
                name=t.get("name", "Vé xem phim"),
                price_text=str(price_val),
                price_number=price_num,
                quota=int(t.get("quota", 100))
            ))
        venue = item.get("venue", {})
        return RawEventDetail(
            source="moveek",
            source_id=str(item.get("id", "")),
            title=item.get("title", ""),
            description=item.get("description", ""),
            banner_url=item.get("banner_url"),
            venue_name=venue.get("name", "Cụm rạp chiếu phim"),
            venue_address=venue.get("address", "Việt Nam"),
            category_name=item.get("category", "Điện ảnh & Chiếu phim"),
            ticket_types=tickets
        )

    def fetch_events(self, limit: int = 10) -> List[RawEventDetail]:
        events = []
        samples = [
            {
                "id": "mov-01",
                "title": "Suất Chiếu Đặc Biệt: Liên Hoan Phim Quốc Tế Hà Nội (HANIFF 2026)",
                "description": "Tuần lễ công chiếu các tác phẩm điện ảnh xuất sắc từng đoạt giải thưởng tại Cannes, Venice và Berlin",
                "banner_url": "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba",
                "venue": {
                    "name": "Trung tâm Chiếu phim Quốc gia",
                    "address": "87 Láng Hạ, Thành Công, Ba Đình, Hà Nội"
                },
                "category": "Liên Hoan Phim",
                "ticket_types": [
                    {"name": "Vé Suất Chiếu Ban Ngày", "price": 90000, "quota": 160},
                    {"name": "Vé Gala Khai Mạc & Giao Lưu Đạo Diễn", "price": 250000, "quota": 80}
                ]
            },
            {
                "id": "mov-02",
                "title": "Đêm Chiếu Phim Kinh Điển Dưới Trời Sao - Open Air Cinema 2026",
                "description": "Trải nghiệm xem phim ngoài trời bên thảm cỏ công viên với màn hình khổng lồ và tai nghe không dây chất lượng cao",
                "banner_url": "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c",
                "venue": {
                    "name": "Công viên Landmark 81 Riverside",
                    "address": "720A Điện Biên Phủ, Phường 22, Bình Thạnh, TP.HCM"
                },
                "category": "Điện Ảnh Ngoài Trời",
                "ticket_types": [
                    {"name": "Vé Ghế Lười (Beanbag Pass)", "price": 180000, "quota": 100},
                    {"name": "Combo Couple (2 Ghế + Bỏng Nước)", "price": 380000, "quota": 50}
                ]
            }
        ]
        for s in samples[:limit]:
            events.append(self.parse_detail(s))
        return events

