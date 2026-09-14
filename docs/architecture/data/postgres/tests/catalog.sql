\set ON_ERROR_STOP on
-- A new psql connection runs this file once in each business database.
BEGIN;
DO $body$
DECLARE service text := replace(current_database(), '_db', '');
        service_schema text; app_role text; owner_role text; migrator_role text;
        target_db text; actor text; relation record; privilege_name text; allowed boolean;
BEGIN
  IF service NOT IN ('event','booking','payment','ticket') THEN RAISE EXCEPTION 'Wrong database for catalog test'; END IF;
  service_schema := service || '_schema'; app_role := service || '_app';
  owner_role := service || '_owner'; migrator_role := service || '_migrator';
  IF (SELECT rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls FROM pg_roles WHERE rolname = owner_role)
    OR (SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR rolinherit FROM pg_roles WHERE rolname = app_role)
    OR pg_has_role(app_role, owner_role, 'MEMBER') THEN RAISE EXCEPTION 'Runtime/owner privilege boundary failed'; END IF;
  IF NOT pg_has_role(migrator_role, owner_role, 'SET') OR pg_has_role(migrator_role, owner_role, 'USAGE') THEN RAISE EXCEPTION 'Migrator must explicitly SET ROLE'; END IF;
  FOREACH actor IN ARRAY ARRAY['event_app','booking_app','payment_app','ticket_app','keycloak_app','rca_observer'] LOOP
    FOREACH target_db IN ARRAY ARRAY['event_db','booking_db','payment_db','ticket_db','keycloak_db','postgres','template1'] LOOP
      allowed := actor <> 'rca_observer' AND target_db = replace(actor, '_app', '_db');
      IF has_database_privilege(actor, target_db, 'CONNECT') IS DISTINCT FROM allowed THEN
        RAISE EXCEPTION 'CONNECT matrix failed for % / %', actor, target_db;
      END IF;
    END LOOP;
  END LOOP;
  IF has_schema_privilege(app_role, service_schema, 'CREATE') OR has_schema_privilege(app_role, 'public', 'CREATE')
     OR has_database_privilege(app_role, current_database(), 'TEMP') OR has_database_privilege(app_role, current_database(), 'CREATE') THEN
    RAISE EXCEPTION 'Runtime unexpectedly has DDL/temp privilege';
  END IF;
  FOR relation IN SELECT c.oid, c.relowner, c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = service_schema AND c.relkind = 'r' LOOP
    IF relation.relowner <> (SELECT oid FROM pg_roles WHERE rolname = owner_role) THEN RAISE EXCEPTION 'Wrong table owner: %', relation.relname; END IF;
    FOREACH privilege_name IN ARRAY ARRAY['SELECT','INSERT','UPDATE','DELETE','TRUNCATE','REFERENCES','TRIGGER'] LOOP
      allowed := privilege_name IN ('SELECT','INSERT','UPDATE');
      allowed := allowed OR (service = 'event' AND privilege_name = 'DELETE' AND relation.relname IN ('event_images','event_layouts','event_sectors','event_seats','ticket_types','promotions'));
      IF obj_description(relation.oid, 'pg_class') IS NULL THEN RAISE EXCEPTION 'Missing table rationale: %', relation.relname; END IF;
      IF has_table_privilege(app_role, relation.oid, privilege_name) IS DISTINCT FROM allowed THEN
        RAISE EXCEPTION 'Table privilege mismatch: % / %', relation.relname, privilege_name;
      END IF;
    END LOOP;
  END LOOP;
  IF EXISTS (SELECT FROM pg_constraint c JOIN pg_class source ON source.oid = c.conrelid
             JOIN pg_class target ON target.oid = c.confrelid
             JOIN pg_namespace ns ON ns.oid = source.relnamespace
             WHERE c.contype = 'f' AND ns.nspname = service_schema AND source.relnamespace <> target.relnamespace)
    OR EXISTS (SELECT FROM pg_foreign_table)
    OR EXISTS (SELECT FROM pg_extension WHERE extname IN ('dblink','postgres_fdw')) THEN
    RAISE EXCEPTION 'Cross-owner access mechanism found';
  END IF;
  RAISE NOTICE 'PASS: catalog owners/grants/connect/local-FK for %', service;

  -- Create as the actual owner, then prove future objects fail closed. Outer ROLLBACK removes probes.
  EXECUTE format('SET LOCAL ROLE %I', owner_role);
  EXECUTE format('CREATE TABLE %I.b12_privilege_probe (id integer)', service_schema);
  EXECUTE format('CREATE FUNCTION %I.b12_function_probe() RETURNS integer LANGUAGE sql AS %L', service_schema, 'SELECT 1');
  IF has_table_privilege(app_role, format('%I.b12_privilege_probe', service_schema), 'SELECT')
    OR has_function_privilege(app_role, format('%I.b12_function_probe()', service_schema), 'EXECUTE') THEN
    RAISE EXCEPTION 'Default privileges did not fail closed';
  END IF;
  EXECUTE 'RESET ROLE';
  RAISE NOTICE 'PASS: future table/function defaults fail closed for %', service;
END $body$;
ROLLBACK;
