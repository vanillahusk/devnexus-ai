wrk.method = "POST"

local auth_token = os.getenv("FAVOR_TOKEN") or os.getenv("TOKEN")
if auth_token ~= nil and auth_token ~= "" then
  auth_token = auth_token:gsub("^%s*[Bb]earer%s+", "")
end
local article_id = os.getenv("COMMENT_ARTICLE_ID") or os.getenv("ARTICLE_ID") or "14"
local parent_id = os.getenv("COMMENT_PARENT_ID")
local top_id = os.getenv("COMMENT_TOP_ID")
local now = tostring(os.time())
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

local function build_body()
  local content = "pressure-comment-" .. now .. "-" .. tostring(math.random(1000000))

  local pid = parent_id
  local tid = top_id
  if pid == nil or pid == "" then
    pid = "0"
  end
  if tid == nil or tid == "" then
    tid = "0"
  end

  return string.format(
      '{"articleId":%s,"commentContent":"%s","parentCommentId":%s,"topCommentId":%s}',
      article_id, content, pid, tid
  )
end

request = function()
  if auth_token ~= nil and auth_token ~= "" then
    wrk.headers["Authorization"] = auth_token
    wrk.headers["x-access-token"] = auth_token
  else
    wrk.headers["Authorization"] = "REPLACE_WITH_YOUR_TOKEN"
    wrk.headers["x-access-token"] = "REPLACE_WITH_YOUR_TOKEN"
  end
  wrk.headers["Content-Type"] = "application/json"
  return wrk.format("POST", "/comment/api/save", nil, build_body())
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
