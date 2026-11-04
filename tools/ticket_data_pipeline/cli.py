import sys
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

import click
import json
from datetime import datetime, timezone
from pathlib import Path
from tools.ticket_data_pipeline.config import PipelineConfig
from tools.ticket_data_pipeline.crawlers.ticketbox import TicketboxScraper
from tools.ticket_data_pipeline.crawlers.ticketgo import TicketgoScraper
from tools.ticket_data_pipeline.crawlers.megatix import MegatixScraper
from tools.ticket_data_pipeline.crawlers.actiup import ActiupScraper
from tools.ticket_data_pipeline.crawlers.eventbrite import EventbriteScraper
from tools.ticket_data_pipeline.transformers.normalizer import DataNormalizer
from tools.ticket_data_pipeline.generators.order_generator import OrderGenerator
from tools.ticket_data_pipeline.exporters.sql_builder import SqlBuilder
from tools.ticket_data_pipeline.importers.ssh_runner import SshRunner
from tools.ticket_data_pipeline.importers.verifier import DataVerifier

@click.group()
def cli():
    """FlashTicket Ticket Data Pipeline - Crawl, Format và Import vào Server 119."""
    pass

@cli.command("crawl")
@click.option("--source", default="all", type=click.Choice(["ticketbox", "ticketgo", "megatix", "actiup", "eventbrite", "all"]), help="Nguồn crawl")
@click.option("--limit", default=5, type=int, help="Số lượng sự kiện cần crawl mỗi nguồn")
def crawl_cmd(source: str, limit: int):
    """Crawl dữ liệu thô từ các website vé và lưu vào storage/raw/."""
    config = PipelineConfig()
    config.ensure_directories()
    results = []
    
    if source in ("ticketbox", "all"):
        click.echo(f"[*] Crawling từ Ticketbox (limit={limit})...")
        results.extend(TicketboxScraper().fetch_events(limit=limit))
        
    if source in ("ticketgo", "all"):
        click.echo(f"[*] Crawling từ Ticketgo (limit={limit})...")
        results.extend(TicketgoScraper().fetch_events(limit=limit))

    if source in ("megatix", "all"):
        click.echo(f"[*] Crawling từ Megatix (limit={limit})...")
        results.extend(MegatixScraper().fetch_events(limit=limit))

    if source in ("actiup", "all"):
        click.echo(f"[*] Crawling từ ActiUp (limit={limit})...")
        results.extend(ActiupScraper().fetch_events(limit=limit))

    if source in ("eventbrite", "all"):
        click.echo(f"[*] Crawling từ Eventbrite (limit={limit})...")
        results.extend(EventbriteScraper().fetch_events(limit=limit))
        
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    out_file = config.raw_storage_dir / f"raw_events_{source}_{timestamp}.json"
    out_file.write_text(json.dumps([r.model_dump() for r in results], ensure_ascii=False, indent=2), encoding="utf-8")
    click.echo(f"[+] Đã crawl {len(results)} sự kiện. Lưu cache tại: {out_file}")

@cli.command("format")
@click.option("--input-file", type=click.Path(exists=True), help="Đường dẫn file raw JSON")
@click.option("--shift-future/--no-shift-future", default=True, help="Tự động dời ngày mở bán và tổ chức về tương lai")
def format_cmd(input_file: str, shift_future: bool):
    """Chuẩn hóa dữ liệu raw sang domain schema FlashTicket."""
    config = PipelineConfig()
    config.ensure_directories()
    
    if input_file:
        raw_data = json.loads(Path(input_file).read_text(encoding="utf-8"))
        from tools.ticket_data_pipeline.models.raw_models import RawEventDetail
        raw_events = [RawEventDetail(**item) for item in raw_data]
    else:
        # Tự động lấy sự kiện mẫu từ Scraper
        scraper = TicketboxScraper()
        raw_events = scraper.fetch_events(limit=5)
        
    normalizer = DataNormalizer(config)
    bundle = normalizer.normalize_bundle(raw_events, shift_future=shift_future)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    out_file = config.processed_storage_dir / f"bundle_{timestamp}.json"
    out_file.write_text(bundle.model_dump_json(indent=2), encoding="utf-8")
    click.echo(f"[+] Đã chuẩn hóa {len(bundle.events)} sự kiện. Lưu tại: {out_file}")

@cli.command("generate-orders")
@click.option("--orders-per-event", default=5, type=int, help="Số lượng đơn hàng mẫu sinh cho mỗi sự kiện")
def generate_orders_cmd(orders_per_event: int):
    """Sinh dữ liệu đơn hàng và vé mẫu."""
    click.echo(f"[*] Sinh {orders_per_event} đơn hàng cho các sự kiện mẫu...")

