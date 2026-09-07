package camchua.phoban.phobanpro.utils;

import camchua.phoban.phobanpro.game.Game;
import camchua.phoban.phobanpro.game.PlayerData;
import camchua.phoban.phobanpro.gui.PhoBanGui;
import camchua.phoban.phobanpro.manager.FileManager;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public class PhoBanExpansion extends PlaceholderExpansion {

    @Override public String getIdentifier() { return "phobanpro"; }
    @Override public String getAuthor() { return "NhanShiba"; }
    @Override public String getVersion() { return "1.4.6"; }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String identifier) {
        try {
            String[] args = identifier.split("_");
            Player player = offlinePlayer.getPlayer();

            return switch (args[0].toLowerCase()) {
                case "time" -> getPlayerGameValue(player, g -> PhoBanGui.timeFormat(g.getTimeLeft()));
                case "prefix" -> getPlayerGameValue(player, g -> g.getConfig().getString("Prefix", "").replace("&", "§"));
                case "maxplayers" -> getPlayerGameValue(player, g -> String.valueOf(g.getConfig().getInt("Player")));
                case "minplayers" -> getPlayerGameValue(player, g -> String.valueOf(g.getPlayers().size()));
                case "point" -> getPoint(offlinePlayer, args);
                case "rank" -> getRank(offlinePlayer, args);
                case "top" -> getTop(args);
                case "turn-left" -> getPlayerGameValue(player, g -> {
                    int left = Math.max(0, g.getTotalTurn() - g.getRealTurn());
                    return String.valueOf(left);
                });
                case "life" -> {
                    if (player == null || !PlayerData.contains(player)) yield "Not in game";
                    yield String.valueOf(PlayerData.get(player).getRespawn());
                }
                default -> handleRoomPlaceholder(args);
            };
        } catch (Exception ex) {
            return "PhoBan error: " + ex.getMessage();
        }
    }

    private String getPlayerGameValue(Player player, java.util.function.Function<Game, String> fn) {
        if (player == null || !PlayerData.contains(player)) return "Not in game";
        return fn.apply(PlayerData.get(player).getGame());
    }

    private String getPoint(OfflinePlayer p, String[] args) {
        if (args.length > 1) {
            String targetName = joinArgs(args, 1);
            Player target = org.bukkit.Bukkit.getPlayer(targetName);
            if (target != null) {
                return String.valueOf(FileManager.getFileConfig(FileManager.Files.DATA).getInt(target.getUniqueId() + ".Point", 0));
            }
            return "0";
        }
        return String.valueOf(FileManager.getFileConfig(FileManager.Files.DATA).getInt(p.getUniqueId() + ".Point", 0));
    }

    private String getRank(OfflinePlayer p, String[] args) {
        String targetUuid;
        if (args.length > 1) {
            String targetName = joinArgs(args, 1);
            Player target = org.bukkit.Bukkit.getPlayer(targetName);
            if (target == null) return "0";
            targetUuid = target.getUniqueId().toString();
        } else {
            targetUuid = p.getUniqueId().toString();
        }
        FileConfiguration data = FileManager.getFileConfig(FileManager.Files.DATA);
        if (!data.contains(targetUuid) || data.getInt(targetUuid + ".Point", 0) < 1) return "0";
        List<Map.Entry<String, Integer>> sorted = getSortedPoints(data);
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getKey().equals(targetUuid)) return String.valueOf(i + 1);
        }
        return "0";
    }

    private String getTop(String[] args) {
        if (args.length < 2) return "";
        int position;
        try {
            position = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            return "";
        }
        if (position < 1) return "";
        FileConfiguration data = FileManager.getFileConfig(FileManager.Files.DATA);
        List<Map.Entry<String, Integer>> sorted = getSortedPoints(data);
        if (position > sorted.size()) return "";
        var entry = sorted.get(position - 1);
        String playerName = data.getString(entry.getKey() + ".Name", entry.getKey());
        if (args.length > 2) {
            switch (args[2].toLowerCase()) {
                case "name":
                    return playerName;
                case "point":
                case "score":
                    return String.valueOf(entry.getValue());
                case "uuid":
                    return entry.getKey();
                default:
                    break;
            }
            return args[2].replace("&", "§").replace("<pos>", String.valueOf(position))
                    .replace("<player>", playerName).replace("<point>", String.valueOf(entry.getValue()));
        }
        return playerName + ": " + entry.getValue();
    }

    private String handleRoomPlaceholder(String[] args) {
        Game game = Game.getGame(args[0]);
        if (game == null) return "Game not found";
        if (args.length <= 1) return "";
        return switch (args[1].toLowerCase()) {
            case "time" -> PhoBanGui.timeFormat(game.getTimeLeft());
            case "prefix" -> game.getConfig().getString("Prefix", "").replace("&", "§");
            case "maxplayers" -> String.valueOf(game.getConfig().getInt("Player"));
            case "minplayers" -> String.valueOf(game.getPlayers().size());
            case "status" -> FileManager.getFileConfig(FileManager.Files.GUI)
                    .getString("PhoBanGui.StatusFormat." + game.getStatus(), "").replace("&", "§");
            case "turn-left" -> String.valueOf(Math.max(0, game.getTotalTurn() - game.getRealTurn()));
            default -> "";
        };
    }

    private List<Map.Entry<String, Integer>> getSortedPoints(FileConfiguration data) {
        Map<String, Integer> points = new HashMap<>();
        for (String name : data.getKeys(false)) {
            int pt = data.getInt(name + ".Point", 0);
            if (pt > 0) points.put(name, pt);
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(points.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return list;
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append("_");
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
