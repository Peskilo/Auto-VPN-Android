# Parser and probe hardening (WP-8i)

Audit of four backlog items at HEAD fe57593. Rule: no change that alters `stableNodeId`
(hash of the tagless outbound) without the owner's agreement: a changed id drops the
node's favorite flag, custom name and selection after the next refresh. Synthetic forms
only; raw output of the audit run: `.pi/wp8i/explore-clash-forms.txt` (local).

## A. Clash `ws-opts.host` as SNI fallback — confirmed, NO-GO

- `clashServerName` reads `ws-opts.headers.Host`, `headers.host`, then `ws-opts.host`.
  `clashTransport("ws")` reads only the two header spellings. For a node that has only
  `ws-opts.host` (no servername/sni) the SNI becomes that host while the ws `Host`
  header stays the server address: the two disagree.
- sing-box `v2raywebsocket` takes the Host only from `headers["Host"]`; mihomo's `ws-opts`
  has `path`, `headers`, `max-early-data`, `early-data-header-name`, no `host`. So the key
  is a non-standard alias; URI (`host=` is the ws Host and the SNI fallback, Xray
  convention) and sing-box JSON (no fallback at all) are already consistent.
- Fix would be: drop the `ws-opts.host` token for `ws` (matches mihomo; `httpupgrade`
  keeps it because its transport reads it too). Effect: SNI for that form changes
  host -> core default (server), so the id changes. Affects only non-standard nodes.
- Decision: NO-GO until the owner accepts the one-off id change for that form
  (alternative: also send the alias as the ws Host header; also changes the id).
  Tests: pin the current ids and outbound for standard and alias forms.

## B. Blank h2 / http host and path — NO-GO

- URI: `commaList` trims and drops blanks, blank path is omitted. Correct, pinned.
- Clash `network: h2`: `host: ""` gives `[""]`, `host: ["", a]` keeps the blank
  element, `path: ""` gives `""`. (`network: http` is skipped since WP-2.)
- sing-box (`v2rayhttp`): path without leading `/` gets one, so `""` equals `/`: no
  behavior gain from cleaning. A blank host list element makes Go use the dial address as
  authority, an omitted list makes it `www.example.com`: unclear which one the provider
  meant, and either cleaning changes the id. Decision: NO-GO, characterization tests only.

## C. `Map.str` strictness — confirmed safe, tests only

- `str` returns String, Number and Boolean text; List and Map give null (before 132182d
  they were stringified into garbage). Forms checked: `port: "443"`, `443.0`, `tls: true |
  "true" | "True" | yes | on`, `alterId: "0" | 0 | ""`, `skip-cert-verify: "true" | "1" |
  yes`, numeric `name`, `password`, `short-id`, `obfs-password`, `alpn` list or string: all
  parse as before. Nothing valid is dropped by the strictness itself.
- Not valid for mihomo, therefore left alone: `tls: 1` (silently no TLS, WP-8e then blocks
  it), `skip-cert-verify: 1` or `"yes"` (stays verified: secure default; accepting would
  flip security and the id).
- New finding E (NO-GO, needs the owner): SnakeYAML resolves unquoted scalars before we
  see them: `short-id: 01234567` -> `342391`, `password: 0123` -> `83`, `1e3` ->
  `1000.0`, `2024-01-01` -> node skipped. mihomo keeps the text. Fixing keeps text for int,
  float and timestamp scalars; it changes ids of the (already non-working) nodes.
- Tests: golden ids and values for the checked forms.

## D. LatencyProbe permit held by a hung DNS lookup — GO

- Today the worker keeps its permit until the lookup returns (WP-3b: bounded threads). 8
  hung lookups (dead resolver, 5-30 s) stall every later measurement.
- Change: the permit covers only the attempt's own deadline and is released in `finally`.
  A separate budget of 16 threads (8 active + up to 8 lingering past the deadline) is taken
  before the worker starts and released when it ends; when exhausted the probe returns
  null at once instead of queueing. Thread count stays bounded (<= 16), no new threads
  per call, TCP/protect/cancellation behavior unchanged. Dispatcher parallelism raised
  from 8 to 16 to match.
- Tests: queue is not stalled by hung lookups (red now), budget exhaustion fails fast, a
  released permit is reusable, workers are bounded.
