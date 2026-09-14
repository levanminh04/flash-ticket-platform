CREATE FUNCTION pg_temp.expect_failure(statement text, expected_state text, test_name text)
RETURNS void LANGUAGE plpgsql AS $body$
DECLARE observed_state text;
BEGIN
  BEGIN
    EXECUTE statement;
  EXCEPTION WHEN OTHERS THEN
    GET STACKED DIAGNOSTICS observed_state = RETURNED_SQLSTATE;
  END;
  IF observed_state IS DISTINCT FROM expected_state THEN
    RAISE EXCEPTION '%: expected SQLSTATE %, observed %', test_name, expected_state, coalesce(observed_state, 'SUCCESS');
  END IF;
  RAISE NOTICE 'PASS: %', test_name;
END $body$;
