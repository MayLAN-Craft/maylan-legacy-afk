package ru.maylan;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AfkToggleCommand implements CommandExecutor {
    private final AfkPlugin plugin;

    public AfkToggleCommand(AfkPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Эту команду можно использовать только в игре.");
            return true;
        }
        if (!p.hasPermission("maylanafk.afk")) {
            p.sendMessage("У вас нет прав.");
            return true;
        }
        plugin.toggleAfk(p);
        return true;
    }
}