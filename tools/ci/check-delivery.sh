#!/usr/bin/env bash
# Checks that a deployed site is delivered the way nginx.conf and the Caddyfile intend: what is cached and
# for how long, and what is compressed. Run after every deploy (deploy.yml); by hand:
#
#   tools/ci/check-delivery.sh https://chess.example.com
#
# Exits non-zero on the first broken promise. HTTP/3 is reported, not required (it needs UDP 443 open).
set -euo pipefail

site="${1:?usage: check-delivery.sh https://your-site}"
site="${site%/}"
failures=0

header() { # url, header name, [extra curl args...]: the header's value, lowercased, on its own
  local url=$1 name=$2; shift 2
  curl -sS -o /dev/null -D - "$@" "$url" | tr -d '\r' | awk -v h="$(echo "$name" | tr 'A-Z' 'a-z')" \
    'tolower($0) ~ "^"h":" {sub(/^[^:]*:[ ]*/, ""); print tolower($0)}' | tail -1
}
status() { curl -sS -o /dev/null -w '%{http_code}' "$@"; }
check() { # description, actual, expected (an extended regex)
  if [[ "$2" =~ $3 ]]; then echo "ok    $1: $2"; else echo "FAIL  $1: got '$2', want /$3/"; failures=$((failures + 1)); fi
}

# The page: always checked with the server, so a deploy shows at once
check "index.html status" "$(status "$site/")" '^200$'
check "index.html Cache-Control" "$(header "$site/" Cache-Control)" 'no-cache'

# A hashed bundle: cached for a year, sent compressed
main=$(curl -sS "$site/" | grep -oE 'main-[A-Za-z0-9]+\.js' | head -1)
check "main bundle named in index.html" "${main:-none}" '^main-'
if [[ -n "$main" ]]; then
  check "$main Cache-Control" "$(header "$site/$main" Cache-Control)" 'max-age=31536000.*immutable'
  check "$main Content-Encoding" "$(header "$site/$main" Content-Encoding -H 'Accept-Encoding: gzip')" '^(gzip|zstd|br)$'
fi

# An asset asked for with its version (assetUrl()): cached for a year; without one: checked every time
check "versioned asset Cache-Control" "$(header "$site/assets/icon.svg?v=deploycheck" Cache-Control)" 'max-age=31536000.*immutable'
check "unversioned asset Cache-Control" "$(header "$site/assets/icon.svg" Cache-Control)" 'no-cache'
check "missing asset status" "$(status "$site/assets/no-such-file.webp")" '^404$'

# The API's JSON, compressed by Caddy. The showcase may still be searching right after a start (503): retry
api=""
for _ in 1 2 3 4 5 6; do
  code=$(status "$site/api/showcase")
  if [[ "$code" == 200 ]]; then
    api=$(header "$site/api/showcase" Content-Encoding -H 'Accept-Encoding: zstd, gzip')
    break
  fi
  sleep 10
done
check "API JSON Content-Encoding" "${api:-no 200 from /api/showcase}" '^(zstd|gzip)$'

alt=$(header "$site/" Alt-Svc)
if [[ "$alt" == *h3* ]]; then echo "ok    HTTP/3 offered: $alt"; else echo "note  HTTP/3 not offered (Alt-Svc: '${alt:-none}')"; fi

if (( failures )); then echo "$failures check(s) failed"; exit 1; fi
echo "Delivery checks passed for $site"
