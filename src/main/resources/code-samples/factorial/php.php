<?php

function factorial(int $n): int
{
    if ($n <= 1) {
        return 1;
    }
    return $n * factorial($n - 1);
}

$n = 10;
echo "{$n}! = " . factorial($n) . PHP_EOL;
