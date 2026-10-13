       IDENTIFICATION DIVISION.
       PROGRAM-ID. FIZZBUZZ.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-I          PIC 9(3) VALUE 1.
       01 WS-I-OUT      PIC Z(2)9.
       PROCEDURE DIVISION.
           PERFORM VARYING WS-I FROM 1 BY 1 UNTIL WS-I > 15
               EVALUATE TRUE
                   WHEN FUNCTION MOD(WS-I, 15) = 0
                       DISPLAY "FizzBuzz"
                   WHEN FUNCTION MOD(WS-I, 3) = 0
                       DISPLAY "Fizz"
                   WHEN FUNCTION MOD(WS-I, 5) = 0
                       DISPLAY "Buzz"
                   WHEN OTHER
                       MOVE WS-I TO WS-I-OUT
                       DISPLAY FUNCTION TRIM(WS-I-OUT)
               END-EVALUATE
           END-PERFORM.
           STOP RUN.
