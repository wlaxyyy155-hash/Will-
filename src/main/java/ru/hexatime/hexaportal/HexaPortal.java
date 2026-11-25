package ru.hexatime.hexaportal;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HexaPortal extends JavaPlugin implements Listener, TabExecutor {

    private static final List<String> KEYS = List.of("nether", "end");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("portalset");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    // ---------- Телепорт при входе через портал ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("hexaportal.bypass")) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        World dest = to.getWorld();
        if (dest == null) {
            return;
        }

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        String key;
        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && dest.getEnvironment() == World.Environment.NETHER) {
            key = "nether";
        } else if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL
                && dest.getEnvironment() == World.Environment.THE_END) {
            key = "end";
        } else {
            return;
        }

        Location target = load(key);
        if (target == null || target.getWorld() == null || !target.getWorld().equals(dest)) {
            return;
        }

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) {
                player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        });
    }

    // ---------- Хранение точек ----------

    private Location load(String key) {
        ConfigurationSection s = getConfig().getConfigurationSection("targets." + key);
        if (s == null) {
            return null;
        }
        World w = Bukkit.getWorld(s.getString("world", ""));
        if (w == null) {
            return null;
        }
        return new Location(w,
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private void save(String key, Location l) {
        String p = "targets." + key + ".";
        getConfig().set(p + "world", l.getWorld().getName());
        getConfig().set(p + "x", round(l.getX()));
        getConfig().set(p + "y", round(l.getY()));
        getConfig().set(p + "z", round(l.getZ()));
        getConfig().set(p + "yaw", round(l.getYaw()));
        getConfig().set(p + "pitch", round(l.getPitch()));
        saveConfig();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String describe(String key) {
        Location l = load(key);
        if (l == null) {
            return msg("not-set");
        }
        return l.getWorld().getName() + " " + (int) Math.floor(l.getX()) + " "
                + (int) Math.floor(l.getY()) + " " + (int) Math.floor(l.getZ());
    }

    // ---------- Команда /portalset ----------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hexaportal.admin")) {
            send(sender, msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            send(sender, msg("usage"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "nether", "end" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, msg("only-player"));
                    return true;
                }
                save(sub, player.getLocation());
                String text = msg(sub.equals("nether") ? "set-nether" : "set-end")
                        .replace("%loc%", describe(sub));
                send(sender, text);
            }
            case "remove" -> {
                if (args.length < 2 || !KEYS.contains(args[1].toLowerCase(Locale.ROOT))) {
                    send(sender, msg("usage"));
                    return true;
                }
                String key = args[1].toLowerCase(Locale.ROOT);
                getConfig().set("targets." + key, null);
                saveConfig();
                send(sender, msg("removed").replace("%name%", key));
            }
            case "info" -> {
                send(sender, "&eНезер: &f" + describe("nether"));
                send(sender, "&eЭнд: &f" + describe("end"));
            }
            case "reload" -> {
                reloadConfig();
                send(sender, msg("reloaded"));
            }
            default -> send(sender, msg("usage"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("hexaportal.admin")) {
            return out;
        }
        if (args.length == 1) {
            for (String s : List.of("nether", "end", "remove", "info", "reload")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            for (String s : KEYS) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    // ---------- Сообщения ----------

    private String msg(String key) {
        return getConfig().getString("messages." + key, key);
    }

    private void send(CommandSender to, String legacyText) {
        to.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText));
    }
}
