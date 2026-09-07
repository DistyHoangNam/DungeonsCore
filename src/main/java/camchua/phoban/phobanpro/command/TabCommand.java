package camchua.phoban.phobanpro.command;

import camchua.phoban.phobanpro.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class TabCommand implements TabCompleter {

    private static final List<String> COMMON_SUB_COMMANDS = List.of(
            "join", "leave", "start", "top", "help"
    );

    private static final List<String> ADMIN_SUB_COMMANDS = List.of(
            "create", "edit", "check", "add", "remove", "setspawn", "addrewards",
            "givepoint", "takepoint", "list", "reload", "phongcho",
            "bosswait", "bossfight", "bossend", "kiemtraphongcho", "taiphongcho", "debug"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterByPrefix(getSubCommandSuggestions(sender), args[0]);
        }

        if (args.length == 2) {
            return handleSecondArgument(args);
        }

        if (args.length == 3) {
            return handleThirdArgument(args);
        }

        if (args.length == 4) {
            return handleFourthArgument(args);
        }

        return List.of();
    }

    private List<String> getSubCommandSuggestions(CommandSender sender) {
        List<String> result = new ArrayList<>(COMMON_SUB_COMMANDS);
        if (sender.hasPermission("phoban.admin")) {
            result.addAll(ADMIN_SUB_COMMANDS);
        }
        return result;
    }

    private List<String> handleSecondArgument(String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = switch (sub) {
            case "join" -> Game.listGame();
            case "edit", "addrewards", "bosswait", "check" -> Game.listGameWithoutCompleteSetup();
            case "create" -> List.of("<tên_phòng>");
            case "add" -> {
                List<String> players = onlinePlayerNames();
                players.add("all");
                yield players;
            }
            case "remove", "givepoint", "takepoint" -> onlinePlayerNames();
            case "debug" -> List.of("on", "off", "status", "player", "room");
            default -> List.of();
        };
        return filterByPrefix(suggestions, args[1]);
    }

    private List<String> handleThirdArgument(String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        String arg2 = args[1];
        if (sub.equals("add") || sub.equals("remove")) {
            List<String> roomAndTypes = roomAndTypeSuggestions();
            if (sub.equals("remove") && arg2.equalsIgnoreCase("all")) {
                return List.of();
            }
            return filterByPrefix(roomAndTypes, args[2]);
        }
        if (sub.equals("givepoint") || sub.equals("takepoint")) {
            return filterByPrefix(List.of("<số_point>"), args[2]);
        }
        if (sub.equals("debug")) {
            if (arg2.equalsIgnoreCase("player")) {
                return filterByPrefix(onlinePlayerNames(), args[2]);
            }
            if (arg2.equalsIgnoreCase("room")) {
                return filterByPrefix(Game.listGameWithoutCompleteSetup(), args[2]);
            }
        }
        return List.of();
    }

    private List<String> handleFourthArgument(String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("add") || sub.equals("remove")) {
            return filterByPrefix(List.of("<số_lượt>"), args[3]);
        }
        return List.of();
    }

    private static List<String> roomAndTypeSuggestions() {
        Set<String> suggestions = new LinkedHashSet<>(Game.listGameWithoutCompleteSetup());
        suggestions.addAll(Game.listType());
        return new ArrayList<>(suggestions);
    }

    private static List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<String> filterByPrefix(List<String> values, String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
