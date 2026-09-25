package com.smpBooster.anticheat;

import com.smpBooster.SmpBooster;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WhitelistStore {
    private final SmpBooster plugin;
    private final File file;
    private final Set<UUID> entries = new HashSet<>();
    private final YamlConfiguration yaml;

    public WhitelistStore(SmpBooster plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ac-whitelist.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("players")) {
            try {
                entries.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("反作弊白名单里有无效 UUID: " + raw);
            }
        }
    }

    public boolean contains(UUID uuid) {
        return entries.contains(uuid);
    }

    public boolean add(OfflinePlayer player) {
        boolean changed = entries.add(player.getUniqueId());
        if (changed) save();
        return changed;
    }

    public boolean remove(OfflinePlayer player) {
        boolean changed = entries.remove(player.getUniqueId());
        if (changed) save();
        return changed;
    }

    public List<String> displayEntries() {
        List<String> result = new ArrayList<>();
        for (UUID uuid : entries) {
            OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
            result.add(player.getName() == null ? uuid.toString() : player.getName());
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private void save() {
        yaml.set("players", entries.stream().map(UUID::toString).sorted().toList());
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("无法保存 ac-whitelist.yml: " + exception.getMessage());
        }
    }
}
