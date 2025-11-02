package com.aesyin.pvpzone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;

public class PVPZoneNotifier extends JavaPlugin implements Listener {

    // --- Zone config ---
    private int pvpRadius;
    private String enterMessage;
    private String leaveMessage;
    private NamedTextColor enterColor;
    private NamedTextColor leaveColor;
    private String enterDisplayType;
    private String leaveDisplayType;
    private String combatDisplayType; // Separate combat display

    // --- Sound config ---
    private boolean soundEnabled;
    private String enterSoundName;
    private String leaveSoundName;
    private float soundVolume;
    private float soundPitch;

    // --- Combat config ---
    private boolean combatEnabled;
    private int combatDuration;
    private String combatTagMsg;
    private String combatSafeMsg;
    private String combatLogoutMsg;
    private String combatPunishment;
    private boolean disablePearls;
    private boolean disableElytra;
    private String blockedMsg;
    private final List<PotionEffect> combatDebuffs = new ArrayList<>();

    // --- Runtime tracking ---
    private final Map<UUID, Long> combatTagged = new HashMap<>();
    private final Set<UUID> loggedOutInCombat = new HashSet<>();
    private final Set<UUID> inPvpZone = new HashSet<>();
    private final Map<UUID, BukkitRunnable> combatCountdownTasks = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        // Reload command
        Objects.requireNonNull(getCommand("pvpzonereload")).setExecutor((sender, command, label, args) -> {
            reloadConfig();
            loadConfig();
            sender.sendMessage(Component.text("PVPZoneNotifier configuration reloaded.", NamedTextColor.GREEN));
            return true;
        });

