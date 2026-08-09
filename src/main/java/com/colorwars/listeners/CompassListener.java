package com.colorwars.listeners;

import com.colorwars.GameManager;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class CompassListener implements Listener {

    private final GameManager gm;

    public CompassListener(GameManager gm) {
        this.gm = gm;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getItem() == null || e.getItem().getType() != Material.DIAMOND) return;
        if (!gm.isRunning()) return;

        Player p = e.getPlayer();
        if (!gm.isParticipant(p.getUniqueId())) return;

        // 사용한 다이아몬드는 소모(1개 차감). 크리에이티브 모드는 예외.
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            org.bukkit.inventory.ItemStack used = e.getItem();
            used.setAmount(used.getAmount() - 1);
        }

        Player target = gm.getNextColorTargetLeader(p.getUniqueId());
        if (target == null || target.equals(p)) {
            p.sendActionBar(Component.text("다음 색 플레이어를 찾을 수 없습니다 (접속 중이 아닐 수 있음).")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));
            return;
        }

        String arrow = bearingArrow(p, target.getLocation());
        double dist = p.getLocation().distance(target.getLocation());
        ChatColor legacyColor = gm.getColor(target.getUniqueId());
        net.kyori.adventure.text.format.TextColor adventureColor =
                net.kyori.adventure.text.format.NamedTextColor.NAMES.value(legacyColor.name().toLowerCase());
        if (adventureColor == null) adventureColor = net.kyori.adventure.text.format.NamedTextColor.WHITE;

        Component message = Component.text(arrow + " ")
                .append(Component.text(gm.colorName(legacyColor)).color(adventureColor))
                .append(Component.text(" 방향 · " + Math.round(dist) + "m"));
        p.sendActionBar(message);
    }

    /**
     * 플레이어가 바라보는 방향(yaw) 대비 목표 지점이 8방위 중 어느 쪽에 있는지 화살표로 반환.
     */
    private String bearingArrow(Player p, Location target) {
        Location from = p.getLocation();
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();

        // 마인크래프트 좌표계 기준 목표 방위각 (0=남쪽, 시계방향 증가와 맞춤)
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = targetYaw - from.getYaw();
        relative = ((relative % 360) + 360) % 360; // 0~360 정규화

        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int index = (int) Math.round(relative / 45.0) % 8;
        return arrows[index];
    }
}
