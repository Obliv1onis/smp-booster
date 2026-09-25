package com.smpBooster.restrictions;

import com.smpBooster.SmpBooster;
import com.smpBooster.i18n.LanguageService;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class RestrictionService implements Listener {
    private final SmpBooster plugin;
    private final LanguageService language;

    public RestrictionService(SmpBooster plugin, LanguageService language) {
        this.plugin = plugin;
        this.language = language;
    }

    public void startInventoryScanner() {
        long period = Math.max(20L, plugin.getConfig().getLong("restrictions.scan-period-ticks", 40L));
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                sanitizeInventory(player.getInventory());
                sanitize(player.getItemOnCursor());
            }
        }, 1L, period);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Material type = event.getMaterial();
        if (type == Material.ENDER_PEARL && plugin.getConfig().getBoolean("restrictions.items.disable-ender-pearl-teleport", false)) {
            event.setCancelled(true);
            message(event.getPlayer(), language.text("ender-pearl-disabled"));
        }
        sanitize(event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.CHORUS_FRUIT
                && plugin.getConfig().getBoolean("restrictions.items.disable-chorus-fruit-teleport", false)) {
            event.setCancelled(true);
            message(event.getPlayer(), language.text("chorus-fruit-disabled"));
            return;
        }
        sanitize(event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageByEntityEvent event) {
        String path = null;
        if (event.getDamager() instanceof ExplosiveMinecart) path = "tnt-minecart";
        else if (event.getDamager() instanceof EnderCrystal) path = "end-crystal";
        else if (event.getDamager() instanceof TNTPrimed) path = "tnt";
        if (path == null) return;
        double multiplier = Math.max(0.0, plugin.getConfig().getDouble("restrictions.explosion-damage." + path + "-multiplier", 1.0));
        event.setDamage(event.getDamage() * multiplier);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        sanitize(event.getItem().getItemStack());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        sanitize(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            sanitize(event.getCurrentItem());
            sanitize(event.getCursor());
            sanitizeInventory(event.getInventory());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> sanitizeInventory(event.getInventory()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        sanitize(event.getResult());
        event.setResult(event.getResult());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent event) {
        sanitize(event.getResult());
        event.setResult(event.getResult());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof InventoryHolder holder) sanitizeInventory(holder.getInventory());
            }
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Item item) sanitize(item.getItemStack());
            }
        });
    }

    private void sanitizeInventory(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) sanitize(item);
    }

    private boolean sanitize(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (mustRemoveItem(meta)) {
            item.setAmount(0);
            return true;
        }
        boolean changed = sanitizeEnchantments(meta);
        if (meta instanceof EnchantmentStorageMeta stored) changed |= sanitizeStoredEnchantments(stored);
        if (meta instanceof PotionMeta potion) changed |= sanitizePotion(potion);
        if (changed) item.setItemMeta(meta);
        return changed;
    }

    private boolean mustRemoveItem(ItemMeta meta) {
        for (Enchantment enchantment : meta.getEnchants().keySet()) {
            if (removeItemFor(enchantment)) return true;
        }
        if (meta instanceof EnchantmentStorageMeta stored) {
            for (Enchantment enchantment : stored.getStoredEnchants().keySet()) {
                if (removeItemFor(enchantment)) return true;
            }
        }
        return false;
    }

    private boolean removeItemFor(Enchantment enchantment) {
        String base = "restrictions.enchantments." + enchantment.getKey();
        return plugin.getConfig().getBoolean(base + ".disabled", false)
                && plugin.getConfig().getBoolean(base + ".remove-item", false);
    }

    private boolean sanitizeEnchantments(ItemMeta meta) {
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : new HashMap<>(meta.getEnchants()).entrySet()) {
            int allowed = allowedEnchantmentLevel(entry.getKey());
            if (allowed < entry.getValue()) {
                meta.removeEnchant(entry.getKey());
                if (allowed > 0) meta.addEnchant(entry.getKey(), allowed, true);
                changed = true;
            }
        }
        return changed;
    }

    private boolean sanitizeStoredEnchantments(EnchantmentStorageMeta meta) {
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : new HashMap<>(meta.getStoredEnchants()).entrySet()) {
            int allowed = allowedEnchantmentLevel(entry.getKey());
            if (allowed < entry.getValue()) {
                meta.removeStoredEnchant(entry.getKey());
                if (allowed > 0) meta.addStoredEnchant(entry.getKey(), allowed, true);
                changed = true;
            }
        }
        return changed;
    }

    private int allowedEnchantmentLevel(Enchantment enchantment) {
        String key = enchantment.getKey().toString();
        String base = "restrictions.enchantments." + key;
        if (plugin.getConfig().getBoolean(base + ".disabled", false)) return 0;
        int blockedFrom = plugin.getConfig().getInt(base + ".disabled-from-level", Integer.MAX_VALUE);
        return blockedFrom == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, blockedFrom - 1);
    }

    private boolean sanitizePotion(PotionMeta meta) {
        boolean changed = false;
        for (PotionEffect effect : new ArrayList<>(meta.getCustomEffects())) {
            int allowed = allowedPotionLevel(effect.getType());
            int level = effect.getAmplifier() + 1;
            if (allowed < level) {
                meta.removeCustomEffect(effect.getType());
                if (allowed > 0) {
                    meta.addCustomEffect(new PotionEffect(effect.getType(), effect.getDuration(), allowed - 1,
                            effect.isAmbient(), effect.hasParticles(), effect.hasIcon()), true);
                }
                changed = true;
            }
        }

        PotionType base = meta.getBasePotionType();
        if (base != null) {
            for (PotionEffect effect : base.getPotionEffects()) {
                int allowed = allowedPotionLevel(effect.getType());
                if (allowed < effect.getAmplifier() + 1) {
                    String name = base.name();
                    if (allowed == 1 && name.startsWith("STRONG_")) {
                        try {
                            meta.setBasePotionType(PotionType.valueOf(name.substring("STRONG_".length())));
                        } catch (IllegalArgumentException ignored) {
                            meta.setBasePotionType(PotionType.WATER);
                        }
                    } else if (allowed <= 0) {
                        meta.setBasePotionType(PotionType.WATER);
                    }
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    private int allowedPotionLevel(PotionEffectType effect) {
        String key = effect.getKey().toString();
        String base = "restrictions.potion-effects." + key;
        if (plugin.getConfig().getBoolean(base + ".disabled", false)) return 0;
        int blockedFrom = plugin.getConfig().getInt(base + ".disabled-from-level", Integer.MAX_VALUE);
        return blockedFrom == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, blockedFrom - 1);
    }

    private void message(Player player, String fallback) {
        String prefix = plugin.getConfig().getString("messages.prefix", "§c[SMP Booster] §f");
        player.sendMessage(prefix + fallback);
    }
}
