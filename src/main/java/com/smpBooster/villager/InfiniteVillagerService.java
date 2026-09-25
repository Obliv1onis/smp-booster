package com.smpBooster.villager;

import com.smpBooster.SmpBooster;
import com.smpBooster.i18n.LanguageService;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class InfiniteVillagerService implements Listener, CommandExecutor, TabCompleter {
    private static final String CONFIG_PATH = "features.infinite-villager-trades";

    private final SmpBooster plugin;
    private final LanguageService language;
    private boolean enabled;

    public InfiniteVillagerService(SmpBooster plugin, LanguageService language) {
        this.plugin = plugin;
        this.language = language;
        this.enabled = plugin.getConfig().getBoolean(CONFIG_PATH, false);
    }

    public void start() {
        if (enabled) restoreAllLoadedVillagers();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        if (!enabled || !(event.getVillager() instanceof Villager)) return;
        // Paper 在完成交易前提供此开关；关闭 uses 增长即可真正做到永不售罄。
        event.setIncreaseTradeUses(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!enabled) return;
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Villager villager) restore(villager);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(language.text("op-only"));
            return true;
        }

        if (args.length > 1) {
            sender.sendMessage(language.text("villager-usage"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        boolean next;
        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) next = !enabled;
        else if (args[0].equalsIgnoreCase("on")) next = true;
        else if (args[0].equalsIgnoreCase("off")) next = false;
        else {
            sender.sendMessage(language.text("villager-usage"));
            return true;
        }

        enabled = next;
        plugin.getConfig().set(CONFIG_PATH, enabled);
        plugin.saveConfig();
        int restored = enabled ? restoreAllLoadedVillagers() : 0;
        sender.sendMessage(enabled ? language.text("villager-enabled", restored) : language.text("villager-disabled"));
        return true;
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(language.text(enabled ? "villager-status-on" : "villager-status-off"));
    }

    private int restoreAllLoadedVillagers() {
        int count = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                restore(villager);
                count++;
            }
        }
        return count;
    }

    private void restore(Villager villager) {
        List<MerchantRecipe> recipes = villager.getRecipes();
        boolean changed = false;
        for (MerchantRecipe recipe : recipes) {
            if (recipe.getUses() > 0) {
                recipe.setUses(0);
                changed = true;
            }
        }
        if (changed) villager.setRecipes(recipes);
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
