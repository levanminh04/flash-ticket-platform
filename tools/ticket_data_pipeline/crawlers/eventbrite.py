import time
import httpx
from typing import List, Dict, Any
from tools.ticket_data_pipeline.crawlers.base import BaseScraper
from tools.ticket_data_pipeline.models.raw_models import RawEventDetail, RawTicketType

class EventbriteScraper(BaseScraper):
    def __init__(self, client: httpx.Client = None):
        self.client = client or httpx.Client(
            headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"},
            timeout=10.0
        )

    def parse_detail(self, item: Dict[str, Any]) -> RawEventDetail:
        tickets = []
        for t in item.get("ticket_classes", []):
            cost = t.get("cost", {})
            price_val = cost.get("value", t.get("price", 0))
            try:
                price_num = int(price_val)
            except (ValueError, TypeError):
                price_num = 0
            tickets.append(RawTicketType(
                name=t.get("name", "Standard Ticket"),
                price_text=str(price_val),
                price_number=price_num,
                quota=int(t.get("quantity_total", 100))
            ))
        venue = item.get("venue") or {}
        raw_name = item.get("name")
        title = raw_name.get("text", "") if isinstance(raw_name, dict) else (item.get("title") or "")

        raw_desc = item.get("description")
        description = raw_desc.get("text", "") if isinstance(raw_desc, dict) else (raw_desc or "Mô tả sự kiện")

        raw_logo = item.get("logo")
        banner = raw_logo.get("url") if isinstance(raw_logo, dict) else item.get("banner_url")

        raw_addr = venue.get("address")
        addr_str = raw_addr.get("localized_address_display", "Việt Nam") if isinstance(raw_addr, dict) else (raw_addr or "Việt Nam")

        return RawEventDetail(
            source="eventbrite",
            source_id=str(item.get("id", "")),
            title=title,
            description=description,
            banner_url=banner,
            venue_name=venue.get("name", "Trung tâm Hội nghị Quốc tế"),
            venue_address=addr_str,
            category_name=item.get("category", "Hội nghị & Công nghệ"),
            ticket_types=tickets
        )

    def fetch_events(self, limit: int = 10) -> List[RawEventDetail]:
        events = []
        samples = [
            {
                "id": "eb-01",
                "title": "Vietnam Tech & Startup Expo 2026 - Kết Nối Đầu Tư Công Nghệ",
                "description": "Diễn đàn kết nối hơn 200 quỹ đầu tư mạo hiểm và 500 startup công nghệ hàng đầu Đông Nam Á",
                "banner_url": "https://images.unsplash.com/photo-1511578314322-379afb476865",
                "venue": {
                    "name": "Trung tâm Hội chợ và Triển lãm Sài Gòn (SECC)",
                    "address": {"localized_address_display": "799 Nguyễn Văn Linh, Tân Phú, Quận 7, TP.HCM"}
                },
                "category": "Triển lãm & Đầu tư",
                "ticket_classes": [
                    {"name": "Visitor Pass (1 Day)", "price": 150000, "quantity_total": 500},
                    {"name": "Founder Pass (3 Days + Networking Lounge)", "price": 850000, "quantity_total": 200},
                    {"name": "Investor VIP Pass (Gala Dinner Access)", "price": 2500000, "quantity_total": 50}
                ]
            },
            {
                "id": "eb-02",
                "title": "Fintech & Web3 Vietnam Summit 2026",
                "description": "Xu hướng thanh toán số, ngân hàng mở (Open Banking) và bảo mật giao dịch tài chính thế hệ mới",
                "banner_url": "https://images.unsplash.com/photo-1559526324-4b87b5e36e44",
                "venue": {
                    "name": "Khách sạn Lotte Hotel Hanoi",
                    "address": {"localized_address_display": "54 Liễu Giai, Cống Vị, Ba Đình, Hà Nội"}
                },
                "category": "Tài chính & Fintech",
                "ticket_classes": [
                    {"name": "Conference Pass", "price": 600000, "quantity_total": 150},
                    {"name": "Executive Pass", "price": 1800000, "quantity_total": 60}
                ]
            },
            {
                "id": "eb-03",
                "title": "Creative UI/UX & Design Thinking Masterclass 2026",
                "description": "Khóa huấn luyện chuyên sâu về thiết kế trải nghiệm người dùng cùng các Lead Product Designer hàng đầu",
                "banner_url": "https://images.unsplash.com/photo-1531403009284-440f080d1e12",
                "venue": {
                    "name": "Toong Coworking Space",
                    "address": {"localized_address_display": "8 Tràng Thi, Hàng Trống, Hoàn Kiếm, Hà Nội"}
                },
                "category": "Thiết kế & Sáng tạo",
                "ticket_classes": [
                    {"name": "Standard Workshop Pass", "price": 400000, "quantity_total": 60},
                    {"name": "Portfolio Review 1-on-1 Pass", "price": 900000, "quantity_total": 20}
                ]
            }
        ]
        for s in samples[:limit]:
            events.append(self.parse_detail(s))
        return events
