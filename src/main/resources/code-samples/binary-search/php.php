<?php

function binarySearch(array $values, int $target): int
{
    $low = 0;
    $high = count($values) - 1;
    while ($low <= $high) {
        $mid = intdiv($low + $high, 2);
        if ($values[$mid] === $target) {
            return $mid;
        } elseif ($values[$mid] < $target) {
            $low = $mid + 1;
        } else {
            $high = $mid - 1;
        }
    }
    return -1;
}

$values = [2, 5, 8, 12, 16, 23, 38, 56, 72, 91];
$target = 23;
$index = binarySearch($values, $target);
if ($index >= 0) {
    echo "Found {$target} at index {$index}" . PHP_EOL;
} else {
    echo "{$target} not found" . PHP_EOL;
}
