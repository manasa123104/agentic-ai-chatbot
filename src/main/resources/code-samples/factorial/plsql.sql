SET SERVEROUTPUT ON;

DECLARE
    n NUMBER := 10;

    FUNCTION factorial(p_n NUMBER) RETURN NUMBER IS
    BEGIN
        IF p_n <= 1 THEN
            RETURN 1;
        END IF;
        RETURN p_n * factorial(p_n - 1);
    END factorial;
BEGIN
    DBMS_OUTPUT.PUT_LINE(n || '! = ' || factorial(n));
END;
/
