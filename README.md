⚔️ PVP Zones Notifier is a lightweight, highly configurable plugin for PaperMC servers that provides PVP zone and combat logging. If you want a plugin that supports both building and PVP, this plugin helps prevent the unfair advantages of combat logging and the randomised killing some players do. This plugin is built with simplicity in mind, with a highly configurable configuration file that is very easy to use.

# ✅ Key Features
- Once a fight happens between players, they're **automatically** put into combat.
  
- Displays a timer in the action bar (_configurable_), **counting down** per player.
  
- Plays a **sound** and shows a **title** when entering and leaving a PVP Zone.
  
- **Blocks** specific items **when in combat**.
 
- **Configurable** punishment system (_KILL, CLEAR INVENTORY, and DEBUFF players_).
  
- In-game commands:


```
/pvpzoneradius <number>
```
 — Set the PvP zone radius dynamically


```
/pvpzonereload
```
 — reload the config without restarting the server

- **Fully customizable** messages for combat enter, exit, logout, and PvP zone notifications.

# ⚙️ Installation:

1. Download the PVPZoneNotifier .jar file.

2. Place it into your server's /plugins folder.

3. Restart or reload the server.

4. Edit the config.yml in /plugins/PVPZonesNotifier to suit your needs.

5. The plugin should start working after that.

# 🔧 Configuration:

In the **config.yml** file, there are hints to ease the customisation within the plugin.

```
# Distance (in blocks) from 0,0 at which PVP zone begins
pvp-radius: 3000

display:
  enter: "TITLE"          # PvP zone enter message display: ACTION_BAR / TITLE / SUBTITLE
  leave: "TITLE"       # PvP zone leave message display
  combat: "ACTION_BAR"    # Combat countdown message display

messages:
  enter-pvp: "You are now in a PVP Zone"
  leave-pvp: "You are now in a Safe Zone"

colors:
  enter-pvp: "red"
  leave-pvp: "green"

combat:
  enabled: true
  tag-duration: 15
  punishment: "DEBUFF" # KILL, CLEAR_INVENTORY, DEBUFF, NONE
  disable-pearls: true
  disable-elytra: true

  debuff-effects:
    - type: POISON
      duration: 100   # ticks (20 = 1 second)
      amplifier: 1
    - type: SLOWNESS
      duration: 100
      amplifier: 1
    - type: BLINDNESS
      duration: 100
      amplifier: 1

sounds:
  enable: true
  enter: "ENTITY_EXPERIENCE_ORB_PICKUP"  # Sound when entering PvP zone
  leave: "ENTITY_ITEM_PICKUP"            # Sound when leaving PvP zone
  volume: 1.0
  pitch: 1.0

  messages:
    tag: "You are in combat!"
    safe: "You are no longer in combat."
    logout: "%player% logged out during combat!"
    blocked-item: "You cannot use that while in combat!"
```

_I will be updating this plugin to newer releases, not snapshots or betas, which may take a while. Feel free to fork this plugin to other platforms with proper credit._

_"Original Plugin Made By [aesyin](https://modrinth.com/user/aesyin)<br>
[Plugin Download](https://modrinth.com/plugin/pvp-zone-notifier)
"_