        // PvP radius command
        Objects.requireNonNull(getCommand("pvpzoneradius")).setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("pvpzone.admin")) {
                sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(Component.text("Usage: /pvpzoneradius <number>", NamedTextColor.YELLOW));
                return true;
            }

            try {
                int newRadius = Integer.parseInt(args[0]);
                if (newRadius < 0) {
                    sender.sendMessage(Component.text("Radius must be 0 or higher.", NamedTextColor.RED));
                    return true;
                }

                pvpRadius = newRadius;
                getConfig().set("pvp-radius", newRadius);
                saveConfig();
                sender.sendMessage(Component.text("PvP radius set to " + newRadius + " blocks.", NamedTextColor.GREEN));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("Invalid number: " + args[0], NamedTextColor.RED));
            }
            return true;
        });

        getLogger().info("PVPZoneNotifier enabled successfully.");
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();

        // Zone
        pvpRadius = config.getInt("pvp-radius", 3000);
        enterMessage = config.getString("messages.enter-pvp", "You are now in a PVP Zone");
        leaveMessage = config.getString("messages.leave-pvp", "You are now in a Safe Zone");
        enterColor = parseColor(config.getString("colors.enter-pvp", "red"));
        leaveColor = parseColor(config.getString("colors.leave-pvp", "green"));

        enterDisplayType = config.getString("display.enter", "ACTION_BAR").toUpperCase(Locale.ROOT);
        leaveDisplayType = config.getString("display.leave", "ACTION_BAR").toUpperCase(Locale.ROOT);
        combatDisplayType = config.getString("display.combat", "ACTION_BAR").toUpperCase(Locale.ROOT);

        // Sound
        soundEnabled = config.getBoolean("sounds.enable", true);
        enterSoundName = config.getString("sounds.enter", "ENTITY_EXPERIENCE_ORB_PICKUP");
        leaveSoundName = config.getString("sounds.leave", "ENTITY_ITEM_PICKUP");
        soundVolume = (float) config.getDouble("sounds.volume", 1.0);
        soundPitch = (float) config.getDouble("sounds.pitch", 1.0);

        // Combat
        combatEnabled = config.getBoolean("combat.enabled", true);
        combatDuration = config.getInt("combat.tag-duration", 15);
        combatPunishment = config.getString("combat.punishment", "DEBUFF");
        disablePearls = config.getBoolean("combat.disable-pearls", true);
        disableElytra = config.getBoolean("combat.disable-elytra", true);

        combatTagMsg = config.getString("combat.messages.tag", "You are in combat!");
        combatSafeMsg = config.getString("combat.messages.safe", "You are no longer in combat.");
        combatLogoutMsg = config.getString("combat.messages.logout", "%player% logged out during combat!");
        blockedMsg = config.getString("combat.messages.blocked-item", "You cannot use that while in combat!");

        combatDebuffs.clear();
        if (config.isList("combat.debuff-effects")) {
            for (Map<?, ?> entry : config.getMapList("combat.debuff-effects")) {
                try {
                    String typeName = Objects.toString(entry.get("type"), "POISON");
                    PotionEffectType type = PotionEffectType.getByName(typeName.toUpperCase(Locale.ROOT));
                    if (type == null) continue;

                    Object durationObj = entry.get("duration");
                    Object amplifierObj = entry.get("amplifier");

                    int duration = 100;
                    int amplifier = 1;

                    if (durationObj instanceof Number num) duration = num.intValue();
                    if (amplifierObj instanceof Number num) amplifier = num.intValue();

                    combatDebuffs.add(new PotionEffect(type, duration, amplifier));
                } catch (Exception ex) {
                    getLogger().warning("Invalid debuff entry in config: " + entry);
                }
            }
        }
    }

    private NamedTextColor parseColor(String name) {
        try {
            return NamedTextColor.NAMES.value(name.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return NamedTextColor.WHITE;
        }
    }

    // --- Display helper ---
    private void sendDisplay(Player player, String type, String message, NamedTextColor color) {
        Component text = Component.text(message, color);
        switch (type) {
            case "TITLE" -> {
                Title title = Title.title(text, Component.empty(),
                        Title.Times.of(Duration.ofMillis(250), Duration.ofMillis(1500), Duration.ofMillis(250)));
                player.showTitle(title);
            }
            case "SUBTITLE" -> {
                Title title = Title.title(Component.empty(), text,
                        Title.Times.of(Duration.ofMillis(250), Duration.ofMillis(1500), Duration.ofMillis(250)));
                player.showTitle(title);
            }
            default -> player.sendActionBar(text);
        }
    }

    private void playSoundSafe(Player player, String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) { }
    }

    // --- PvP zone detection ---
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        Location loc = e.getTo();
        if (loc == null) return;

        double dist = Math.sqrt(loc.getX() * loc.getX() + loc.getZ() * loc.getZ());
        boolean inZone = dist >= pvpRadius;
        boolean wasInZone = inPvpZone.contains(player.getUniqueId());

        if (inZone && !wasInZone) {
            inPvpZone.add(player.getUniqueId());
            sendDisplay(player, enterDisplayType, enterMessage, enterColor);
            if (soundEnabled) playSoundSafe(player, enterSoundName, soundVolume, soundPitch);
        } else if (!inZone && wasInZone) {
            inPvpZone.remove(player.getUniqueId());
            sendDisplay(player, leaveDisplayType, leaveMessage, leaveColor);
            if (soundEnabled) playSoundSafe(player, leaveSoundName, soundVolume, soundPitch);
        }
    }

    // --- Combat tagging ---
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!combatEnabled) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof Player attacker)) return;

        double dist = Math.sqrt(victim.getLocation().getX() * victim.getLocation().getX() +
                victim.getLocation().getZ() * victim.getLocation().getZ());
        if (dist < pvpRadius) return;

        tagCombat(attacker);
        tagCombat(victim);
    }

    private void tagCombat(Player player) {
        combatTagged.put(player.getUniqueId(), System.currentTimeMillis());

        if (combatCountdownTasks.containsKey(player.getUniqueId())) {
            combatCountdownTasks.get(player.getUniqueId()).cancel();
        }

        BukkitRunnable countdown = new BukkitRunnable() {
            int secondsLeft = combatDuration;

            @Override
            public void run() {
                if (!isInCombat(player)) {
                    sendDisplay(player, combatDisplayType, combatSafeMsg, NamedTextColor.GREEN);
                    cancel();
                    combatCountdownTasks.remove(player.getUniqueId());
                    return;
                }

                sendDisplay(player, combatDisplayType, combatTagMsg + " (" + secondsLeft + "s)", NamedTextColor.RED);
                secondsLeft--;

                if (secondsLeft < 0) {
                    cancel();
                    combatCountdownTasks.remove(player.getUniqueId());
                }
            }
        };

        countdown.runTaskTimer(this, 0L, 20L);
        combatCountdownTasks.put(player.getUniqueId(), countdown);
    }

    private boolean isInCombat(Player player) {
        Long lastHit = combatTagged.get(player.getUniqueId());
        return lastHit != null && (System.currentTimeMillis() - lastHit) < (combatDuration * 1000L);
    }

    // --- Combat logout handling ---
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!combatEnabled) return;
        Player p = e.getPlayer();
        if (isInCombat(p)) {
            loggedOutInCombat.add(p.getUniqueId());
            String msg = combatLogoutMsg.replace("%player%", p.getName());
            Bukkit.broadcast(Component.text(msg, NamedTextColor.RED));

            switch (combatPunishment.toUpperCase(Locale.ROOT)) {
                case "KILL" -> p.setHealth(0.0);
                case "CLEAR_INVENTORY" -> p.getInventory().clear();
                case "DEBUFF" -> {} // handled on join
                default -> {}
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!combatEnabled) return;
        if (combatPunishment.equalsIgnoreCase("DEBUFF") && loggedOutInCombat.remove(p.getUniqueId())) {
            for (PotionEffect effect : combatDebuffs) {
                p.addPotionEffect(effect);
            }
            p.sendMessage(Component.text("You logged out during combat and are now weakened!", NamedTextColor.RED));
        }
    }

    // --- Prevent pearls/elytra in combat ---
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!combatEnabled || !disablePearls) return;
        Player p = e.getPlayer();
        if (isInCombat(p) && p.getInventory().getItemInMainHand().getType() == Material.ENDER_PEARL) {
            e.setCancelled(true);
            sendDisplay(p, combatDisplayType, blockedMsg, NamedTextColor.RED);
        }
    }

    @EventHandler
    public void onElytraToggle(PlayerToggleFlightEvent e) {
        if (!combatEnabled || !disableElytra) return;
        Player p = e.getPlayer();
        if (isInCombat(p) && p.isGliding()) {
            e.setCancelled(true);
            p.setGliding(false);
            sendDisplay(p, combatDisplayType, blockedMsg, NamedTextColor.RED);
        }
    }
}
