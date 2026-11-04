from datetime import datetime, timezone
from tools.ticket_data_pipeline.config import PipelineConfig
from tools.ticket_data_pipeline.models.raw_models import RawEventDetail, RawTicketType
from tools.ticket_data_pipeline.transformers.normalizer import DataNormalizer
from tools.ticket_data_pipeline.transformers.snapshot_builder import SnapshotBuilder

def test_normalizer_shifts_dates_to_future_and_parses_price():
    config = PipelineConfig()
    normalizer = DataNormalizer(config)
    raw = RawEventDetail(
        source="test", source_id="1", title="Concert X", description="Desc",
        venue_name="V1", venue_address="A1", category_name="Music",
        ticket_types=[RawTicketType(name="GA", price_text="250.000đ", price_number=None, quota=100)]
    )
    bundle = normalizer.normalize_bundle([raw], shift_future=True)
    assert len(bundle.events) == 1
    ev = bundle.events[0]
    assert ev.sale_starts_at < ev.sale_ends_at <= ev.ends_at
    assert ev.checkin_starts_at == ev.starts_at
    assert ev.checkin_ends_at == ev.ends_at
    assert ev.ticket_types[0].unit_price == 250000
    assert ev.ticket_types[0].currency == "VND"

def test_snapshot_builder_schema_version():
    config = PipelineConfig()
    normalizer = DataNormalizer(config)
    raw = RawEventDetail(
        source="test", source_id="1", title="Concert X", description="Desc",
        venue_name="V1", venue_address="A1", category_name="Music",
        ticket_types=[RawTicketType(name="GA", price_text="50000", price_number=50000, quota=50)]
    )
    bundle = normalizer.normalize_bundle([raw], shift_future=True)
    ev = bundle.events[0]
    builder = SnapshotBuilder()
    sales_snap = builder.build_booking_snapshot(ev, bundle.venues[0])
    assert sales_snap["schemaVersion"] == 1
    assert sales_snap["mode"] == "QUANTITY"
    assert len(sales_snap["ticketTypes"]) == 1
    assert sales_snap["ticketTypes"][0]["currency"] == "VND"
