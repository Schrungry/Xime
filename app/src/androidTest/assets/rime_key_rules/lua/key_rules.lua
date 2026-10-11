-- A small deterministic scheme: tone keys edit the code, other digits select;
-- uppercase magic codes accept every digit. No Wanxiang dictionary is required.
return function(key, env)
  if key:release() or key:ctrl() or key:alt() or key:shift() then
    return 2
  end
  local digit = key:repr():match("^[0-9]$")
  if not digit then return 2 end
  local ctx = env.engine.context
  if ctx.input:match("^[URN]") then
    ctx:push_input(digit)
    return 1
  end
  if ctx.input ~= "" and digit:match("[7890]") then
    local previous = ctx.input:sub(ctx.caret_pos, ctx.caret_pos)
    if previous:match("[7890]") then ctx:pop_input(1) end
    ctx:push_input(digit)
    return 1
  end
  return 2
end
