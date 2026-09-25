package com.smpBooster.paperfix;

import com.smpBooster.SmpBooster;
import com.smpBooster.i18n.LanguageService;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Toggles Paper's configurable Vanilla exploit fixes. These settings live in
 * Paper's own configuration; reflection is used only to apply the same values
 * to the already-loaded configuration objects, avoiding a forced restart.
 */
public final class PaperFixService implements CommandExecutor, TabCompleter {
    private static final Map<String, String> GLOBAL_FIELDS = new LinkedHashMap<>();

    static {
        GLOBAL_FIELDS.put("allow-headless-pistons", "allowHeadlessPistons");
        GLOBAL_FIELDS.put("allow-permanent-block-break-exploits", "allowPermanentBlockBreakExploits");
        GLOBAL_FIELDS.put("allow-piston-duplication", "allowPistonDuplication");
        GLOBAL_FIELDS.put("allow-unsafe-end-portal-teleportation", "allowUnsafeEndPortalTeleportation");
        GLOBAL_FIELDS.put("skip-tripwire-hook-placement-validation", "skipTripwireHookPlacementValidation");
        GLOBAL_FIELDS.put("update-equipment-on-player-actions", "updateEquipmentOnPlayerActions");
    }

    private static final String CRYSTAL_YAML_KEY = "fix-invulnerable-end-crystal-exploit";
    private static final String CRYSTAL_FIELD = "fixInvulnerableEndCrystalExploit";

    private final SmpBooster plugin;
    private final LanguageService language;
    private final Path globalConfig;
    private final Path worldDefaultsConfig;

    public PaperFixService(SmpBooster plugin, LanguageService language) {
        this.plugin = plugin;
        this.language = language;
        Path serverRoot = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        this.globalConfig = serverRoot.resolve("config/paper-global.yml");
        this.worldDefaultsConfig = serverRoot.resolve("config/paper-world-defaults.yml");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(language.text("op-only"));
            return true;
        }
        if (args.length > 1) {
            usage(sender);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        Boolean current = runtimeStatus();
        boolean enabled;
        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) enabled = current == null || !current;
        else if (args[0].equalsIgnoreCase("on")) enabled = true;
        else if (args[0].equalsIgnoreCase("off")) enabled = false;
        else {
            usage(sender);
            return true;
        }

        try {
            persist(enabled);
        } catch (IOException exception) {
            plugin.getLogger().severe("无法写入 Paper 配置: " + exception.getMessage());
            sender.sendMessage(language.text("paperfix-write-error"));
            return true;
        }

