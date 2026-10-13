def binary_search(values, target)
  low = 0
  high = values.length - 1
  while low <= high
    mid = (low + high) / 2
    if values[mid] == target
      return mid
    elsif values[mid] < target
      low = mid + 1
    else
      high = mid - 1
    end
  end
  -1
end

values = [2, 5, 8, 12, 16, 23, 38, 56, 72, 91]
target = 23
index = binary_search(values, target)
if index >= 0
  puts "Found #{target} at index #{index}"
else
  puts "#{target} not found"
end
