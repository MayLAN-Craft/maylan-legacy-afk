package ru.maylan;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class MaylanAfkExpansion extends PlaceholderExpansion {

    private final AfkPlugin plugin;

    public MaylanAfkExpansion(AfkPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        // ВАЖНО: идентификатор не должен содержать '_' по правилам PAPI
        return "maylanafk";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        // чтобы expansion не выгружалась на /papi reload
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        UUID id = player.getUniqueId();
        boolean afk = plugin.isAfk(id);

        return switch (params.toLowerCase()) {
            case "afk" -> String.valueOf(afk); // "true"/"false"

            // Готовый текст для TAB suffix: либо "&7[AFK]" (с пробелом), либо ""
            case "suffix" -> afk ? plugin.getPlaceholderSuffix() : "";

            // Иногда удобно отдельно "тег" без пробела
            case "tag" -> afk ? plugin.getPlaceholderTag() : "";

            case "inactive_seconds" -> String.valueOf(plugin.getInactiveSeconds(id));
            case "afk_seconds" -> String.valueOf(plugin.getAfkSeconds(id));

            case "inactive_mmss" -> formatMmSs(plugin.getInactiveSeconds(id));
            case "afk_mmss" -> formatMmSs(plugin.getAfkSeconds(id));

            default -> null; // неизвестный плейсхолдер
        };
    }

    private String formatMmSs(long seconds) {
        if (seconds < 0) seconds = 0;
        long m = seconds / 60;
        long s = seconds % 60;
        return m + ":" + (s < 10 ? "0" + s : s);
    }
}