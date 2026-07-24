wrk.method = "GET"

local article_ids = {1, 2, 3, 4, 5}
local operate_types = {2, 4}
local function trim_token(token)
  if token == nil then
    return nil
  end
  token = token:gsub("^%s+", ""):gsub("%s+$", "")
  token = token:gsub("^%s*[Bb]earer%s+", "")
  if token == "" then
    return nil
  end
  return token
end

local token_list = {}
local tokens_env = os.getenv("FAVOR_TOKENS")
if tokens_env ~= nil and tokens_env ~= "" then
  for token in string.gmatch(tokens_env, "([^,]+)") do
    local cleaned = trim_token(token)
    if cleaned ~= nil then
      table.insert(token_list, cleaned)
    end
  end
end

if #token_list == 0 then
  local single_token = trim_token(os.getenv("FAVOR_TOKEN"))
  if single_token ~= nil then
    table.insert(token_list, single_token)
  end
end

local fixed_article_id = os.getenv("FAVOR_ARTICLE_ID")
local request_delay_ms = tonumber(os.getenv("REQUEST_DELAY_MS") or "0")

math.randomseed(os.time())

business_success = 0
business_error = 0
http_error = 0
local threads = {}

setup = function(thread)
  table.insert(threads, thread)
end

delay = function()
  return request_delay_ms
end

request = function()
  local aid = fixed_article_id or article_ids[math.random(#article_ids)]
  local op = operate_types[math.random(#operate_types)]
  local path = "/article/api/favor?articleId=" .. aid .. "&type=" .. op

  if #token_list > 0 then
    local selected_token = token_list[math.random(#token_list)]
    wrk.headers["Authorization"] = selected_token
    wrk.headers["x-access-token"] = selected_token
  else
    wrk.headers["Authorization"] = "REPLACE_WITH_YOUR_TOKEN"
    wrk.headers["x-access-token"] = "REPLACE_WITH_YOUR_TOKEN"
  end
  return wrk.format(wrk.method, path)
end

response = function(status, headers, body)
  if status < 200 or status >= 300 then
    http_error = http_error + 1
  elseif body ~= nil and body:find('"code"%s*:%s*0') then
    business_success = business_success + 1
  else
    business_error = business_error + 1
  end
end

done = function(summary, latency, requests)
  local success_total = 0
  local business_error_total = 0
  local http_error_total = 0
  for _, thread in ipairs(threads) do
    success_total = success_total + (thread:get("business_success") or 0)
    business_error_total = business_error_total + (thread:get("business_error") or 0)
    http_error_total = http_error_total + (thread:get("http_error") or 0)
  end
  io.write(string.format(
      "Business results: success=%d business_error=%d http_error=%d\n",
      success_total, business_error_total, http_error_total))
  io.write(string.format(
      "Exact latency: p95=%.2fms p99=%.2fms\n",
      latency:percentile(95.0) / 1000.0,
      latency:percentile(99.0) / 1000.0))
end
