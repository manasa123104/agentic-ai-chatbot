(1..15).each do |i|
  if (i % 15).zero?
    puts "FizzBuzz"
  elsif (i % 3).zero?
    puts "Fizz"
  elsif (i % 5).zero?
    puts "Buzz"
  else
    puts i
  end
end
