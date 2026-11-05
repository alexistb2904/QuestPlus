# Trusted command rewards (experimental)

Quest Plus command rewards are **off by default** for every world. Opting in is a server administrator decision, not a player setting.

A world may enable them in `questplus/config.json`:

```json
{
  "schemaVersion": 1,
  "autosaveSeconds": 5,
  "enableCommandRewards": true
}
```

Quest Studio offers the same project config flag. If disabled, the server refuses to load a quest pack containing command rewards rather than allowing unusable rewards to enter the ledger.

A quest reward uses:

```json5
{
  type: 'questplus:command',
  data: { command: 'say Quest completed' },
}
```

The command runs **only after the approved reward claim is durably reserved**, on the authoritative server thread, with the claiming player as command source and the GAMEMASTER permission set. It does not grant OWNER permissions. Command strings are bounded, single-line, and never interpolate player-provided text.

**Security:** a trusted pack author may still change world state with commands. Never enable this setting for untrusted downloaded quest packs or allow unknown users to publish/edit server quest content. This implementation does not sandbox game commands. A failed command can require administrator recovery because reward reservations are intentionally never replayed blindly.

No command runs while browsing a quest, importing a pack, or receiving a network packet.

## Unsupported

There is no FTB-style placeholder language or separate per-command permission escalation. Untrusted script support is intentionally excluded.
