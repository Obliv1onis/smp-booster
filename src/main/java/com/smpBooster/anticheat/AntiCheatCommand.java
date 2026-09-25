package com.smpBooster.anticheat;

import com.smpBooster.SmpBooster;
import com.smpBooster.i18n.LanguageService;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class AntiCheatCommand implements CommandExecutor, TabCompleter {
    private final SmpBooster plugin;
    private final WhitelistStore whitelist;
    private final AntiCheatService antiCheat;
    private final LanguageService language;

    public AntiCheatCommand(SmpBooster plugin, WhitelistStore whitelist, AntiCheatService antiCheat,
                            LanguageService language) {
        this.plugin = plugin;
        this.whitelist = whitelist;
        this.antiCheat = antiCheat;
        this.language = language;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(language.text("op-only"));
            return true;
        }

        if (command.getName().equalsIgnoreCase("silentac")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
                sender.sendMessage(language.text(antiCheat.isSilent() ? "silent-status-on" : "silent-status-off"));
                return true;
            }
            boolean silent;
            if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) silent = !antiCheat.isSilent();
            else if (args[0].equalsIgnoreCase("on")) silent = true;
            else if (args[0].equalsIgnoreCase("off")) silent = false;
            else {
                sender.sendMessage(language.text("silent-usage"));
                return true;
            }
            antiCheat.setSilent(silent);
            sender.sendMessage(language.text(silent ? "silent-enabled" : "silent-disabled"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            List<String> names = whitelist.displayEntries();
            sender.sendMessage(language.text("whitelist-list", names.size(),
                    names.isEmpty() ? language.text("none") : String.join(", ", names)));
            return true;
        }
        if (args.length != 2 || !(args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            sender.sendMessage(language.text("whitelist-usage"));
            return true;
        }

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
        boolean add = args[0].equalsIgnoreCase("add");
        boolean changed = add ? whitelist.add(target) : whitelist.remove(target);
        sender.sendMessage(changed
                ? language.text(add ? "whitelist-added" : "whitelist-removed", args[1])
                : language.text("whitelist-unchanged", args[1]));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp()) return Collections.emptyList();
        if (command.getName().equalsIgnoreCase("silentac")) {
            return args.length == 1 ? filter(Arrays.asList("on", "off", "toggle", "status"), args[0]) : Collections.emptyList();
        }
        if (args.length == 1) return filter(Arrays.asList("add", "remove", "list"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) return filter(whitelist.displayEntries(), args[1]);
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value);
        return result;
    }
}
