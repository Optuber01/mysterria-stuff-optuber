package net.mysterria.stuff.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class MainCommandTabCompleter implements TabCompleter {

    private static final List<String> MAIN_COMMANDS = Arrays.asList(
            "help", "info", "status", "reload", "give", "export", "debug", "recipe", "token",
            "joinmsg", "lastsprint", "booster"
    );

    private static final List<String> JOINMSG_SUBCOMMANDS = Arrays.asList(
            "give", "confirm", "cancel", "restart", "set", "get", "remove", "list", "default", "firstjoin", "reload", "repair"
    );

    private static final List<String> JOINMSG_PLAYER_TARGET_SUBCOMMANDS = List.of(
            "give", "set", "get", "remove"
    );

    private static final List<String> JOINMSG_TYPES = List.of(
            "join", "quit"
    );

    private static final List<String> JOINMSG_GET_SET = List.of(
            "get", "set"
    );

    private static final List<String> BOOSTER_SUBCOMMANDS = Arrays.asList(
            "check", "grant", "revoke", "refresh", "list"
    );

    private static final List<String> LASTSPRINT_SUBCOMMANDS = Arrays.asList(
            "setup", "give", "reset", "enable", "disable", "info"
    );

    private static final List<String> ITEM_TYPES = List.of(
            "elytra"
    );

    private static final List<String> RECIPE_SUBCOMMANDS = Arrays.asList(
            "list", "reload"
    );

    private static final List<String> TOKEN_SUBCOMMANDS = List.of(
            "give"
    );

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {

            return filterStartingWith(MAIN_COMMANDS, args[0]);
        } else if (args.length == 2) {

            switch (args[0].toLowerCase()) {
                case "give" -> {
                    return filterStartingWith(ITEM_TYPES, args[1]);
                }
                case "recipe" -> {
                    return filterStartingWith(RECIPE_SUBCOMMANDS, args[1]);
                }
                case "token" -> {
                    return filterStartingWith(TOKEN_SUBCOMMANDS, args[1]);
                }
                case "joinmsg" -> {
                    return filterStartingWith(JOINMSG_SUBCOMMANDS, args[1]);
                }
                case "lastsprint" -> {
                    return filterStartingWith(LASTSPRINT_SUBCOMMANDS, args[1]);
                }
                case "booster" -> {
                    return filterStartingWith(BOOSTER_SUBCOMMANDS, args[1]);
                }
            }
        } else if (args.length == 3) {

            if (args[0].equalsIgnoreCase("give")) {

                return filterStartingWith(
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .collect(Collectors.toList()),
                        args[2]
                );
            } else if (args[0].equalsIgnoreCase("token") && args[1].equalsIgnoreCase("give")) {

                return filterStartingWith(
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .collect(Collectors.toList()),
                        args[2]
                );
            } else if (args[0].equalsIgnoreCase("joinmsg")
                    && JOINMSG_PLAYER_TARGET_SUBCOMMANDS.contains(args[1].toLowerCase())) {

                return filterStartingWith(
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .collect(Collectors.toList()),
                        args[2]
                );
            } else if (args[0].equalsIgnoreCase("joinmsg")
                    && (args[1].equalsIgnoreCase("default") || args[1].equalsIgnoreCase("firstjoin"))) {

                return filterStartingWith(JOINMSG_GET_SET, args[2]);
            } else if (args[0].equalsIgnoreCase("lastsprint")
                    && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("reset"))) {

                return filterStartingWith(
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .collect(Collectors.toList()),
                        args[2]
                );
            } else if (args[0].equalsIgnoreCase("booster")
                    && (args[1].equalsIgnoreCase("check") || args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("revoke"))) {

                return filterStartingWith(
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .collect(Collectors.toList()),
                        args[2]
                );
            }
        } else if (args.length == 4) {

            if (args[0].equalsIgnoreCase("token") && args[1].equalsIgnoreCase("give")) {

                return List.of("1", "5", "10", "16", "32", "64");
            } else if (args[0].equalsIgnoreCase("joinmsg") && args[1].equalsIgnoreCase("give")) {

                return List.of("1", "5", "10", "16", "32", "64");
            } else if (args[0].equalsIgnoreCase("joinmsg")
                    && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove"))) {

                return filterStartingWith(JOINMSG_TYPES, args[3]);
            } else if (args[0].equalsIgnoreCase("joinmsg") && args[1].equalsIgnoreCase("default")
                    && args[2].equalsIgnoreCase("set")) {

                return filterStartingWith(JOINMSG_TYPES, args[3]);
            }
        }

        return completions;
    }


    private List<String> filterStartingWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }
}
