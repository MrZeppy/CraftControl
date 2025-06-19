package me.zeppytest.craftControl;

import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.Set;
import java.util.stream.Collectors;

public final class CraftControl extends JavaPlugin implements Listener, CommandExecutor {
    private Set<Material> blockedRecipes;
    private Set<Enchantment> blockedEnchantments;

    public Set<Material> getBlockedRecipes() {
        return blockedRecipes;
    }

    public Set<Enchantment> getBlockedEnchantments() {
        return blockedEnchantments;
    }

    private String prettifyMaterialName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder prettyName = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                prettyName.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return prettyName.toString().trim();
    }

    private String prettifyEnchantmentName(Enchantment enchantment) {
        String name = enchantment.getKey().getKey().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder prettyName = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                prettyName.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return prettyName.toString().trim();
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You must be an operator to use this command.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            // List blocked recipes
            if (blockedRecipes.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No items are currently blocked.");
            } else {
                sender.sendMessage(ChatColor.DARK_RED + "Blocked crafting items:");
                sender.sendMessage(ChatColor.WHITE + blockedRecipes.stream()
                        .map(this::prettifyMaterialName)
                        .collect(Collectors.joining(", ")));
            }

            // List blocked enchantments
            if (blockedEnchantments.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No enchantments are currently blocked.");
            } else {
                sender.sendMessage(ChatColor.DARK_RED + "Blocked enchantments:");
                sender.sendMessage(ChatColor.WHITE + blockedEnchantments.stream()
                        .map(this::prettifyEnchantmentName)
                        .collect(Collectors.joining(", ")));
            }
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /craftcontrol list");
        return true;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadBlockedRecipes();
        loadBlockedEnchantments();
        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("craftcontrol").setExecutor(this);
    }

    private void loadBlockedRecipes() {
        blockedRecipes = getConfig().getStringList("blocked-recipes").stream()
                .map(String::toUpperCase)
                .map(Material::valueOf)
                .collect(Collectors.toSet());
    }

    private void loadBlockedEnchantments() {
        blockedEnchantments = getConfig().getStringList("blocked-enchantments").stream()
                .map(String::toUpperCase)
                .map(enchName -> Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchName.toLowerCase())))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        Material resultType = event.getRecipe().getResult().getType();
        if (blockedRecipes.contains(resultType)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(ChatColor.RED + "Crafting " + prettifyMaterialName(resultType) + " is disabled!");
            }
        }
    }

    @EventHandler
    public void onAutoCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null || event.getRecipe().getResult() == null) return;

        Material resultType = event.getRecipe().getResult().getType();
        if (blockedRecipes.contains(resultType)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler
    public void onSmithItem(SmithItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && blockedRecipes.contains(result.getType())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(ChatColor.RED + "Upgrading to " + prettifyMaterialName(result.getType()) + " is disabled!");
            }
        }
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        // Check if any of the enchantments being added are blocked
        for (Enchantment enchantment : event.getEnchantsToAdd().keySet()) {
            if (blockedEnchantments.contains(enchantment)) {
                event.setCancelled(true);
                event.getEnchanter().sendMessage(ChatColor.RED + "The enchantment " +
                        prettifyEnchantmentName(enchantment) + " is disabled!");
                return;
            }
        }
    }

    @EventHandler
    public void onSmithItemEnchant(SmithItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;

        // Check if the result has any blocked enchantments
        if (result.hasItemMeta()) {
            // Check regular enchantments
            if (result.getItemMeta().hasEnchants()) {
                for (Enchantment enchantment : result.getItemMeta().getEnchants().keySet()) {
                    if (blockedEnchantments.contains(enchantment)) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player player) {
                            player.sendMessage(ChatColor.RED + "Cannot create items with the enchantment " +
                                    prettifyEnchantmentName(enchantment) + " as it is disabled!");
                        }
                        return;
                    }
                }
            }

            // Check stored enchantments (for enchanted books)
            if (result.getItemMeta() instanceof EnchantmentStorageMeta storageMeta) {
                if (storageMeta.hasStoredEnchants()) {
                    for (Enchantment enchantment : storageMeta.getStoredEnchants().keySet()) {
                        if (blockedEnchantments.contains(enchantment)) {
                            event.setCancelled(true);
                            if (event.getWhoClicked() instanceof Player player) {
                                player.sendMessage(ChatColor.RED + "Cannot create items with the enchantment " +
                                        prettifyEnchantmentName(enchantment) + " as it is disabled!");
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic if needed
    }
}
