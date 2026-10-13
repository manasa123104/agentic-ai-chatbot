def factorial(n)
  return 1 if n <= 1

  n * factorial(n - 1)
end

n = 10
puts "#{n}! = #{factorial(n)}"
