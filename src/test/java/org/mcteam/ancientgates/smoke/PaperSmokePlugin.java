package org.mcteam.ancientgates.smoke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcteam.ancientgates.Conf;
import org.mcteam.ancientgates.Gate;
import org.mcteam.ancientgates.Gates;
import org.mcteam.ancientgates.Plugin;
import org.mcteam.ancientgates.listeners.PluginMovementListener;
import org.mcteam.ancientgates.util.EntityUtil;
import org.mcteam.ancientgates.util.ItemStackUtil;
import org.mcteam.ancientgates.util.TeleportUtil;
import org.mcteam.ancientgates.util.types.FloodOrientation;
import org.mcteam.ancientgates.util.types.GateMaterial;
import org.mcteam.ancientgates.util.types.InvBoolean;
import org.mcteam.ancientgates.util.types.PluginMessage;
import org.mcteam.ancientgates.util.types.WorldCoord;

import net.kyori.adventure.text.Component;
import org.bukkit.util.Vector;

/** Runs only when explicitly installed on a disposable server. Never ship this test JAR. */
public final class PaperSmokePlugin extends JavaPlugin {

    private int assertions;
    private int failures;
    private int gateOffset;
    private final List<Entity> entities = new ArrayList<>();
    private World world;
    private final NamespacedKey metadata = new NamespacedKey("other_plugin", "identity");

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::runTests, 20);
    }

    private void runTests() {
        world = Bukkit.getWorlds().getFirst();
        // Real players/entities enter gates in loaded chunks; load test spawn areas first.
        for (int z = 1; z <= 4; z++) world.getChunkAt(0, z).load();
        runCase("plugin descriptor", () -> {
            check(Plugin.instance.isEnabled(), "AncientGates enabled");
            check("26.2".equals(Plugin.instance.getDescription().getAPIVersion()), "Paper 26.2 minimum API");
            check(Runtime.version().feature() == 25, "test runtime is Java 25");
        });
        runCase("restart persistence", () -> {
            Gate persisted = Gate.get("smoke-persisted");
            if (Boolean.getBoolean("ancientgates.smoke.restart")) {
                check(persisted != null, "gate survived restart");
                check(persisted.getTo().getBlockX() == 71, "destination survived restart");
                check(Gates.gateFromPortal(new WorldCoord(persisted.getFroms().getFirst())) == persisted,
                        "open gate index restored after restart");
            }
        });
        for (GateMaterial material : GateMaterial.values()) {
            runCase("material " + material, () -> gate(material, FloodOrientation.VERTICAL1, false));
        }
        Conf.useDiagonalPortals = true;
        for (FloodOrientation orientation : FloodOrientation.values()) {
            runCase("orientation " + orientation, () -> gate(GateMaterial.PORTAL, orientation, false));
        }
        runCase("item round trip", this::items);
        runCase("oversized item transfer", this::oversizedTransfer);
        runCase("local dropped item", this::droppedItem);
        runCase("local storage vehicle", this::storageVehicle);
        runCase("vehicle passengers", this::passengers);
        runCase("cancelled vehicle teleport", this::cancelledVehicle);
        runCase("vehicle cargo drop", this::cargoDrop);
        runCase("all vehicle riders authorized", this::riderPermissions);
        runCase("legacy entity data", this::entityData);
        runCase("save for restart", () -> {
            Gate previous = Gate.get("smoke-persisted");
            if (previous != null) {
                Gates.close(previous);
                Gate.delete(previous.getId());
            }
            Gate persisted = gate(GateMaterial.PORTAL, FloodOrientation.VERTICAL1, true);
            persisted.rename(persisted.getId(), "smoke-persisted");
            check(Gate.save(), "gate save accepted");
        });
        entities.forEach(entity -> { if (entity.isValid()) entity.remove(); });
        getLogger().info("ANCIENTGATES_SMOKE_RESULT assertions=" + assertions + " failures=" + failures);
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20);
    }

    private Gate gate(GateMaterial material, FloodOrientation orientation, boolean keep) {
        int origin = gateOffset++ * 8;
        int dx = orientation == FloodOrientation.VERTICAL1 ? 0 : 1;
        int dz = orientation == FloodOrientation.VERTICAL1 || orientation == FloodOrientation.VERTICAL4 ? 1
                : orientation == FloodOrientation.VERTICAL3 ? -1 : 0;
        boolean horizontal = orientation == FloodOrientation.HORIZONTAL;
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 5; b++) {
                Block block = world.getBlockAt(origin + a * dx, 100 + (horizontal ? 0 : b),
                        a * dz + (horizontal ? b : 0));
                block.setType(a == 0 || a == 3 || b == 0 || b == 4 ? Material.OBSIDIAN : Material.CAVE_AIR, false);
            }
        }
        Location from = new Location(world, origin + dx, 100 + (horizontal ? 0 : 1), dz + (horizontal ? 1 : 0));
        Gate gate = Gate.create("smoke-" + gateOffset);
        gate.setGateMaterial(material);
        gate.addFrom(from);
        gate.addTo(new Location(world, 71, 101, 31));
        check(Gates.open(gate), "gate opens " + material + "/" + orientation);
        check(gate.getPortalBlocks().size() == 6, "six portal blocks");
        check(Gates.gateFromPortal(new WorldCoord(from)) == gate, "gate indexed");
        for (WorldCoord coord : gate.getPortalBlocks().keySet()) {
            check(coord.getBlock().getType() == gate.getGateMaterial().getMaterial(), "portal block material");
            if (material == GateMaterial.PORTAL && orientation == FloodOrientation.VERTICAL1) {
                check(((Orientable) coord.getBlock().getBlockData()).getAxis() == Axis.Z, "portal axis Z");
            }
        }
        if (!keep) {
            Gates.close(gate);
            check(Gates.gateFromPortal(new WorldCoord(from)) == null, "closed gate unindexed");
            for (WorldCoord coord : gate.getPortalBlocks().keySet()) {
                check(coord.getBlock().getType().isAir(), "closed portal cleared");
            }
            Gate.delete(gate.getId());
        }
        return gate;
    }

    private ItemStack specialItem() {
        ItemStack item = new ItemStack(Material.DIAMOND, 7);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Metadata, with punctuation"));
        meta.lore(List.of(Component.text("Keep third-party data")));
        meta.getPersistentDataContainer().set(metadata, PersistentDataType.STRING, "preserve-me");
        item.setItemMeta(meta);
        return item;
    }

    private void items() {
        ItemStack[] contents = new ItemStack[27];
        contents[0] = specialItem();
        ItemStack box = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        ShulkerBox state = (ShulkerBox) meta.getBlockState();
        state.getInventory().setItem(26, specialItem());
        meta.setBlockState(state);
        box.setItemMeta(meta);
        contents[26] = box;
        ItemStack[] restored = ItemStackUtil.stringToItemStack(ItemStackUtil.itemStackToString(contents));
        check(restored.length == 27, "inventory length");
        check(contents[0].equals(restored[0]), "item metadata and amount preserved");
        check(restored[1] == null || restored[1].isEmpty(), "empty slot retained");
        check(contents[26].equals(restored[26]), "container and sparse slot retained");
        ItemStack[] legacy = ItemStackUtil.stringToItemStack("27;26#t@DIAMOND:a@3;");
        check(legacy[26] != null && legacy[26].getAmount() == 3, "legacy sparse slot retained");
    }

    private void droppedItem() {
        Item item = world.dropItem(new Location(world, 5, 110, 30), specialItem());
        entities.add(item);
        item.setGravity(false);
        item.setPickupDelay(999);
        item.getPersistentDataContainer().set(metadata, PersistentDataType.STRING, "entity-data");
        UUID uuid = item.getUniqueId();
        Location destination = new Location(world, 15, 110, 30);
        TeleportUtil.teleportEntity(new EntityPortalEvent(item, item.getLocation(), destination), destination);
        check(item.isValid() && uuid.equals(item.getUniqueId()), "original item entity retained");
        check(item.getLocation().distanceSquared(destination) < 0.01, "item teleported");
        check(item.getItemStack().equals(specialItem()), "dropped item data retained");
        check(item.getPickupDelay() == 999, "pickup delay retained");
    }

    private void oversizedTransfer() {
        ItemStack item = specialItem();
        ItemMeta meta = item.getItemMeta();
        byte[] large = new byte[70_000];
        new Random(25).nextBytes(large);
        meta.getPersistentDataContainer().set(metadata, PersistentDataType.BYTE_ARRAY, large);
        item.setItemMeta(meta);
        PluginMessage message = new PluginMessage(EntityType.CHEST_MINECART, 0,
                Map.of("server", "test", "world", "world", "x", "0", "y", "100", "z", "0", "yaw", "0", "pitch", "0"));
        message.addItemStack(new ItemStack[] {item});
        boolean rejected = false;
        try {
            message.toByteArray();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "oversized payload rejected before sending");
        check(item.getAmount() == 7, "source stack unchanged");
    }

    private void storageVehicle() {
        StorageMinecart cart = (StorageMinecart) world.spawnEntity(new Location(world, 5, 110, 40), EntityType.CHEST_MINECART);
        entities.add(cart);
        cart.setGravity(false);
        cart.customName(Component.text("Named cart"));
        cart.getPersistentDataContainer().set(metadata, PersistentDataType.STRING, "cart-data");
        cart.getInventory().setItem(26, specialItem());
        Location destination = new Location(world, 15, 110, 40);
        check(cart.isValid(), "cart valid before teleport");
        TeleportUtil.teleportVehicle(cart, destination, true, InvBoolean.TRUE);
        check(cart.isValid(), "original cart retained");
        check(cart.getLocation().distanceSquared(destination.clone().add(0, 0.5, 0)) < 0.01, "cart teleported");
        check(cart.getInventory().getItem(26).equals(specialItem()), "cart inventory retained");
        check("cart-data".equals(cart.getPersistentDataContainer().get(metadata, PersistentDataType.STRING)), "cart PDC retained");
    }

    private void passengers() {
        Vehicle boat = (Vehicle) world.spawnEntity(new Location(world, 5, 110, 50), EntityType.OAK_BOAT);
        LivingEntity first = (LivingEntity) world.spawnEntity(new Location(world, 5, 110, 50), EntityType.COW);
        LivingEntity second = (LivingEntity) world.spawnEntity(new Location(world, 5, 110, 50), EntityType.SHEEP);
        entities.addAll(List.of(boat, first, second));
        first.setAI(false);
        second.setAI(false);
        check(boat.isValid(), "boat valid before teleport");
        check(boat.addPassenger(first) && boat.addPassenger(second), "two passengers mounted");
        TeleportUtil.teleportVehicle(boat, new Location(world, 15, 110, 50), true, InvBoolean.TRUE);
        check(boat.isValid(), "original boat retained");
        check(boat.getPassengers().size() == 2, "both passengers retained");
        World nether = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NETHER).findFirst().orElseThrow();
        Location acrossWorlds = new Location(nether, 5, 100, 5);
        acrossWorlds.getChunk().load();
        UUID boatId = boat.getUniqueId();
        UUID firstId = first.getUniqueId();
        UUID secondId = second.getUniqueId();
        TeleportUtil.teleportVehicle(boat, acrossWorlds, true, InvBoolean.TRUE);
        Entity arrived = Bukkit.getEntity(boatId);
        check(arrived != null && arrived.getWorld().equals(nether), "boat moved across worlds with UUID intact");
        check(arrived.getPassengers().stream().map(Entity::getUniqueId).toList().containsAll(List.of(firstId, secondId)),
                "both passenger UUIDs retained across worlds");
        entities.add(arrived);
        entities.addAll(arrived.getPassengers());
    }

    private void cancelledVehicle() {
        StorageMinecart cart = (StorageMinecart) world.spawnEntity(new Location(world, 5, 110, 40), EntityType.CHEST_MINECART);
        entities.add(cart);
        cart.getInventory().setItem(26, specialItem());
        Location origin = cart.getLocation();
        Listener cancel = new Listener() {
            @EventHandler
            public void onTeleport(EntityTeleportEvent event) {
                if (event.getEntity().getUniqueId().equals(cart.getUniqueId())) event.setCancelled(true);
            }
        };
        Bukkit.getPluginManager().registerEvents(cancel, this);
        try {
            TeleportUtil.teleportVehicle(cart, new Location(world, 15, 110, 40), false, InvBoolean.CLEAR);
            check(cart.isValid() && cart.getLocation().equals(origin), "cancelled vehicle stays at origin");
            check(cart.getInventory().getItem(26).equals(specialItem()), "cancelled vehicle keeps inventory");
        } finally {
            HandlerList.unregisterAll(cancel);
        }
    }

    private void cargoDrop() {
        StorageMinecart cart = (StorageMinecart) world.spawnEntity(new Location(world, 5, 110, 40), EntityType.CHEST_MINECART);
        entities.add(cart);
        cart.getInventory().setItem(26, specialItem());
        long before = world.getEntitiesByClass(Item.class).stream().filter(i -> i.getItemStack().equals(specialItem())).count();
        TeleportUtil.teleportVehicle(cart, new Location(world, 15, 110, 40), false, InvBoolean.TRUE);
        check(cart.getInventory().isEmpty(), "cargo removed from teleported vehicle");
        long after = world.getEntitiesByClass(Item.class).stream().filter(i -> i.getItemStack().equals(specialItem())).count();
        check(after == before + 1, "cargo dropped exactly once");
    }

    private void entityData() {
        for (EntityType type : List.of(EntityType.SKELETON, EntityType.HORSE, EntityType.VILLAGER)) {
            Entity source = world.spawnEntity(new Location(world, 5, 110, 60), type);
            Entity target = world.spawnEntity(new Location(world, 8, 110, 60), type);
            entities.addAll(List.of(source, target));
            EntityUtil.setEntityTypeData(target, EntityUtil.getEntityTypeData(source));
            check(target.getType() == source.getType(), "entity data " + type);
        }
    }

    private void riderPermissions() {
        Gate gate = gate(GateMaterial.PORTAL, FloodOrientation.VERTICAL1, true);
        gate.setTeleportVehicles(true);
        Location origin = gate.getFroms().getFirst();
        AtomicBoolean secondAllowed = new AtomicBoolean(false);
        Player first = playerStub("SmokeJava", new AtomicBoolean(true));
        Player second = playerStub(".SmokeBedrock", secondAllowed);
        AtomicInteger teleports = new AtomicInteger();
        Vehicle vehicle = (Vehicle) Proxy.newProxyInstance(getClassLoader(), new Class<?>[] {Vehicle.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getPassengers" -> List.of(first, second);
                case "getLocation" -> origin.clone();
                case "getVelocity" -> new Vector();
                case "isValid" -> true;
                case "teleport" -> { teleports.incrementAndGet(); yield true; }
                case "setFireTicks", "setVelocity" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
        PluginMovementListener listener = new PluginMovementListener(Plugin.instance);
        VehicleMoveEvent move = new VehicleMoveEvent(vehicle, origin, origin);
        boolean oldAccess = Conf.enforceAccess;
        boolean oldEconomy = Conf.useEconomy;
        try {
            Conf.enforceAccess = true;
            Conf.useEconomy = false;
            listener.onVehicleMove(move);
            check(teleports.get() == 0, "unauthorized second rider blocks vehicle");
            secondAllowed.set(true);
            Conf.useEconomy = true;
            gate.setCost(1.0);
            listener.onVehicleMove(move);
            check(teleports.get() == 0, "multiple paid riders rejected before charging");
            Conf.useEconomy = false;
            gate.setCost(0.0);
            listener.onVehicleMove(move);
            check(teleports.get() == 1, "authorized riders pass together");
            check(Plugin.lastTeleportTime.containsKey(first.getName()) && Plugin.lastTeleportTime.containsKey(second.getName()),
                    "every rider receives cooldown");
            listener.onVehicleMove(move);
            check(teleports.get() == 1, "cooldown prevents immediate repeat");
        } finally {
            Conf.enforceAccess = oldAccess;
            Conf.useEconomy = oldEconomy;
            Plugin.lastTeleportTime.remove(first.getName());
            Plugin.lastTeleportTime.remove(second.getName());
            Gates.close(gate);
            Gate.delete(gate.getId());
        }
    }

    private Player playerStub(String name, AtomicBoolean permitted) {
        return (Player) Proxy.newProxyInstance(getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getName" -> name;
            case "hasPermission" -> permitted.get();
            case "setFireTicks", "sendMessage" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private void runCase(String name, Runnable test) {
        try {
            test.run();
            getLogger().info("PASS " + name);
        } catch (Throwable failure) {
            failures++;
            getLogger().log(Level.SEVERE, "FAIL " + name, failure);
        }
    }
}
