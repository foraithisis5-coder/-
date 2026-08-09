package com.colorwars.listeners;

import com.colorwars.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class DeathListener implements Listener {

    private static final long FREEZE_MILLIS = 10_000L;
    private static final int FREEZE_TICKS = 200; // 10초

    private final GameManager gm;

    public DeathListener(GameManager gm) {
        this.gm = gm;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!gm.isRunning()) return;
        Player victim = e.getEntity();
        if (!gm.isParticipant(victim.getUniqueId())) return;

        Player killer = victim.getKiller();

        if (killer != null && gm.isParticipant(killer.getUniqueId()) && !killer.equals(victim)) {
            // --- 플레이어에게 사망: 부하화 ---
            UUID winner = gm.convertToMinion(victim.getUniqueId(), killer.getUniqueId());

            Bukkit.broadcastMessage(ChatColor.YELLOW + victim.getName() + ChatColor.WHITE
                    + "님이 " + ChatColor.GOLD + killer.getName() + ChatColor.WHITE + "님의 부하가 되었습니다!");

            if (winner != null) {
                Player winnerPlayer = Bukkit.getPlayer(winner);
                String name = winnerPlayer != null ? winnerPlayer.getName() : "???";
                Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD
                        + "=== " + name + "님이 모든 플레이어를 부하로 만들어 승리했습니다! ===");
                gm.stop();
            }
        } else {
            // --- 외부 요인(몹, 낙사 등) 사망: 그 자리에서 10초 행동불가 ---
            Location deathLoc = victim.getLocation().clone();
            gm.markFrozen(victim.getUniqueId(), deathLoc, FREEZE_MILLIS);
            victim.sendMessage(ChatColor.RED + "외부 요인으로 사망하여 10초간 그 자리에서 행동할 수 없습니다.");
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!gm.isFrozen(p.getUniqueId())) return;

        Location freezeLoc = gm.getFreezeLocation(p.getUniqueId());
        if (freezeLoc != null) {
            e.setRespawnLocation(freezeLoc);
        }

        // 리스폰 직후 이동 불가 + 느낌 전달용 효과 부여
        Bukkit.getScheduler().runTask(gm.getPlugin(), () -> {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, FREEZE_TICKS, 10, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, FREEZE_TICKS, -10, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false));
        });
    }
}
