       IDENTIFICATION DIVISION.
       PROGRAM-ID. FACTORIAL.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-N           PIC 9(2)  VALUE 10.
       01 WS-I           PIC 9(2)  VALUE 1.
       01 WS-RESULT      PIC 9(18) VALUE 1.
       01 WS-RESULT-OUT  PIC Z(17)9.
       PROCEDURE DIVISION.
      * COBOL paragraphs are not recursive by default, so loop instead.
           PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > WS-N
               MULTIPLY WS-I BY WS-RESULT
           END-PERFORM.
           MOVE WS-RESULT TO WS-RESULT-OUT.
           DISPLAY WS-N "! = " FUNCTION TRIM(WS-RESULT-OUT).
           STOP RUN.
