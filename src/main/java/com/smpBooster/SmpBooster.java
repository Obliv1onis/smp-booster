package com.smpBooster;

import com.smpBooster.anticheat.AntiCheatCommand;
import com.smpBooster.anticheat.AntiCheatService;
import com.smpBooster.anticheat.WhitelistStore;
import com.smpBooster.i18n.LanguageService;
import com.smpBooster.paperfix.PaperFixService;
import com.smpBooster.restrictions.RestrictionService;
import com.smpBooster.villager.InfiniteVillagerService;
import org.bukkit.plugin.java.JavaPlugin;

public final class SmpBooster extends JavaPlugin {

    private AntiCheatService antiCheat;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        LanguageService language = new LanguageService(this);
        getCommand("language").setExecutor(language);
        getCommand("language").setTabCompleter(language);

        RestrictionService restrictions = new RestrictionService(this, language);
        getServer().getPluginManager().registerEvents(restrictions, this);
        restrictions.startInventoryScanner();

        WhitelistStore whitelist = new WhitelistStore(this);
        antiCheat = new AntiCheatService(this, whitelist, language);
        getServer().getPluginManager().registerEvents(antiCheat, this);
        antiCheat.start();

        AntiCheatCommand commands = new AntiCheatCommand(this, whitelist, antiCheat, language);
        getCommand("acwhitelist").setExecutor(commands);
        getCommand("acwhitelist").setTabCompleter(commands);
        getCommand("silentac").setExecutor(commands);

        InfiniteVillagerService infiniteVillagers = new InfiniteVillagerService(this, language);
        getServer().getPluginManager().registerEvents(infiniteVillagers, this);
        getCommand("infvillager").setExecutor(infiniteVillagers);
        getCommand("infvillager").setTabCompleter(infiniteVillagers);
        infiniteVillagers.start();

        PaperFixService paperFixes = new PaperFixService(this, language);
        getCommand("paperfix").setExecutor(paperFixes);
        getCommand("paperfix").setTabCompleter(paperFixes);

        getLogger().info("SMP Booster 已启用。");
    }

    @Override
    public void onDisable() {
        if (antiCheat != null) {
            antiCheat.close();
        }
    }
}
