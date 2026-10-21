\set ON_ERROR_STOP on
-- Local inspection databases: apply after canonical B12 DDL and reader grants.
-- Safe to rerun. ALTER TABLE retains data, foreign keys, indexes and privileges.
BEGIN;
SET LOCAL lock_timeout = '5s';
DO $prefix$
DECLARE
  schema_name text;
  table_name text;
  function_definition text;
BEGIN
  IF current_database() NOT IN ('booking_db', 'event_db', 'payment_db', 'ticket_db') THEN
    RAISE EXCEPTION 'Expected a local business database';
  END IF;
  schema_name := replace(current_database(), '_db', '_schema');

  -- Check all target names before renaming anything.
  FOR table_name IN
    SELECT tablename FROM pg_tables
    WHERE schemaname = schema_name AND left(tablename, 4) <> 'tbl_'
  LOOP
    IF to_regclass(format('%I.%I', schema_name, 'tbl_' || table_name)) IS NOT NULL THEN
      RAISE EXCEPTION 'Target already exists: %.tbl_%', schema_name, table_name;
    END IF;
  END LOOP;

  FOR table_name IN
    SELECT tablename FROM pg_tables
    WHERE schemaname = schema_name AND left(tablename, 4) <> 'tbl_'
    ORDER BY tablename
  LOOP
    EXECUTE format('ALTER TABLE %I.%I RENAME TO %I', schema_name, table_name, 'tbl_' || table_name);
  END LOOP;

  -- PL/pgSQL source contains a textual table reference, unlike FK dependencies.
  IF current_database() = 'event_db' THEN
    function_definition := pg_get_functiondef('event_schema.guard_draft_child_delete()'::regprocedure);
    IF strpos(function_definition, 'FROM events WHERE') > 0 THEN
      EXECUTE replace(function_definition, 'FROM events WHERE', 'FROM tbl_events WHERE');
    ELSIF strpos(function_definition, 'FROM tbl_events WHERE') = 0 THEN
      RAISE EXCEPTION 'Unexpected Event delete guard; review its table reference';
    END IF;
  END IF;

  IF NOT EXISTS (SELECT FROM pg_tables WHERE schemaname = schema_name)
     OR EXISTS (SELECT FROM pg_tables WHERE schemaname = schema_name AND left(tablename, 4) <> 'tbl_') THEN
    RAISE EXCEPTION 'Table prefix verification failed';
  END IF;
END $prefix$;
COMMIT;
