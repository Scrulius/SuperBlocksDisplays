package com.blockdisplay.plugin.furniture;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only paginated GUI of the viewer's placed furniture (/furniture): one icon per placement
 * (the real MythicMobs item, so the player sees the furniture itself), lore with type, world,
 * coordinates and distance, and click = remote pickup (FurnitureManager.pickupRemote loads the
 * chunk and reuses the normal pickup path: ownership, item back, sounds).
 *
 * Anti-dupe: every click/drag touching the top inventory is cancelled — the icons are real
 * items, nothing may ever leave the GUI except through pickupRemote.
 */
public class FurnitureGui implements Listener {

    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;            // slots 0-44
    private static final int SLOT_PREV = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;

    private final FurnitureManager manager;

    public FurnitureGui(FurnitureManager manager) {
        this.manager = manager;
    }

    /** Marks the inventory as ours and maps each page slot back to its furniture instance. */
    private static class Holder implements InventoryHolder {
        Inventory inv;
        int page;
        final List<String> slotInstances = new ArrayList<>();  // index = slot 0..44

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public void open(Player player, int page) {
        List<Map.Entry<String, PlacementIndex.Placement>> entries = sortedEntries(player);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder();
        holder.page = page;
        Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text("Tus muebles ", NamedTextColor.DARK_GRAY)
                .append(Component.text("(" + entries.size() + ")", NamedTextColor.GOLD))
                .append(Component.text("  pág. " + (page + 1) + "/" + pages, NamedTextColor.GRAY)));
        holder.inv = inv;

        Location eye = player.getLocation();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = page * PAGE_SIZE + i;
            if (idx >= entries.size()) break;
            Map.Entry<String, PlacementIndex.Placement> e = entries.get(idx);
            inv.setItem(i, buildIcon(e.getValue(), eye));
            holder.slotInstances.add(e.getKey());
        }

        ItemStack filler = named(new ItemStack(Material.BLACK_STAINED_GLASS_PANE), Component.empty(), List.of());
        for (int slot = PAGE_SIZE; slot < SIZE; slot++) {
            inv.setItem(slot, filler);
        }
        if (page > 0) {
            inv.setItem(SLOT_PREV, named(new ItemStack(Material.ARROW),
                    Component.text("◀ Página anterior", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                    List.of()));
        }
        if (page < pages - 1) {
            inv.setItem(SLOT_NEXT, named(new ItemStack(Material.ARROW),
                    Component.text("Página siguiente ▶", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                    List.of()));
        }
        inv.setItem(SLOT_INFO, buildInfo(player, entries.size()));
        inv.setItem(SLOT_CLOSE, named(new ItemStack(Material.BARRIER),
                Component.text("Cerrar", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of()));

        player.openInventory(inv);
    }

    /** Player's world first (nearest first), then other worlds alphabetically, then by type. */
    private List<Map.Entry<String, PlacementIndex.Placement>> sortedEntries(Player player) {
        List<Map.Entry<String, PlacementIndex.Placement>> entries = new ArrayList<>(
                manager.getIndex().byOwnerWithIds(player.getUniqueId().toString()).entrySet());
        String myWorld = player.getWorld().getName();
        Location me = player.getLocation();
        entries.sort(Comparator
                .comparing((Map.Entry<String, PlacementIndex.Placement> e) -> !e.getValue().world().equals(myWorld))
                .thenComparing(e -> e.getValue().world())
                .thenComparingDouble(e -> {
                    PlacementIndex.Placement p = e.getValue();
                    if (!p.world().equals(myWorld)) return 0;
                    double dx = p.x() - me.getX(), dy = p.y() - me.getY(), dz = p.z() - me.getZ();
                    return dx * dx + dy * dy + dz * dz;
                })
                .thenComparing(e -> e.getValue().type()));
        return entries;
    }

    private ItemStack buildIcon(PlacementIndex.Placement p, Location viewer) {
        FurnitureType type = manager.getRegistry().byId(p.type());
        ItemStack icon = (type != null) ? manager.getMythic().getItem(type.mythicItem) : null;
        boolean orphan = (icon == null);
        if (orphan) {
            icon = new ItemStack(Material.BARRIER);
        }

        // Title: the MythicMobs item's own display name IS the furniture's name; keep it.
        // Orphans (type removed from the catalog / MM item gone) get a fallback title.
        Component title = null;
        ItemMeta meta = icon.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            title = meta.displayName();
        }
        if (title == null) {
            title = Component.text(pretty(p.type()), orphan ? NamedTextColor.RED : NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(line("Tipo: ", p.type()));
        lore.add(line("Mundo: ", p.world()));
        lore.add(line("Coordenadas: ", String.format(Locale.ROOT, "%.0f, %.0f, %.0f", p.x(), p.y(), p.z())));
        if (p.world().equals(viewer.getWorld().getName())) {
            double dist = Math.sqrt(Math.pow(p.x() - viewer.getX(), 2)
                    + Math.pow(p.y() - viewer.getY(), 2) + Math.pow(p.z() - viewer.getZ(), 2));
            lore.add(line("Distancia: ", String.format(Locale.ROOT, "a %.0f bloques", dist)));
        } else {
            lore.add(line("Distancia: ", "en otro mundo"));
        }
        lore.add(Component.empty());
        if (orphan) {
            lore.add(Component.text("⚠ Mueble fuera del catálogo:", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("al recogerlo NO devuelve item.", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("▶ Clic para recogerlo", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" (va a tu inventario)", NamedTextColor.GRAY)));

        return named(icon, title, lore);
    }

    private ItemStack buildInfo(Player player, int placed) {
        int limit = manager.limitFor(player);
        String quota = (limit < 0) ? placed + " / ∞" : placed + " / " + limit;
        return named(new ItemStack(Material.BOOK),
                Component.text("Tus muebles", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                List.of(
                        line("Colocados: ", quota),
                        Component.empty(),
                        Component.text("Clic en un mueble para", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("recogerlo a distancia.", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)));
    }

    private static Component line(String label, String value) {
        return Component.text(label, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private static String pretty(String id) {
        String clean = id.replace('_', ' ').replace('-', ' ');
        return clean.isEmpty() ? id : Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private static ItemStack named(ItemStack item, Component name, List<Component> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== CLICK HANDLING ====================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);  // real items inside: nothing leaves this GUI by hand
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getClickedInventory() != e.getInventory()) return;

        int slot = e.getSlot();
        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            click(player);
        } else if (slot == SLOT_PREV && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) {
            click(player);
            open(player, holder.page - 1);
        } else if (slot == SLOT_NEXT && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) {
            click(player);
            open(player, holder.page + 1);
        } else if (slot < holder.slotInstances.size()) {
            // The callback fires when the index actually changed (the chunk may need a few
            // ticks to stream its entities in); re-render only if the GUI is still on screen.
            manager.pickupRemote(player, holder.slotInstances.get(slot), () -> {
                if (player.isOnline()
                        && player.getOpenInventory().getTopInventory().getHolder() instanceof Holder h) {
                    open(player, h.page);
                }
            });
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        for (int slot : e.getRawSlots()) {
            if (slot < SIZE) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), "ui.button.click", 0.5f, 1.0f);
    }
}
