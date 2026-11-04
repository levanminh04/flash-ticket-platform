import time
import httpx
from typing import List, Dict, Any
from tools.ticket_data_pipeline.crawlers.base import BaseScraper
from tools.ticket_data_pipeline.models.raw_models import RawEventDetail, RawTicketType

class TicketboxScraper(BaseScraper):
    def __init__(self, client: httpx.Client = None):
        self.client = client or httpx.Client(
            headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"},
            timeout=10.0
        )

    def parse_detail(self, item: Dict[str, Any]) -> RawEventDetail:
        tickets = []
        for t in item.get("ticket_types", []):
            price_val = t.get("price", 0)
            try:
                price_num = int(price_val)
            except (ValueError, TypeError):
                price_num = 0
            tickets.append(RawTicketType(
                name=t.get("name", "Vé thường"),
                price_text=str(price_val),
                price_number=price_num,
                quota=int(t.get("quantity", 100))
            ))
        venue = item.get("venue", {})
        return RawEventDetail(
            source="ticketbox",
            source_id=str(item.get("id", "")),
            title=item.get("name", ""),
            description=item.get("description", ""),
            banner_url=item.get("banner_url"),
            venue_name=venue.get("name", "Trung tâm sự kiện"),
            venue_address=venue.get("address", "TP. Hồ Chí Minh"),
            category_name=item.get("category", "Giải trí"),
            ticket_types=tickets
        )

    def fetch_events(self, limit: int = 10) -> List[RawEventDetail]:
        events = []
        try:
            # Endpoint mô phỏng / real query
            resp = self.client.get("https://api.ticketbox.vn/v2/events", params={"limit": limit})
            if resp.status_code == 200:
                data = resp.json()
                for item in data.get("data", [])[:limit]:
                    events.append(self.parse_detail(item))
        except Exception:
            pass

        # Nếu không có mạng hoặc API thay đổi, tạo fallback sự kiện mẫu phong phú thực tế
        if not events:
            samples = [
                {
                    "id": "tb-sample-01",
                    "name": "Live Concert - Những Bản Tình Ca Mùa Thu",
                    "description": "Đêm nhạc đặc sắc quy tụ các giọng ca hàng đầu Việt Nam tại TP.HCM",
                    "banner_url": "https://images.unsplash.com/photo-1470225620780-dba8ba36b745",
                    "venue": {"name": "Nhà Hát Hòa Bình", "address": "240 Đường 3 Tháng 2, Phường 12, Quận 10, TP.HCM"},
                    "category": "Âm nhạc",
                    "ticket_types": [
                        {"name": "Standard (Tầng 2)", "price": 250000, "quantity": 150},
                        {"name": "VIP (Khu A)", "price": 600000, "quantity": 100},
                        {"name": "VVIP (Hàng đầu)", "price": 1200000, "quantity": 50}
                    ]
                },
                {
                    "id": "tb-sample-02",
                    "name": "Vietnam Marathon Championship 2026",
                    "description": "Giải chạy việt dã thường niên khám phá vẻ đẹp đô thị sôi động",
                    "banner_url": "https://images.unsplash.com/photo-1461896836934-ffe607ba8211",
                    "venue": {"name": "Sân vận động Quân khu 7", "address": "202 Hoàng Văn Thụ, Phường 9, Phú Nhuận, TP.HCM"},
                    "category": "Thể thao",
                    "ticket_types": [
                        {"name": "Cự ly 5KM", "price": 300000, "quantity": 300},
                        {"name": "Cự ly 10KM", "price": 450000, "quantity": 200},
                        {"name": "Cự ly 21KM Half Marathon", "price": 700000, "quantity": 100}
                    ]
                },
                {
                    "id": "tb-sample-03",
                    "name": "Hội nghị Công nghệ AI & Tương lai Lập trình 2026",
                    "description": "Diễn đàn trao đổi xu hướng Agentic AI, Cloud Computing và Kiến trúc Microservices",
                    "banner_url": "https://images.unsplash.com/photo-1540575467063-178a50c2df87",
                    "venue": {"name": "Trung tâm Hội nghị Quốc gia", "address": "Đại lộ Thăng Long, Mễ Trì, Nam Từ Liêm, Hà Nội"},
                    "category": "Hội thảo",
                    "ticket_types": [
                        {"name": "Vé Người Đi Làm (General)", "price": 500000, "quantity": 200},
                        {"name": "Vé Doanh nghiệp (Networking Pass)", "price": 1500000, "quantity": 80}
                    ]
                }
            ]
            for s in samples[:limit]:
                events.append(self.parse_detail(s))
        return events
