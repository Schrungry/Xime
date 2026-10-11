return function(input, segment, env)
  yield(Candidate("fixture", segment.start, segment._end, "first:" .. input, ""))
  yield(Candidate("fixture", segment.start, segment._end, "second:" .. input, ""))
end
