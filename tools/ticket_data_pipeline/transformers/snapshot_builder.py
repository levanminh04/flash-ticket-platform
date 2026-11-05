from tools.ticket_data_pipeline.models.flashticket_models import Event, Venue

class SnapshotBuilder:
    def build_booking_snapshot(self, event: Event, venue: Venue) -> dict:
        ticket_types_json = []
        for t in event.ticket_types:
            ticket_types_json.append({
                "ticketTypeId": str(t.id),
                "name": t.name,
                "unitPrice": str(t.unit_price),
                "currency": t.currency,
                "sectorId": None,
                "configuredQuantity": str(t.configured_quantity),
                "colorCode": t.color_code
            })
        return {
            "schemaVersion": 1,
            "eventId": str(event.id),
            "organizerSubject": event.organizer_subject,
            "sourceConfigVersion": "1",
            "eventStatus": event.status,
            "eventTitle": event.title,
            "eventStartsAt": event.starts_at.isoformat(),
            "eventEndsAt": event.ends_at.isoformat(),
            "venueName": venue.name if venue else "Địa điểm sự kiện",
            "mode": event.sales_mode,
            "saleStartsAt": event.sale_starts_at.isoformat(),
            "saleEndsAt": event.sale_ends_at.isoformat(),
            "purchaseLimit": event.purchase_limit,
            "ticketTypes": ticket_types_json,
            "sectors": [],
            "seats": [],
            "promotions": []
        }

    def build_finance_snapshot(self, event: Event) -> dict:
        return {
            "eventId": str(event.id),
            "organizerSubject": event.organizer_subject,
            "eventEndsAt": event.ends_at.isoformat(),
            "feeRate": event.fee_rate,
            "sourceFeeVersion": 1
        }

    def build_access_snapshot(self, event: Event) -> dict:
        return {
            "eventId": str(event.id),
            "organizerSubject": event.organizer_subject,
            "eventStatus": event.status,
            "eventStartsAt": event.starts_at.isoformat(),
            "eventEndsAt": event.ends_at.isoformat(),
            "sourceConfigVersion": 1
        }

