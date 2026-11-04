import json
from typing import List
from tools.ticket_data_pipeline.models.flashticket_models import FlashTicketBundle
from tools.ticket_data_pipeline.models.simulated_models import OrderSim, TicketSim, PaymentAttemptSim
from tools.ticket_data_pipeline.transformers.snapshot_builder import SnapshotBuilder

class SqlBuilder:
    def __init__(self):
        self.snapshot_builder = SnapshotBuilder()

    def escape_str(self, val: str) -> str:
        if val is None:
            return "NULL"
        return "'" + str(val).replace("'", "''") + "'"

    def build_sql_script(
        self,
        bundle: FlashTicketBundle,
        orders: List[OrderSim] = None,
        tickets: List[TicketSim] = None,
        payments: List[PaymentAttemptSim] = None
    ) -> str:
        orders = orders or []
        tickets = tickets or []
        payments = payments or []

        lines = [
            "\\set ON_ERROR_STOP on",
            ""
        ]

        # 1. EVENT_DB
        lines.extend([
            "\\connect event_db",
            "BEGIN; SET LOCAL ROLE event_owner; SET LOCAL search_path=event_schema,pg_catalog; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='30s';"
        ])
        for c in bundle.categories:
            lines.append(f"INSERT INTO tbl_categories(id,name) VALUES ('{c.id}',{self.escape_str(c.name)}) ON CONFLICT (id) DO NOTHING;")
        for v in bundle.venues:
            lines.append(f"INSERT INTO tbl_venues(id,name,address) VALUES ('{v.id}',{self.escape_str(v.name)},{self.escape_str(v.address)}) ON CONFLICT (id) DO NOTHING;")
        for e in bundle.events:
            lines.append(
                f"INSERT INTO tbl_events(id,organizer_subject,category_id,venue_id,title,description,sales_mode,status,starts_at,ends_at,sale_starts_at,sale_ends_at,checkin_starts_at,checkin_ends_at,purchase_limit,fee_rate,approved_by,approved_at) "
                f"VALUES ('{e.id}','{e.organizer_subject}','{e.category_id}','{e.venue_id}',{self.escape_str(e.title)},{self.escape_str(e.description)},'{e.sales_mode}','{e.status}','{e.starts_at.isoformat()}','{e.ends_at.isoformat()}','{e.sale_starts_at.isoformat()}','{e.sale_ends_at.isoformat()}','{e.checkin_starts_at.isoformat()}','{e.checkin_ends_at.isoformat()}',{e.purchase_limit},{e.fee_rate},'{e.approved_by}','{e.approved_at.isoformat()}') ON CONFLICT (id) DO NOTHING;"
            )
            for t in e.ticket_types:
                lines.append(
                    f"INSERT INTO tbl_ticket_types(id,event_id,name,sales_mode,unit_price,currency,configured_quantity,color_code) "
                    f"VALUES ('{t.id}','{e.id}',{self.escape_str(t.name)},'{t.sales_mode}',{t.unit_price},'{t.currency}',{t.configured_quantity},'{t.color_code}') ON CONFLICT (id) DO NOTHING;"
                )
            if e.banner_url:
                lines.append(
                    f"INSERT INTO tbl_event_images(id,event_id,object_reference,display_order) "
                    f"VALUES (gen_random_uuid(),'{e.id}',{self.escape_str(e.banner_url)},0) ON CONFLICT (event_id, display_order) DO NOTHING;"
                )
        lines.append("COMMIT;\n")

        # 2. BOOKING_DB
        lines.extend([
            "\\connect booking_db",
            "BEGIN; SET LOCAL ROLE booking_owner; SET LOCAL search_path=booking_schema,pg_catalog; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='30s';"
        ])
        venues_lookup = {v.id: v for v in bundle.venues}
        for e in bundle.events:
            venue = venues_lookup.get(e.venue_id)
            snap_json = json.dumps(self.snapshot_builder.build_booking_snapshot(e, venue), ensure_ascii=False)
            lines.append(
                f"INSERT INTO tbl_event_sales_snapshots(event_id,organizer_subject,sales_mode,event_status,sale_starts_at,sale_ends_at,purchase_limit,configuration_snapshot,source_config_version,received_at) "
                f"VALUES ('{e.id}','{e.organizer_subject}','{e.sales_mode}','{e.status}','{e.sale_starts_at.isoformat()}','{e.sale_ends_at.isoformat()}',{e.purchase_limit},{self.escape_str(snap_json)}::jsonb,1,now()) ON CONFLICT (event_id) DO NOTHING;"
            )
            for t in e.ticket_types:
                lines.append(
                    f"INSERT INTO tbl_ticket_type_inventory(ticket_type_id,event_id,configured_capacity,source_config_version) "
                    f"VALUES ('{t.id}','{e.id}',{t.configured_quantity},1) ON CONFLICT (ticket_type_id) DO NOTHING;"
                )
        for o in orders:
            charge_clause = f"'{o.accepted_charge_id}'" if o.accepted_charge_id else "NULL"
            accepted_clause = f"'{o.accepted_at.isoformat()}'" if o.accepted_at else "NULL"
            lines.append(
                f"INSERT INTO tbl_orders(id,event_id,buyer_subject,sales_mode,lifecycle_state,customer_email,subtotal,discount,total,currency,expires_at,accepted_charge_id,accepted_at,source_config_version) "
                f"VALUES ('{o.id}','{o.event_id}','{o.buyer_subject}','QUANTITY','{o.lifecycle_state}',{self.escape_str(o.customer_email)},{o.subtotal},{o.discount},{o.total},'{o.currency}','{o.expires_at.isoformat()}',{charge_clause},{accepted_clause},1) ON CONFLICT (id) DO NOTHING;"
            )
            for itm in o.items:
                sel_json = json.dumps({"schemaVersion": 1, "quantity": itm.quantity, "ticketTypeId": str(itm.ticket_type_id)})
                lines.append(
                    f"INSERT INTO tbl_order_items(id,order_id,event_id,ticket_type_id,sales_mode,ticket_type_name,quantity,unit_price,line_subtotal,currency,selection_snapshot) "
                    f"VALUES ('{itm.id}','{itm.order_id}','{itm.event_id}','{itm.ticket_type_id}','QUANTITY',{self.escape_str(itm.ticket_type_name)},{itm.quantity},{itm.unit_price},{itm.line_subtotal},'{itm.currency}',{self.escape_str(sel_json)}::jsonb) ON CONFLICT (id) DO NOTHING;"
                )
        lines.append("COMMIT;\n")

        # 3. PAYMENT_DB
        lines.extend([
            "\\connect payment_db",
            "BEGIN; SET LOCAL ROLE payment_owner; SET LOCAL search_path=payment_schema,pg_catalog; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='30s';"
        ])
        for e in bundle.events:
            lines.append(
                f"INSERT INTO tbl_event_finance_snapshots(event_id,organizer_subject,event_ends_at,fee_rate,source_fee_version,received_at) "
                f"VALUES ('{e.id}','{e.organizer_subject}','{e.ends_at.isoformat()}',{e.fee_rate},1,now()) ON CONFLICT (event_id) DO NOTHING;"
            )
        lines.append("COMMIT;\n")

        # 4. TICKET_DB
        lines.extend([
            "\\connect ticket_db",
            "BEGIN; SET LOCAL ROLE ticket_owner; SET LOCAL search_path=ticket_schema,pg_catalog; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='30s';"
        ])
        for e in bundle.events:
            lines.append(
                f"INSERT INTO tbl_event_access_snapshots(event_id,organizer_subject,event_status,event_starts_at,event_ends_at,source_config_version,received_at) "
                f"VALUES ('{e.id}','{e.organizer_subject}','{e.status}','{e.starts_at.isoformat()}','{e.ends_at.isoformat()}',1,now()) ON CONFLICT (event_id) DO NOTHING;"
            )
        for tk in tickets:
            lines.append(
                f"INSERT INTO tbl_tickets(id,event_id,ticket_type_id,order_id,buyer_subject,serial_number,checkin_code,status) "
                f"VALUES ('{tk.id}','{tk.event_id}','{tk.ticket_type_id}','{tk.order_id}','{tk.buyer_subject}','{tk.serial_number}','{tk.checkin_code}','{tk.status}') ON CONFLICT (id) DO NOTHING;"
            )
        lines.append("COMMIT;\n")

        return "\n".join(lines)

