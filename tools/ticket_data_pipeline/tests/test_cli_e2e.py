from click.testing import CliRunner
from tools.ticket_data_pipeline.cli import cli

def test_cli_help_menu():
    runner = CliRunner()
    result = runner.invoke(cli, ["--help"])
    assert result.exit_code == 0
    assert "crawl" in result.output
    assert "format" in result.output
    assert "run-all" in result.output

def test_cli_run_all_dry_run():
    runner = CliRunner()
    result = runner.invoke(cli, ["run-all", "--sources", "ticketbox", "--limit", "2", "--orders-per-event", "2"])
    assert result.exit_code == 0
    assert "Bắt đầu crawl" in result.output
    assert "Chuẩn hóa dữ liệu" in result.output
    assert "File SQL đã sẵn sàng" in result.output
    assert "dry-run hoàn tất" in result.output
