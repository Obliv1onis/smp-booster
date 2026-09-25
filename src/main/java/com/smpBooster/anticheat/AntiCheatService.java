package com.smpBooster.anticheat;

import com.smpBooster.SmpBooster;
import com.smpBooster.i18n.LanguageService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiCheatService implements Listener, AutoCloseable {
    private enum Check { REACH, KILLAURA, CRYSTALAURA, FLIGHT, ELYTRA_FLIGHT }

    private static final DecimalFormat NUMBER = new DecimalFormat("0.00");
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final SmpBooster plugin;
    private final WhitelistStore whitelist;
    private final LanguageService language;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Long> alertCooldown = new ConcurrentHashMap<>();
    private final File logFile;
    private BufferedWriter logWriter;
    private boolean silent;

    public AntiCheatService(SmpBooster plugin, WhitelistStore whitelist, LanguageService language) {
        this.plugin = plugin;
        this.whitelist = whitelist;
        this.language = language;
        this.silent = plugin.getConfig().getBoolean("anti-cheat.silent", true);
        this.logFile = new File(plugin.getDataFolder(), "anticheat.log");
        try {
            Files.createDirectories(logFile.toPath().getParent());
            logWriter = Files.newBufferedWriter(logFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            plugin.getLogger().severe("无法打开 anticheat.log: " + exception.getMessage());
        }
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (State state : states.values()) {
                state.violations.replaceAll((check, value) -> Math.max(0.0, value - 0.25));
            }
        }, 100L, 100L);
    }

    public boolean isSilent() {
        return silent;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
        plugin.getConfig().set("anti-cheat.silent", silent);
        plugin.saveConfig();
        writeLog("SYSTEM", "silentac=" + silent);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        State state = state(event.getPlayer());
        state.resetMovement(event.getPlayer().getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        state(event.getPlayer()).exemptUntil = System.currentTimeMillis() + 1500L;
        state(event.getPlayer()).resetMovement(event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        state(event.getPlayer()).exemptUntil = System.currentTimeMillis() + 2000L;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            State state = state(player);
            state.exemptUntil = Math.max(state.exemptUntil, System.currentTimeMillis() + 1250L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFirework(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getMaterial() == Material.FIREWORK_ROCKET && event.getPlayer().isGliding()) {
            state(event.getPlayer()).boostUntil = System.currentTimeMillis() + 3500L;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        Entity target = event.getEntity();
        if (isExempt(player)) return;

        State state = state(player);
        long now = System.nanoTime();
        double reach = distanceToBox(player.getEyeLocation(), target.getBoundingBox());
        double maxReach = player.getGameMode() == GameMode.CREATIVE
                ? plugin.getConfig().getDouble("anti-cheat.reach.creative-max", 5.0)
                : plugin.getConfig().getDouble("anti-cheat.reach.survival-max", 3.15);
        maxReach += Math.min(0.18, Math.max(0, player.getPing() - 100) * 0.0006);

        if (reach > maxReach) {
            event.setCancelled(true);
            flag(player, Check.REACH, 4.0, "距离=" + NUMBER.format(reach) + " 允许=" + NUMBER.format(maxReach)
                    + " ping=" + player.getPing() + " target=" + target.getType());
        }

        double angle = aimAngle(player.getEyeLocation(), target.getBoundingBox());
        boolean noSight = !player.hasLineOfSight(target);
        if (angle > plugin.getConfig().getDouble("anti-cheat.combat.max-angle-degrees", 55.0) || noSight) {
            event.setCancelled(true);
            Check check = target instanceof EnderCrystal ? Check.CRYSTALAURA : Check.KILLAURA;
            flag(player, check, noSight ? 3.5 : 2.5, "视角差=" + NUMBER.format(angle) + "° 穿墙=" + noSight
                    + " target=" + target.getType());
        }

        long intervalMs = state.lastAttackNanos == 0 ? Long.MAX_VALUE : (now - state.lastAttackNanos) / 1_000_000L;
        boolean switched = state.lastTarget != null && !state.lastTarget.equals(target.getUniqueId());
        if (intervalMs < plugin.getConfig().getLong("anti-cheat.combat.minimum-attack-interval-ms", 70L)) {
            Check check = target instanceof EnderCrystal ? Check.CRYSTALAURA : Check.KILLAURA;
            flag(player, check, switched ? 2.5 : 1.25, "攻击间隔=" + intervalMs + "ms 快速切换目标=" + switched
                    + " target=" + target.getType());
        }
        state.lastAttackNanos = now;
        state.lastTarget = target.getUniqueId();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        Player player = event.getPlayer();
        State state = state(player);
        Location from = event.getFrom();
        Location to = event.getTo();
        if (isMovementExempt(player, state)) {
            state.resetMovement(to);
            return;
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.hypot(dx, dz);

        if (player.isOnGround()) {
            state.airTicks = 0;
            state.hoverTicks = 0;
            state.glideFlatTicks = 0;
            state.lastSafe = to.clone();
        } else {
            state.airTicks++;
            if (Math.abs(dy) < 0.018 && horizontal > 0.015) state.hoverTicks++;
            else state.hoverTicks = Math.max(0, state.hoverTicks - 2);
        }

        if (player.isGliding()) {
            if (System.currentTimeMillis() > state.boostUntil && dy > -0.035 && horizontal > 0.35) state.glideFlatTicks++;
            else state.glideFlatTicks = Math.max(0, state.glideFlatTicks - 2);
            int limit = plugin.getConfig().getInt("anti-cheat.flight.elytra-flat-ticks", 16);
            if (state.glideFlatTicks > limit) {
                flag(player, Check.ELYTRA_FLIGHT, 2.0, "连续平飞=" + state.glideFlatTicks + "ticks dy="
                        + NUMBER.format(dy) + " 水平速度=" + NUMBER.format(horizontal));
                setback(event, state);
            }
        } else {
            int hoverLimit = plugin.getConfig().getInt("anti-cheat.flight.hover-ticks", 22);
            double maxRise = plugin.getConfig().getDouble("anti-cheat.flight.max-unexplained-rise", 0.62);
            if (state.airTicks > 12 && dy > -0.06) state.floatTicks++;
            else state.floatTicks = Math.max(0, state.floatTicks - 2);
            double totalRise = state.lastSafe == null ? 0.0 : to.getY() - state.lastSafe.getY();
            double maxAirRise = plugin.getConfig().getDouble("anti-cheat.flight.max-air-rise", 2.15);
            boolean impossibleRise = state.airTicks > 10 && totalRise > maxAirRise;
            boolean sustainedFloat = state.floatTicks > hoverLimit;
            if ((state.hoverTicks > hoverLimit && state.airTicks > hoverLimit) || sustainedFloat
                    || impossibleRise || (dy > maxRise && state.airTicks > 2)) {
                double weight = impossibleRise || dy > maxRise ? 3.0 : 2.0;
                flag(player, Check.FLIGHT, weight, "滞空=" + state.hoverTicks + " 平缓飞行=" + state.floatTicks
                        + "ticks dy=" + NUMBER.format(dy) + " 水平速度=" + NUMBER.format(horizontal));
                setback(event, state);
            }
        }
        state.lastLocation = to.clone();
    }

    private void setback(PlayerMoveEvent event, State state) {
        if (state.lastSafe != null && System.currentTimeMillis() - state.lastSetback > 750L) {
            event.setTo(state.lastSafe);
            state.lastSetback = System.currentTimeMillis();
            state.exemptUntil = state.lastSetback + 500L;
        }
    }

    private boolean isExempt(Player player) {
        return whitelist.contains(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean isMovementExempt(Player player, State state) {
        Material feet = player.getLocation().getBlock().getType();
        Material below = player.getLocation().clone().subtract(0, 0.25, 0).getBlock().getType();
        if (isExempt(player) || player.getAllowFlight() || player.isFlying() || player.isInsideVehicle()
                || player.isSwimming() || player.isRiptiding() || player.isClimbing()
                || player.getLocation().getBlock().isLiquid() || feet == Material.COBWEB || feet == Material.POWDER_SNOW
                || below == Material.SLIME_BLOCK || below == Material.HONEY_BLOCK
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;
        return System.currentTimeMillis() < state.exemptUntil;
    }

    private void flag(Player player, Check check, double amount, String evidence) {
        State state = state(player);
        double vl = state.violations.merge(check, amount, Double::sum);
        String details = "player=" + player.getName() + " uuid=" + player.getUniqueId() + " check=" + check
                + " vl=" + NUMBER.format(vl) + " world=" + player.getWorld().getName()
                + " xyz=" + blockPosition(player.getLocation()) + " " + evidence;
        writeLog("FLAG", details);

        double threshold = plugin.getConfig().getDouble("anti-cheat.thresholds." + check.name().toLowerCase().replace('_', '-'), 8.0);
        if (vl < threshold) return;

        long now = System.currentTimeMillis();
        long previous = alertCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 3000L) return;
        alertCooldown.put(player.getUniqueId(), now);

        if (silent) {
            String alert = "§c§l" + language.text("ac-alert", player.getName(), displayCheck(check), NUMBER.format(vl));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.isOp()) continue;
                online.sendMessage(alert);
                online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.35f);
            }
            writeLog("ALERT", details);
        } else {
            punish(player, check, details);
        }
    }

    private String displayCheck(Check check) {
        return language.text("check." + check.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private void punish(Player player, Check check, String details) {
        if (!player.isOnline()) return;
        String durationText = plugin.getConfig().getString("anti-cheat.ban.duration", "7d");
        Date expires = parseExpiry(durationText);
        String template = plugin.getConfig().getString("anti-cheat.ban.message", "检测到作弊：%check%\n封禁时长：%duration%");
        String message = template.replace("%player%", player.getName()).replace("%check%", check.name())
                .replace("%duration%", durationText == null ? "permanent" : durationText);
        writeLog("BAN", details + " expires=" + (expires == null ? "permanent" : expires));
        player.ban(message, expires, "SMP Booster AntiCheat", true);
    }

    private Date parseExpiry(String text) {
        if (text == null || text.isBlank() || text.equalsIgnoreCase("permanent") || text.equalsIgnoreCase("forever")) return null;
        try {
            char unit = Character.toLowerCase(text.charAt(text.length() - 1));
            long value = Long.parseLong(text.substring(0, text.length() - 1));
            Duration duration = switch (unit) {
                case 'm' -> Duration.ofMinutes(value);
                case 'h' -> Duration.ofHours(value);
                case 'd' -> Duration.ofDays(value);
                case 'w' -> Duration.ofDays(value * 7L);
                default -> throw new IllegalArgumentException();
            };
            return Date.from(Instant.now().plus(duration));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("无效的封禁时长 '" + text + "'，将永久封禁。支持 30m/12h/7d/2w/permanent。");
            return null;
        }
    }

    private double distanceToBox(Location eye, BoundingBox box) {
        double x = clamp(eye.getX(), box.getMinX(), box.getMaxX());
        double y = clamp(eye.getY(), box.getMinY(), box.getMaxY());
        double z = clamp(eye.getZ(), box.getMinZ(), box.getMaxZ());
        return eye.toVector().distance(new Vector(x, y, z));
    }

    private double aimAngle(Location eye, BoundingBox box) {
        Vector center = box.getCenter().subtract(eye.toVector());
        if (center.lengthSquared() == 0) return 0;
        double dot = eye.getDirection().normalize().dot(center.normalize());
        return Math.toDegrees(Math.acos(clamp(dot, -1.0, 1.0)));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String blockPosition(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private State state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new State(player.getLocation()));
    }

    private synchronized void writeLog(String type, String text) {
        String line = LOG_TIME.format(Instant.now()) + " [" + type + "] " + text;
        plugin.getLogger().info("[AC] " + text);
        if (logWriter == null) return;
        try {
            logWriter.write(line);
            logWriter.newLine();
            logWriter.flush();
        } catch (IOException exception) {
            plugin.getLogger().severe("写入 anticheat.log 失败: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        if (logWriter == null) return;
        try {
            logWriter.close();
        } catch (IOException ignored) {
        }
        logWriter = null;
    }

    private static final class State {
        private final EnumMap<Check, Double> violations = new EnumMap<>(Check.class);
        private Location lastLocation;
        private Location lastSafe;
        private int airTicks;
        private int hoverTicks;
        private int glideFlatTicks;
        private int floatTicks;
        private long exemptUntil;
        private long boostUntil;
        private long lastSetback;
        private long lastAttackNanos;
        private UUID lastTarget;

        private State(Location location) {
            resetMovement(location);
        }

        private void resetMovement(Location location) {
            lastLocation = location.clone();
            lastSafe = location.clone();
            airTicks = 0;
            hoverTicks = 0;
            glideFlatTicks = 0;
            floatTicks = 0;
        }
    }
}
