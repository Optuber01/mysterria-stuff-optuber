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
per-instance `item_uuid` top-level with `item_uuid_scope=instance`. Stackable
wrapper items are left unstamped and the row carries the same row-only lot
fields instead. No player-visible stacking behaviour changes.

`item_uuid_scope` is always present next to `item_uuid`: `instance` means the id
is written to the item PDC and names one item; `lot` means the id exists only in
the row and names one granted stack. Consumers must not join or dedupe on
`item_uuid` without also checking the scope. Elytra and Last Sprint kit grant
rows carry the delivered items' existing `item_uuid` (scope `instance`) when
they have one; a kit with several identified items carries `item_uuids`
(comma separated) and `item_uuid_count` instead.

## Delivery outcomes

Grant rows (`token.granted`, `item.granted`, `kit.granted`, `cosmetic.unlocked`)
carry `delivery_mode`, `delivered_amount` (inventory) and `dropped_amount`
(ground). A ground drop only counts when the spawned item entity is still valid;
otherwise the amount is reported as `undelivered_amount` and the row is FAILED
with `failure=items_undelivered` (`delivery_mode` `undelivered` or
`partial_undelivered`). A delivery that throws emits a FAILED row with
`failure=delivery_exception` and `error_class` before the exception propagates.
A failed elytra creation emits a FAILED `item.granted` row
(`failure=item_creation_failed`). A universal-token exchange whose wrapper cannot
be created or tagged for HMCWraps emits FAILED `cosmetic.unlocked`
(`failure_stage=exchange`) and refunds the token.

## Deliberate exclusions

GUI previews (except a bounded FAILED `cosmetic.unlocked` with
`failure_stage=preview` when a wrap preview cannot load, at most once per player
per wrap per 60 seconds), rendering, routine interactions, transient sprint/listener
effects, cosmetic equip/remove events, and HMCWraps ownership changes are not
emitted here. HMCWraps owns the durable cosmetic ownership model and does not
expose an ownership-mutation API in its integration contract; the HMCWraps
plugin's own event/owner records are the authoritative source for those changes.

## MythicDungeons parties and dungeons

`features/dungeons/MythicDungeonsAuditModule` binds only while the MythicDungeons
plugin is enabled. MysterriaStuff keeps `loadbefore: MythicDungeons` (the Dungeon
World Enforcer must create the void world first), so MythicDungeons is not in
`softdepend` (that would form a load-order cycle); the module instead binds on
MythicDungeons' `PluginEnableEvent` (or in `onEnable` if it is already enabled) and
unbinds on its `PluginDisableEvent`. There is no compile dependency: event classes
are loaded by name through MythicDungeons' class loader, registered at `MONITOR`
with `ignoreCancelled=true`, and every value is read through cached reflective
getter lookups (bounded, misses cached too). A missing event class or getter logs
one WARN and disables that row only; any other failure logs one WARN and disables
the module. Nothing is modified or cancelled; there is no player- or staff-visible
output.

All rows: outcome `OBSERVED`, privacy `INTERNAL` (party chat: `CHAT_CONTENT`),
risk `LOW` (loot: `NORMAL`), `source_plugin=MythicDungeons`.

| Event | Actor / subject | Correlation | Business ID | Metadata |
| --- | --- | --- | --- | --- |
| `mysterria-stuff.party.created` | leader / - | party id | `party:<party id>` | `party_id`, `party_id_source`, `members`, `member_count`, leader `world/x/y/z` |
| `mysterria-stuff.party.joined` | joining player / leader | party id | `party:<party id>` | `members` (includes the joiner), `member_count`, joiner `world/x/y/z` |
| `mysterria-stuff.party.left` | leaving player / - | party id | `party:<party id>` | `remaining_members`, `remaining_count`, leaver `world/x/y/z` |
| `mysterria-stuff.party.kicked` | kicker (unset when not exposed) / kicked player | party id | `party:<party id>` | `kicker_exposed`, `remaining_members`, `remaining_count`, `world/x/y/z` + `location_source` (`actor` or `subject`) |
| `mysterria-stuff.party.chat` | unset / - | party id | `party:<party id>` | `message`, `message_length`, `recipient_count`, `sender_exposed=false`, `actor_name=unknown` |
| `mysterria-stuff.dungeon.started` | party leader, else first participant | instance UUID | `party:<party id>`, else `dungeon_instance:<instance UUID>` | `instance_id`, `party_id`, `dungeon`, `dungeon_display_name`, `instance_name`, `participants`, `participant_count`, `world/x/y/z` + `location_source` (`instance_start` or `participant`) |
| `mysterria-stuff.dungeon.ended` | unset (`actor_name=system`) | instance UUID | as above | dungeon fields, `participants`, `participant_count`, `result` (`completed`, `not_completed`, `unknown`), `status`, `time_elapsed` (MythicDungeons counter), `duration_ms` (when the start was observed), participant `world/x/y/z` |
| `mysterria-stuff.dungeon.player_left` | player | instance UUID | as above | dungeon fields, `edit_mode`, `reason_exposed=false`, player `world/x/y/z` |
| `mysterria-stuff.dungeon.loot_generated` | player | instance UUID | as above | dungeon fields, `loot_table`, `material`, `amount`, `item_uuid` + `item_uuid_scope=instance` when the stack carries `circleofimagination:item_uuid`, player `world/x/y/z` (`location_source=player`) |

**Party id.** MythicDungeons 2.1.0 parties expose no id. If a party object ever
exposes a UUID getter (`getUuid`, `getUniqueId`, `getId`, `getPartyId`) it is used
(`party_id_source=exposed`). Otherwise the id is
`UUID.nameUUIDFromBytes("mythicdungeons-party:<leader uuid>:<millis>")`, where
millis is the create-event time (`leader_created`) or, for a party first seen
through another event, that event's time (`leader_first_seen`). A party first
seen by the async chat event, whose leader cannot be read off the main thread,
gets `anonymous_first_seen`. The id is then fixed for that party object (tracked
by identity) so later rows join. Dungeon rows use the instance UUID as correlation
and `party:<party id>` as business id, so party and dungeon rows join on business id.

**Limits of the MythicDungeons API (2.1.0).** The party chat event does not carry
the sender, so `party.chat` has no actor; `recipient_count` is the party size last
observed on the main thread (key omitted when unknown) and
excludes chat spies. Chat is async: the row reads only the event's message and
party reference, no Bukkit API. `PlayerLeaveDungeonEvent` exposes no reason.
`DungeonEndEvent` does not distinguish failed from abandoned, so `result` is
`completed` only when the instance reports the dungeon finished. The loot event
fires once per generated stack and exposes no chest location, so each row names
one item and uses the player's position. `party.kicked` fires before the removal
is applied; the matching `party.left` follows. Member/participant lists hold at
most 6 UUIDs (`<key>_truncated=true` when longer; the count is exact). Tracking
maps (parties, running instances, reflective lookups) hold at most 512 entries
each and evict the oldest.

## Overlap policy

Routine administrative grant success messages use PrettyLogger.debug; token/cosmetic/message mutation events remain the staff audit view. Player-facing confirmations and actionable errors remain.
