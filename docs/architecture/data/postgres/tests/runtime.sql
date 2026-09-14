\set ON_ERROR_STOP on
-- Invoke as each *_app principal, only on the isolated baseline test instance.
BEGIN;
DO $body$
DECLARE service text := replace(current_database(), '_db', '');
        target_schema text; relation record; statement text; rejected boolean; checked integer := 0;
BEGIN
  IF service NOT IN ('event','booking','payment','ticket') OR current_user <> service || '_app' THEN
    RAISE EXCEPTION 'Runtime test requires the matching app principal';
  END IF;
  target_schema := service || '_schema';
  FOR relation IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                  WHERE n.nspname=target_schema AND c.relkind='r' LOOP
    EXECUTE format('SELECT count(*) FROM %I.%I', target_schema, relation.relname);
    FOREACH statement IN ARRAY ARRAY[
      format('DELETE FROM %I.%I WHERE false', target_schema, relation.relname),
      format('TRUNCATE %I.%I', target_schema, relation.relname),
      format('ALTER TABLE %I.%I ADD COLUMN b12_denied_column integer', target_schema, relation.relname)
    ] LOOP
      IF service = 'event' AND relation.relname IN ('event_images','event_layouts','event_sectors','event_seats','ticket_types','promotions') AND statement LIKE 'DELETE%' THEN
        EXECUTE statement;
        CONTINUE;
      END IF;
      rejected := false;
      BEGIN EXECUTE statement;
      EXCEPTION WHEN insufficient_privilege THEN rejected := true;
      END;
      IF NOT rejected THEN RAISE EXCEPTION 'App unexpectedly allowed a destructive/DDL statement'; END IF;
      checked := checked + 1;
    END LOOP;
  END LOOP;
  FOREACH statement IN ARRAY ARRAY[
    format('CREATE TABLE %I.b12_denied_table (id integer)', target_schema),
    'CREATE TEMP TABLE b12_denied_temp (id integer)',
    format('SET LOCAL ROLE %I', service || '_owner')
  ] LOOP
    rejected := false;
    BEGIN EXECUTE statement;
    EXCEPTION WHEN insufficient_privilege THEN rejected := true;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'App unexpectedly allowed schema/temp/role operation'; END IF;
    checked := checked + 1;
  END LOOP;
  RAISE NOTICE 'PASS: % runtime SELECT and % denied DELETE/TRUNCATE/DDL/SET ROLE operations', service, checked;
END $body$;
ROLLBACK;
