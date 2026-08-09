package com.colorwars;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class ColorWarsCommand implements CommandExecutor, TabCompleter {

    private final GameManager gm;

    public ColorWarsCommand(GameManager gm) {
        this.gm = gm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(org.bukkit.ChatColor.YELLOW + "사용법: /colorwars <register|start|stop|status>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "register" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("플레이어만 사용할 수 있습니다.");
                    return true;
                }
                if (gm.isRunning()) {
                    p.sendMessage(org.bukkit.ChatColor.RED + "이미 게임이 진행 중이라 등록할 수 없습니다.");
                    return true;
                }
                boolean ok = gm.register(p);
                p.sendMessage(ok
                        ? org.bukkit.ChatColor.GREEN + "다음 게임에 등록되었습니다."
                        : org.bukkit.ChatColor.YELLOW + "이미 등록되어 있습니다.");
                return true;
            }
            case "start" -> {
                if (!sender.hasPermission("colorwars.admin")) {
                    sender.sendMessage(org.bukkit.ChatColor.RED + "권한이 없습니다.");
                    return true;
                }
                String error = gm.start();
                if (error != null) {
                    sender.sendMessage(org.bukkit.ChatColor.RED + error);
                } else {
                    Bukkit.broadcastMessage(org.bukkit.ChatColor.GOLD + "=== 컬러워즈 게임 시작! ===");
                    for (UUID id : gm.getLeaderOrder()) {
                        Player p = Bukkit.getPlayer(id);
                        if (p == null) continue;
                        Bukkit.broadcastMessage(gm.getColor(id) + p.getName() + org.bukkit.ChatColor.WHITE
                                + " -> " + gm.colorName(gm.getColor(id)) + " 팀");
                    }
                    Bukkit.broadcastMessage(org.bukkit.ChatColor.GRAY
                            + "다이아몬드를 우클릭하면 다음 색 방향을 알 수 있습니다.");
                }
                return true;
            }
            case "stop" -> {
                if (!sender.hasPermission("colorwars.admin")) {
                    sender.sendMessage(org.bukkit.ChatColor.RED + "권한이 없습니다.");
                    return true;
                }
                gm.stop();
                Bukkit.broadcastMessage(org.bukkit.ChatColor.RED + "컬러워즈 게임이 종료되었습니다.");
                return true;
            }
            case "status" -> {
                if (!gm.isRunning()) {
                    sender.sendMessage(org.bukkit.ChatColor.YELLOW + "현재 진행 중인 게임이 없습니다.");
                    return true;
                }
                sender.sendMessage(org.bukkit.ChatColor.GOLD + "=== 색 순환 현황 ===");
                for (UUID leaderId : gm.getLeaderOrder()) {
                    Player leader = Bukkit.getPlayer(leaderId);
                    String name = leader != null ? leader.getName() : leaderId.toString().substring(0, 8);
                    Set<UUID> minions = gm.getMinionsMap().getOrDefault(leaderId, Collections.emptySet());
                    StringBuilder sb = new StringBuilder();
                    sb.append(gm.getColor(leaderId)).append(gm.colorName(gm.getColor(leaderId)))
                            .append(org.bukkit.ChatColor.WHITE).append(" 팀장: ").append(name)
                            .append(org.bukkit.ChatColor.GRAY).append(" (부하 ").append(minions.size()).append("명)");
                    sender.sendMessage(sb.toString());
                }
                return true;
            }
            default -> {
                sender.sendMessage(org.bukkit.ChatColor.YELLOW + "사용법: /colorwars <register|start|stop|status>");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("register", "start", "stop", "status");
        }
        return Collections.emptyList();
    }
}