@cli.command("export-sql")
@click.option("--output", type=click.Path(), help="Đường dẫn file sql xuất ra")
def export_sql_cmd(output: str):
    """Xuất file script SQL sẵn sàng nạp vào server 119."""
    click.echo("[*] Đang xuất file SQL...")

@cli.command("import-119")
@click.option("--file", "sql_file", required=True, type=click.Path(exists=True), help="Đường dẫn tệp SQL cần nạp")
@click.option("--apply", is_flag=True, default=False, help="Thực thi thật sự vào server 119 qua SSH")
def import_cmd(sql_file: str, apply: bool):
    """Nạp file script SQL vào server 119."""
    config = PipelineConfig()
    if apply:
        click.echo(f"[*] Đang thực thi nạp {sql_file} lên server 119 ({config.ssh_host})...")
        runner = SshRunner(config.ssh_host)
        code, out = runner.execute_remote_sql(Path(sql_file))
        if code == 0:
            click.echo(f"[SUCCESS] Import hoàn tất:\n{out}")
        else:
            click.echo(f"[FAILED] Lỗi import (code={code}):\n{out}")
    else:
        click.echo("[INFO] Chế độ dry-run: Thêm --apply để thực thi trực tiếp.")

@cli.command("run-all")
@click.option("--sources", default="ticketbox", help="Nguồn crawl (ticketbox, ticketgo, all)")
@click.option("--limit", default=5, type=int, help="Số lượng sự kiện cần crawl")
@click.option("--orders-per-event", default=5, type=int, help="Số lượng đơn hàng mẫu sinh cho mỗi sự kiện")
@click.option("--apply", is_flag=True, default=False, help="Thực thi nạp thẳng vào server 119 qua SSH")
def run_all(sources: str, limit: int, orders_per_event: int, apply: bool):
    """Chạy toàn bộ pipeline từ A đến Z."""
    config = PipelineConfig()
    config.ensure_directories()
    click.echo(f"[*] Bắt đầu crawl từ nguồn {sources} (limit={limit})...")

    # 1. Crawl
    raw_events = []
    src_lower = sources.lower()
    if "ticketbox" in src_lower or src_lower == "all":
        raw_events.extend(TicketboxScraper().fetch_events(limit=limit))
    if "ticketgo" in src_lower or src_lower == "all":
        raw_events.extend(TicketgoScraper().fetch_events(limit=limit))
    if "megatix" in src_lower or src_lower == "all":
        raw_events.extend(MegatixScraper().fetch_events(limit=limit))
    if "actiup" in src_lower or src_lower == "all":
        raw_events.extend(ActiupScraper().fetch_events(limit=limit))
    if "eventbrite" in src_lower or src_lower == "all":
        raw_events.extend(EventbriteScraper().fetch_events(limit=limit))

    if not raw_events:
        raw_events = TicketboxScraper().fetch_events(limit=limit)
    click.echo(f"[+] Đã crawl tổng cộng {len(raw_events)} sự kiện từ các nền tảng.")

    # 2. Normalize
    click.echo("[*] Chuẩn hóa dữ liệu sang FlashTicket schema...")
    normalizer = DataNormalizer(config)
    bundle = normalizer.normalize_bundle(raw_events, shift_future=True)

    # 3. Generate Orders
    click.echo(f"[*] Sinh dữ liệu đơn hàng giả lập ({orders_per_event} đơn/sự kiện)...")
    generator = OrderGenerator()
    all_orders, all_tickets, all_payments = [], [], []
    for ev in bundle.events:
        o, t, p = generator.generate_orders_for_event(ev, count=orders_per_event)
        all_orders.extend(o)
        all_tickets.extend(t)
        all_payments.extend(p)

    # 4. Export SQL
    click.echo("[*] Xuất script SQL chuẩn hóa...")
    builder = SqlBuilder()
    sql_content = builder.build_sql_script(bundle, all_orders, all_tickets, all_payments)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    sql_file = config.sql_storage_dir / f"import_seed119_{timestamp}.sql"
    sql_file.write_text(sql_content, encoding="utf-8")
    click.echo(f"[+] File SQL đã sẵn sàng: {sql_file}")

    # 5. Import nếu có cờ --apply
    if apply:
        click.echo("[*] Đang thực thi nạp dữ liệu lên server 119...")
        runner = SshRunner(config.ssh_host)
        code, out = runner.execute_remote_sql(sql_file)
        if code == 0:
            click.echo("[SUCCESS] Import server 119 hoàn tất!")
            verifier = DataVerifier(config.ssh_host)
            report = verifier.verify_import([str(e.id) for e in bundle.events])
            click.echo(f"Kết quả đối soát:\n{report.get('output')}")
        else:
            click.echo(f"[FAILED] Lỗi khi nạp server 119 (code={code}):\n{out}")
    else:
        click.echo("[INFO] Chế độ dry-run hoàn tất. Dùng cờ --apply để nạp vào DB server 119.")

if __name__ == "__main__":
    cli()
