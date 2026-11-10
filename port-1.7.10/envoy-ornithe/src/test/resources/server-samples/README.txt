Real server plugin-message payloads recorded on 2026-11-07 (server configuration only, no player data):
- hypixel-badlion-mods.json     : channel badlion:mods sent by mc.hypixel.net
- minemen-badlion-modapi.json   : channel badlion:modapi sent by eu.minemen.club
- minemen-apollo-json.json      : channel apollo:json (Apollo's JSON transport) sent by eu.minemen.club
- strafepvp-lunar-apollo-configurable.hex : the payload bytes of lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage (inside the protobuf Any on channel lunar:apollo) sent by strafepvp.com.
  Layout: top level repeated field 1 = ConfigurableSettings { f2 string module, f3 bool enable, f4 repeated {f1 string key, f2 google.protobuf.Value} }.
