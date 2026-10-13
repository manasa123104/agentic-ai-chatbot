SET SERVEROUTPUT ON;

DECLARE
    TYPE int_array IS VARRAY(10) OF PLS_INTEGER;
    v_values int_array   := int_array(2, 5, 8, 12, 16, 23, 38, 56, 72, 91);
    v_target PLS_INTEGER := 23;
    v_low    PLS_INTEGER := 1;
    v_high   PLS_INTEGER := v_values.COUNT;
    v_mid    PLS_INTEGER;
    v_index  PLS_INTEGER := -1;
BEGIN
    WHILE v_low <= v_high LOOP
        v_mid := TRUNC((v_low + v_high) / 2);
        IF v_values(v_mid) = v_target THEN
            -- PL/SQL collections start at 1; report a 0-based index like the other samples.
            v_index := v_mid - 1;
            EXIT;
        ELSIF v_values(v_mid) < v_target THEN
            v_low := v_mid + 1;
        ELSE
            v_high := v_mid - 1;
        END IF;
    END LOOP;

    IF v_index >= 0 THEN
        DBMS_OUTPUT.PUT_LINE('Found ' || v_target || ' at index ' || v_index);
    ELSE
        DBMS_OUTPUT.PUT_LINE(v_target || ' not found');
    END IF;
END;
/
