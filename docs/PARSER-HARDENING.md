# Parser and probe hardening (WP-8i)
Audit at fe57593. Identity hashes the tagless outbound: changing it on refresh can lose
favorites, custom names and selection. No such correction without explicit approval.

## A. Clash ws-opts.host fallback — confirmed, NO-GO

`clashServerName` reads headers.Host / headers.host / ws-opts.host; ws transport reads
only headers. Host-only alias therefore affects SNI but not the WebSocket Host header.
Mihomo WSOptions has no host field: explicit servername wins, then headers.Host, then
server address. Sing-box WebSocket also overrides request Host only from headers.Host.
WP-2 added explicit SNI -> transport-host fallback; WP-2b removed redundant server-domain
SNI while retaining transport-host fallback. Standard headers.Host forms remain correct.
Recommendation: remove only the ws-opts.host SNI alias, not headers.Host fallback.
Risk: alias-only forms change SNI and id; NO-GO pending owner approval.
URI host= already supplies transport Host and fallback SNI; explicit SNI wins.
Sing-box JSON preserves explicit fields, without this parser's SNI inference.
Tests: golden ids for standard Host, alias-only, explicit SNI and URI/JSON forms.

## B. Blank h2/http host/path — confirmed for Clash H2, NO-GO

URI commaList trims/drops blank hosts; blank HTTP/H2 path is omitted: no fix needed.
Clash H2 host: "" emits [""], mixed lists retain blanks, path: "" emits empty path.
Clash network:http is intentionally skipped (HTTP obfuscation is not native H2).
Pinned sing-box makes empty HTTP path '/': normalizing it buys no wire behavior.
For HTTP/2 an absent host list defaults to www.example.com; a supplied blank element
is assigned verbatim to request.Host, letting Go HTTP use URL authority instead.
Cleaning blanks can therefore change authority as well as id. NO-GO until agreed.
Tests: pin URI filtering, ordinary Clash lists and existing blank-form ids.

## C. Map.str — scalar compatibility retained; numeric bool defect, NO-GO

str accepts String/Number/Boolean, rejecting only structured List/Map. Checked forms
port: "443" or 443, tls: true or "true", alterId: "0" or 0 still parse; they are not
lost by stricter str. Existing string-bool extensions ("true", "1") are retained.
However bool handles Boolean/String, NOT Number: tls: 1 omits TLS, skip-cert-verify: 1
keeps verification enabled. This is a separate existing defect, not a str regression.
Mihomo ParseProxy uses WeaklyTypedInput; decodeBool accepts nonzero integers as true.
Thus numeric bools ARE valid mihomo forms. Correcting them changes outbound/id;
skip-cert-verify also weakens certificate checking as explicitly requested by the input.
Decision: STOP for owner approval, no numeric-bool correction silently applied.
SnakeYAML also changes numeric scalar spelling (leading zero/scientific notation) and
resolves dates before str sees them. Local behavior observed; mihomo parity not proven.
Do not claim that mihomo preserves original scalar text: its map decoder coerces numbers.
Tests: supported scalar forms plus golden ids; numeric-bool red tests after approval.

## D. Hung DNS retains probe permits — confirmed, GO

WP-3b keeps each worker permit until completion. Eight uninterruptible DNS calls can
stall the entire queued batch past the per-attempt deadline. Queue wait has no deadline.
Minimal proposal: release attempt permits at timeout/cancellation; separately bound
active-plus-lingering workers to 16, acquire worker capacity without waiting, return null
when saturated. Completion releases worker capacity; dispatcher parallelism matches it.
No new per-call threads; cancellation closes sockets, cancelled DNS never connects.
TCP/protect and connected-mode engine urltest semantics stay unchanged. Tests: queue
progress after deadline, bounded lingering workers, fast saturation, capacity reuse.
Original-production red run: 10 tests, 2 assertion failures, 0 errors/skipped.
## Primary sources
- [Mihomo parser/decoder](https://github.com/MetaCubeX/mihomo/tree/Meta/common/structure)
- [Mihomo WSOptions](https://github.com/MetaCubeX/mihomo/blob/Meta/adapter/outbound/vmess.go)
- [sing-box 1.14.1 transports](https://github.com/SagerNet/sing-box/tree/v1.14.1/transport)
