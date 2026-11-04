from tools.ticket_data_pipeline.config import PipelineConfig

def test_default_config_values():
    config = PipelineConfig()
    assert config.default_currency == "VND"
    assert config.default_fee_rate == 0.05
    assert config.ssh_host == "ft119"
    assert config.organizer_subject is not None
    assert config.admin_subject is not None

