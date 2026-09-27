# Mysterria Stuff audit events

Mysterria Stuff emits best-effort, staff-restricted events through its shaded neutral audit client. A full queue or failed spool write does not block token, cosmetic, or message-store operations.

The optional per-server audit engine owns SQLite and local staff searches. Each producer writes to its own bounded spool directory even when the engine is absent. Existing gameplay dependencies remain separate from audit transport.

## Canonical events

| Event | Authoritative commit | Business ID | Notes |
| --- | --- | --- | --- |
| `mysterria-stuff.token.granted` | Inventory add/drop completes | `token:universal` or `token:joinmsg` | Includes `token_type`, `amount`, and delivery/reason metadata. |
| `mysterria-stuff.token.consumed` | Token stack is decremented | `token:universal` or `token:joinmsg` | Emitted only after the decrement succeeds. |
| `mysterria-stuff.cosmetic.unlocked` | Wrapper item is added or dropped after a universal-token exchange | `wrap:<wrap uuid or loader id>` | Includes the stable wrap ID and physical item projection. |
| `mysterria-stuff.joinmsg.message_set` | Join/quit message store save succeeds | `joinmsg:<player uuid or pending name>` | Admin actor is recorded when the sender is a player. |
| `mysterria-stuff.joinmsg.message_removed` | Join/quit message removal reports a changed entry | `joinmsg:<player uuid or pending name>` | `message_type` identifies join, quit, or both. |
| `mysterria-stuff.joinmsg.default_changed` | Default message store save succeeds | `joinmsg:default:join` or `joinmsg:default:quit` | Message contents are intentionally not logged. |
| `mysterria-stuff.joinmsg.firstjoin_changed` | First-join message store save succeeds | `joinmsg:first_join` | Message contents are intentionally not logged. |

Every independent operation gets a fresh correlation UUID. Related lifecycle
events, such as a token consumption and its cancellation refund, reuse the same
correlation UUID. Player UUIDs are used as actor/subject IDs where available;
console actors remain unset. Metadata keys are snake_case and bounded by the
shared audit contract.

## Location

Rows that involve a player carry `world`, `x`, `y`, `z`. Admin join-message rows
(`message_set`, `message_removed`, `default_changed`, `firstjoin_changed`) use the
online subject's position when there is one (`location_source=subject`).
Otherwise they use the in-game admin actor's position (`location_source=actor`).
Console-issued rows with no online subject carry no location.

## Item identity

Tokens (universal and joinmsg) are fungible and stackable. On origin/main every
token of a type carries only the same constant marker PDC, so separately
granted stacks stack together. To keep that, tokens are never stamped with
`circleofimagination:item_uuid` or any other per-instance PDC value (stamping
would also trip CoI's dupe scan, which treats that key as unique per instance).

Instead each granted stack is identified per lot, in the audit row only: the
`token.granted` row carries top-level `item_uuid` (a fresh UUID per granted
stack, `item_uuid_scope=lot`), `item_mint_qty` (the stack amount),
`item_origin` (`STAFF_GRANT` for admin gives, `SHOP` for refunds) and, for
admin gives, `item_minted_by` (actor UUID or `console`). The lot UUID is never
written to the item, so it cannot be read back on consumption; `item_uuid` on a
`token.consumed` row appears only if the consumed stack already carried one.

Wrapper items produced by a universal-token exchange are stamped with
`item_uuid`, `item_origin=SHOP` and `item_parent` only when they are
non-stackable (max stack size 1, amount 1); the row then carries that
per-instance `item_uuid` top-level. Stackable wrapper items are left unstamped
and the row carries the same row-only lot fields instead. No player-visible
stacking behaviour changes.

## Deliberate exclusions

GUI previews, rendering, routine interactions, transient sprint/listener
effects, cosmetic equip/remove events, and HMCWraps ownership changes are not
emitted here. HMCWraps owns the durable cosmetic ownership model and does not
expose an ownership-mutation API in its integration contract; the HMCWraps
plugin's own event/owner records are the authoritative source for those changes.

## Overlap policy

Routine administrative grant success messages use PrettyLogger.debug; token/cosmetic/message mutation events remain the staff audit view. Player-facing confirmations and actionable errors remain.
