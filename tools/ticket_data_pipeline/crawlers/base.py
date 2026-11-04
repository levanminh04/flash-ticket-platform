from abc import ABC, abstractmethod
from typing import List
from tools.ticket_data_pipeline.models.raw_models import RawEventDetail

class BaseScraper(ABC):
    @abstractmethod
    def fetch_events(self, limit: int = 10) -> List[RawEventDetail]:
        pass

