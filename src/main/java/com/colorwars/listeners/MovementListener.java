package com.colorwars.listeners;

import com.colorwars.GameManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

public class MovementListener implements Listener {

    private static final double LEASH_RANGE = 50.0;
    private static final double LEASH_DAMAGE = 2.0; // 1하트, 범위를 벗어나 있는 동안 주기적으로 적용
    private static final long DAMAGE_COOLDOWN_MILLIS = 1000L; // 1초에 한 번씩 데미지

    private final java.util.Map<UUID, Long> lastDamageMillis = new java.util.HashMap<>();

    private final GameManager gm;

    public MovementListener(GameManager gm) {
        this.gm = gm;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        // 1) 외부요인 사망 후 행동불가 상태: 그 자리에 고정
        if (gm.isFrozen(id)) {
            Location origin = gm.getFreezeLocation(id);
            if (origin != null && (e.getTo().getX() != e.getFrom().getX()
                    || e.getTo().getZ() != e.getFrom().getZ()
                    || e.getTo().getY() != e.getFrom().getY())) {
                Location fixed = origin.clone();
                fixed.setYaw(e.getTo().getYaw());
                fixed.setPitch(e.getTo().getPitch());
                e.setTo(fixed);
            }
            return;
        }

        if (!gm.isRunning()) return;

        // 2) 부하는 주인으로부터 50블록 이상 벗어나면 이동은 막지 않되 데미지를 입음
        if (!gm.isMinion(id)) return;
        UUID ownerId = gm.getOwner(id);
        Player owner = ownerId != null ? org.bukkit.Bukkit.getPlayer(ownerId) : null;
        if (owner == null) return;
        if (!owner.getWorld().equals(p.getWorld())) return; // 다른 월드면 제한 판단 불가, 통과

        if (owner.getLocation().distance(e.getTo()) > LEASH_RANGE) {
            long now = System.currentTimeMillis();
            Long last = lastDamageMillis.get(id);
            if (last == null || now - last >= DAMAGE_COOLDOWN_MILLIS) {
                p.damage(LEASH_DAMAGE);
                p.sendActionBar(net.kyori.adventure.text.Component
                        .text("주인으로부터 " + (int) LEASH_RANGE + "블록 이상 벗어나 피해를 입고 있습니다!")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED));
                lastDamageMillis.put(id, now);
            }
        }
    }
}
