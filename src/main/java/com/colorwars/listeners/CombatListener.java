package com.colorwars.listeners;

import com.colorwars.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 플레이어 대 플레이어 데미지만 색 순환 규칙으로 제한한다.
 * 몹/낙사/불 등 그 외 모든 바닐라 데미지는 손대지 않는다(원본 규칙: "기존 마크에서 받는
 * 엔티티, 낙뎀 등도 받음" -> 즉 그대로 통과시켜야 함).
 */
public class CombatListener implements Listener {

    private final GameManager gm;

    public CombatListener(GameManager gm) {
        this.gm = gm;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!gm.isRunning()) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(e);
        if (attacker == null) return; // 플레이어가 직접 낸 데미지가 아니면(예: 화살은 아래서 처리) 통과

        if (!gm.canAttack(attacker.getUniqueId(), victim.getUniqueId())) {
            e.setCancelled(true);
            attacker.sendActionBar(net.kyori.adventure.text.Component
                    .text("지금은 그 대상을 공격할 수 없습니다 (색 순환 규칙)")
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return p;
        if (e.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
