       IDENTIFICATION DIVISION.
       PROGRAM-ID. BINARY-SEARCH.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-TABLE-DATA   PIC X(30)
              VALUE "002005008012016023038056072091".
       01 WS-TABLE REDEFINES WS-TABLE-DATA.
          05 WS-VALUE     PIC 9(3) OCCURS 10 TIMES.
       01 WS-TARGET       PIC 9(3)  VALUE 23.
       01 WS-LOW          PIC 9(2)  VALUE 1.
       01 WS-HIGH         PIC 9(2)  VALUE 10.
       01 WS-MID          PIC 9(2)  VALUE 0.
       01 WS-INDEX        PIC 9(2)  VALUE 0.
       01 WS-FOUND        PIC X     VALUE "N".
       01 WS-TARGET-OUT   PIC Z(2)9.
       01 WS-INDEX-OUT    PIC Z9.
       PROCEDURE DIVISION.
           PERFORM UNTIL WS-LOW > WS-HIGH OR WS-FOUND = "Y"
               COMPUTE WS-MID = (WS-LOW + WS-HIGH) / 2
               EVALUATE TRUE
                   WHEN WS-VALUE(WS-MID) = WS-TARGET
                       MOVE "Y" TO WS-FOUND
                   WHEN WS-VALUE(WS-MID) < WS-TARGET
                       COMPUTE WS-LOW = WS-MID + 1
                   WHEN OTHER
                       COMPUTE WS-HIGH = WS-MID - 1
               END-EVALUATE
           END-PERFORM.
           MOVE WS-TARGET TO WS-TARGET-OUT.
      * COBOL tables start at 1; report a 0-based index like the others.
           IF WS-FOUND = "Y"
               COMPUTE WS-INDEX = WS-MID - 1
               MOVE WS-INDEX TO WS-INDEX-OUT
               DISPLAY "Found " FUNCTION TRIM(WS-TARGET-OUT)
                   " at index " FUNCTION TRIM(WS-INDEX-OUT)
           ELSE
               DISPLAY FUNCTION TRIM(WS-TARGET-OUT) " not found"
           END-IF.
           STOP RUN.
