import pytest
from tools.ticket_data_pipeline.crawlers.ticketbox import TicketboxScraper
from tools.ticket_data_pipeline.crawlers.ticketgo import TicketgoScraper

def test_ticketbox_parse_event_payload():
    scraper = TicketboxScraper()
    mock_payload = {
        "id": "tb-101",
        "name": "Live Concert 2026",
        "description": "Đêm nhạc đặc biệt",
        "venue": {"name": "Nhà hát Hòa Bình", "address": "240 đường 3/2, Q.10"},
        "category": "Music",
        "ticket_types": [
            {"name": "VIP Zone", "price": 1000000, "quantity": 50},
            {"name": "Standard", "price": 400000, "quantity": 200}
        ]
    }
    event = scraper.parse_detail(mock_payload)
    assert event.source == "ticketbox"
    assert event.title == "Live Concert 2026"
    assert len(event.ticket_types) == 2
    assert event.ticket_types[0].price_number == 1000000

def test_ticketgo_parse_event_payload():
    scraper = TicketgoScraper()
    mock_payload = {
        "id": "tg-202",
        "title": "Hội thảo AI 2026",
        "description": "Chia sẻ về Generative AI",
        "location": "Khách sạn Daewoo, Hà Nội",
        "category_title": "Công nghệ",
        "prices": [
            {"title": "Vé tham dự", "price_value": 300000, "stock": 100}
        ]
    }
    event = scraper.parse_detail(mock_payload)
    assert event.source == "ticketgo"
    assert event.title == "Hội thảo AI 2026"
    assert len(event.ticket_types) == 1
    assert event.ticket_types[0].price_number == 300000