        boolean liveApplied = applyRuntime(enabled);
        sender.sendMessage(language.text(enabled ? "paperfix-enabled" : "paperfix-disabled"));
        sender.sendMessage(liveApplied
                ? language.text("paperfix-live")
                : language.text("paperfix-restart")
        );
        return true;
    }

    private void sendStatus(CommandSender sender) {
        Boolean status = runtimeStatus();
        if (status == null) {
            sender.sendMessage(language.text("paperfix-status-error"));
        } else {
            sender.sendMessage(language.text(status ? "paperfix-status-on" : "paperfix-status-off"));
        }
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(language.text("paperfix-usage"));
    }

    private void persist(boolean fixesEnabled) throws IOException {
        Map<String, Boolean> globalValues = new LinkedHashMap<>();
        // allow/skip=false means Paper blocks the Vanilla exploit.
        for (String key : GLOBAL_FIELDS.keySet()) globalValues.put(key, !fixesEnabled);
        // This setting uses the opposite wording: true enables Paper's equipment refresh fix.
        globalValues.put("update-equipment-on-player-actions", fixesEnabled);
        patchYaml(globalConfig, globalValues);
        patchYaml(worldDefaultsConfig, Map.of(CRYSTAL_YAML_KEY, fixesEnabled));

        // Respect explicit per-world overrides by updating them as well.
        for (World world : plugin.getServer().getWorlds()) {
            Path worldConfig = world.getWorldFolder().toPath().resolve("paper-world.yml");
            if (Files.isRegularFile(worldConfig) && containsYamlKey(worldConfig, CRYSTAL_YAML_KEY)) {
                patchYaml(worldConfig, Map.of(CRYSTAL_YAML_KEY, fixesEnabled));
            }
        }
    }

    private boolean containsYamlKey(Path path, String key) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*:").matcher(content).find();
    }

    private void patchYaml(Path path, Map<String, Boolean> values) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("配置文件不存在: " + path);
        Path backup = path.resolveSibling(path.getFileName() + ".smp-booster.bak");
        if (!Files.exists(backup)) Files.copy(path, backup);

        String content = Files.readString(path, StandardCharsets.UTF_8);
        for (Map.Entry<String, Boolean> entry : values.entrySet()) {
            Pattern pattern = Pattern.compile("(?m)^(\\s*" + Pattern.quote(entry.getKey()) + "\\s*:\\s*)(?:true|false)(\\s*(?:#.*)?)$");
            Matcher matcher = pattern.matcher(content);
            if (!matcher.find()) throw new IOException("Paper 配置缺少键: " + entry.getKey());
            content = matcher.replaceFirst("$1" + entry.getValue() + "$2");
        }

        Path temporary = path.resolveSibling(path.getFileName() + ".smp-booster.tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unsupportedAtomicMove) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean applyRuntime(boolean fixesEnabled) {
        try {
            Object global = globalConfiguration();
            Object unsupported = publicField(global, "unsupportedSettings");
            for (String field : GLOBAL_FIELDS.values()) setBoolean(unsupported, field, !fixesEnabled);
            setBoolean(unsupported, "updateEquipmentOnPlayerActions", fixesEnabled);

            for (World world : plugin.getServer().getWorlds()) {
                Object worldConfig = worldConfiguration(world);
                Object worldUnsupported = publicField(worldConfig, "unsupportedSettings");
                setBoolean(worldUnsupported, CRYSTAL_FIELD, fixesEnabled);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("无法即时更新 Paper 配置，将在重启后生效: " + exception);
            return false;
        }
    }

    private Boolean runtimeStatus() {
        try {
            Object global = globalConfiguration();
            Object unsupported = publicField(global, "unsupportedSettings");
            boolean enabled = true;
            for (Map.Entry<String, String> entry : GLOBAL_FIELDS.entrySet()) {
                boolean value = getBoolean(unsupported, entry.getValue());
                enabled &= entry.getKey().equals("update-equipment-on-player-actions") ? value : !value;
            }
            for (World world : plugin.getServer().getWorlds()) {
                Object worldConfig = worldConfiguration(world);
                enabled &= getBoolean(publicField(worldConfig, "unsupportedSettings"), CRYSTAL_FIELD);
            }
            return enabled;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("无法读取 Paper 修复状态: " + exception);
            return null;
        }
    }

    private Object globalConfiguration() throws ReflectiveOperationException {
        Class<?> type = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
        return type.getMethod("get").invoke(null);
    }

    private Object worldConfiguration(World world) throws ReflectiveOperationException {
        Method getHandle = world.getClass().getMethod("getHandle");
        Object handle = getHandle.invoke(world);
        return handle.getClass().getMethod("paperConfig").invoke(handle);
    }

    private Object publicField(Object owner, String name) throws ReflectiveOperationException {
        return owner.getClass().getField(name).get(owner);
    }

    private boolean getBoolean(Object owner, String name) throws ReflectiveOperationException {
        return owner.getClass().getField(name).getBoolean(owner);
    }

    private void setBoolean(Object owner, String name, boolean value) throws ReflectiveOperationException {
        owner.getClass().getField(name).setBoolean(owner, value);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp() || args.length != 1) return Collections.emptyList();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Arrays.asList("on", "off", "toggle", "status").stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
    }
}
