package ru.maylan;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AfkPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();

    // когда игрок вошёл в AFK (для плейсхолдера afk_seconds)
    private final Map<UUID, Long> afkSince = new ConcurrentHashMap<>();

    private long timeoutMs;
    private int checkIntervalTicks;

    // legacy-визуалы (лучше выключить, если используете TAB)
    private boolean showInTab;
    private boolean showAboveName;

    private boolean bcEnter;
    private boolean bcExit;
    private String teamName;
    private String prefixText;

    // тексты для PlaceholderAPI
    private String placeholderSuffix; // например " &7[AFK]"
    private String placeholderTag;    // например "&7[AFK]"

    private Scoreboard board;
    private Team afkTeam;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        // events + команда
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("afk") != null) {
            getCommand("afk").setExecutor(new AfkToggleCommand(this));
        }

        // Scoreboard team — только если реально нужен legacy-режим над ником
        if (showAboveName) {
            board = Bukkit.getScoreboardManager().getMainScoreboard();
            afkTeam = board.getTeam(teamName);
            if (afkTeam == null) {
                afkTeam = board.registerNewTeam(teamName);
            }
            afkTeam.prefix(Component.text(prefixText).color(NamedTextColor.GRAY));
        }

        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            lastActivity.put(p.getUniqueId(), now);
            clearAfkVisuals(p);
        }

        Bukkit.getScheduler().runTaskTimer(this, this::tickCheck, checkIntervalTicks, checkIntervalTicks);

        // Register PlaceholderAPI expansion (если PAPI установлен)
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MaylanAfkExpansion(this).register();
            getLogger().info("PlaceholderAPI найден — expansion maylanafk зарегистрирована.");
        } else {
            getLogger().info("PlaceholderAPI не найден — плейсхолдеры недоступны.");
        }

        getLogger().info("maylanAFK включён.");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            clearAfkVisuals(p);
        }
        afkPlayers.clear();
        lastActivity.clear();
        afkSince.clear();
    }

    private void loadConfigValues() {
        this.timeoutMs = getConfig().getLong("afk-timeout-seconds", 300) * 1000L;
        this.checkIntervalTicks = getConfig().getInt("check-interval-ticks", 100);

        this.teamName = getConfig().getString("team-name", "AFK");
        this.prefixText = getConfig().getString("prefix-text", "[AFK] ");

        this.showInTab = getConfig().getBoolean("show.in-tab", false);
        this.showAboveName = getConfig().getBoolean("show.above-name", false);

        this.bcEnter = getConfig().getBoolean("broadcast.on-enter", true);
        this.bcExit = getConfig().getBoolean("broadcast.on-exit", true);

        this.placeholderSuffix = getConfig().getString("placeholders.suffix", " &7[AFK]");
        this.placeholderTag = getConfig().getString("placeholders.tag", "&7[AFK]");
    }

    // -------- Ивенты активности --------
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        lastActivity.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        clearAfkVisuals(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastActivity.remove(id);
        afkPlayers.remove(id);
        afkSince.remove(id);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (e.getFrom().getX() != e.getTo().getX()
                || e.getFrom().getY() != e.getTo().getY()
                || e.getFrom().getZ() != e.getTo().getZ()) {
            touch(e.getPlayer());
        }
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) { touch(e.getPlayer()); }

    // ВАЖНО: AsyncChatEvent — перекидываем на главный поток
    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Bukkit.getScheduler().runTask(this, () -> touch(e.getPlayer()));
    }

    @EventHandler public void onCommand(PlayerCommandPreprocessEvent e) { touch(e.getPlayer()); }
    @EventHandler public void onInvClick(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p) touch(p); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { touch(e.getPlayer()); }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) { touch(e.getPlayer()); }
    @EventHandler public void onSneak(PlayerToggleSneakEvent e) { touch(e.getPlayer()); }
    @EventHandler public void onSprint(PlayerToggleSprintEvent e) { touch(e.getPlayer()); }

    // -------- Логика --------
    private void tickCheck() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            long last = lastActivity.getOrDefault(id, now);
            if (!afkPlayers.contains(id) && now - last >= timeoutMs) {
                setAfk(p, true, false);
            }
        }
    }

    private void touch(Player p) {
        lastActivity.put(p.getUniqueId(), System.currentTimeMillis());
        if (afkPlayers.contains(p.getUniqueId())) {
            setAfk(p, false, true);
        }
    }

    // --- API для плейсхолдеров (важно: без Bukkit-операций) ---
    public boolean isAfk(UUID id) {
        return afkPlayers.contains(id);
    }

    public long getInactiveSeconds(UUID id) {
        long now = System.currentTimeMillis();
        long last = lastActivity.getOrDefault(id, now);
        return Math.max(0, (now - last) / 1000L);
    }

    public long getAfkSeconds(UUID id) {
        if (!isAfk(id)) return 0;
        long now = System.currentTimeMillis();
        long since = afkSince.getOrDefault(id, now);
        return Math.max(0, (now - since) / 1000L);
    }

    public String getPlaceholderSuffix() {
        return placeholderSuffix;
    }

    public String getPlaceholderTag() {
        return placeholderTag;
    }

    private void setAfk(Player p, boolean afk, boolean manualUnAfkBroadcast) {
        UUID id = p.getUniqueId();

        if (afk) {
            if (!afkPlayers.add(id)) return;

            afkSince.put(id, System.currentTimeMillis());
            applyAfkVisuals(p);

            if (bcEnter) {
                Bukkit.broadcast(Component.text("Игрок " + p.getName() + " теперь AFK.")
                        .color(NamedTextColor.GRAY));
            }
            p.sendActionBar(Component.text("Вы AFK").color(NamedTextColor.GRAY));

        } else {
            if (!afkPlayers.remove(id)) return;

            afkSince.remove(id);
            clearAfkVisuals(p);

            if (manualUnAfkBroadcast && bcExit) {
                Bukkit.broadcast(Component.text("Игрок " + p.getName() + " вернулся.")
                        .color(NamedTextColor.GRAY));
            }
            p.sendActionBar(Component.text("Вы больше не AFK").color(NamedTextColor.GRAY));
        }
    }

    private void applyAfkVisuals(Player p) {
        if (showAboveName && afkTeam != null) {
            if (!afkTeam.hasEntry(p.getName())) {
                afkTeam.addEntry(p.getName());
            }
        }
        if (showInTab) {
            p.playerListName(Component.text(prefixText).color(NamedTextColor.GRAY).append(p.name()));
        }
    }

    private void clearAfkVisuals(Player p) {
        if (showAboveName && afkTeam != null) {
            if (afkTeam.hasEntry(p.getName())) {
                afkTeam.removeEntry(p.getName());
            }
        }
        if (showInTab) {
            p.playerListName(null);
        }
    }

    public void toggleAfk(Player p) {
        setAfk(p, !isAfk(p.getUniqueId()), true);
        if (!isAfk(p.getUniqueId())) {
            lastActivity.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }
}