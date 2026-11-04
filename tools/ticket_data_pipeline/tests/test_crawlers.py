import pytest
from tools.ticket_data_pipeline.crawlers.ticketbox import TicketboxScraper
from tools.ticket_data_pipeline.crawlers.ticketgo import TicketgoScraper
from tools.ticket_data_pipeline.crawlers.megatix import MegatixScraper
from tools.ticket_data_pipeline.crawlers.actiup import ActiupScraper
from tools.ticket_data_pipeline.crawlers.eventbrite import EventbriteScraper
from tools.ticket_data_pipeline.crawlers.klook import KlookScraper
from tools.ticket_data_pipeline.crawlers.ticketmaster import TicketmasterScraper
from tools.ticket_data_pipeline.crawlers.moveek import MoveekScraper
from tools.ticket_data_pipeline.crawlers.irace import IRaceScraper
from tools.ticket_data_pipeline.crawlers.ticketmelon import TicketmelonScraper
from tools.ticket_data_pipeline.crawlers.feverup import FeverUpScraper
from tools.ticket_data_pipeline.crawlers.peatix import PeatixScraper
from tools.ticket_data_pipeline.crawlers.interpark import InterparkScraper
from tools.ticket_data_pipeline.crawlers.todaytix import TodayTixScraper
from tools.ticket_data_pipeline.crawlers.ra import ResidentAdvisorScraper
from tools.ticket_data_pipeline.crawlers.axs import AxsScraper

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

def test_megatix_fetch_events():
    scraper = MegatixScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "megatix"
    assert len(events[0].ticket_types) > 0

def test_actiup_fetch_events():
    scraper = ActiupScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "actiup"
    assert "Trail" in events[0].title or "Marathon" in events[0].title

def test_eventbrite_fetch_events():
    scraper = EventbriteScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "eventbrite"
    assert len(events[0].ticket_types) > 0

def test_klook_fetch_events():
    scraper = KlookScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "klook"
    assert len(events[0].ticket_types) > 0

def test_ticketmaster_fetch_events():
    scraper = TicketmasterScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "ticketmaster"
    assert len(events[0].ticket_types) > 0

def test_moveek_fetch_events():
    scraper = MoveekScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "moveek"
    assert len(events[0].ticket_types) > 0

def test_irace_fetch_events():
    scraper = IRaceScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "irace"
    assert len(events[0].ticket_types) > 0
    assert "Marathon" in events[0].category_name or "Trail" in events[0].category_name

def test_ticketmelon_fetch_events():
    scraper = TicketmelonScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "ticketmelon"
    assert len(events[0].ticket_types) > 0

def test_feverup_fetch_events():
    scraper = FeverUpScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "feverup"
    assert len(events[0].ticket_types) > 0

def test_peatix_fetch_events():
    scraper = PeatixScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "peatix"
    assert len(events[0].ticket_types) > 0

def test_interpark_fetch_events():
    scraper = InterparkScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "interpark"
    assert len(events[0].ticket_types) > 0

def test_todaytix_fetch_events():
    scraper = TodayTixScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "todaytix"
    assert len(events[0].ticket_types) > 0

def test_ra_fetch_events():
    scraper = ResidentAdvisorScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "ra"
    assert len(events[0].ticket_types) > 0

def test_axs_fetch_events():
    scraper = AxsScraper()
    events = scraper.fetch_events(limit=2)
    assert len(events) == 2
    assert events[0].source == "axs"
    assert len(events[0].ticket_types) > 0



