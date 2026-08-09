package com.colorwars.tasks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 매 초마다 전체 접속자를 검사해서, 오프핸드(왼손)에 엔더드래곤 알을 들고 있으면
 * 재생 I, 신속 II 효과를 계속 유지시켜준다. (게임 참가 여부와 무관하게 적용되는
 * 아이템 자체의 능력이라고 판단해서 전체 플레이어 대상으로 처리)
 */
public class DragonEggEffectTask extends BukkitRunnable {

    private static final int DURATION_TICKS = 40; // 1초 주기로 재부여하므로 살짝 여유있게 2초치 부여
    private static final int REGEN_AMPLIFIER = 0;  // 재생 I
    private static final int SPEED_AMPLIFIER = 1;  // 신속 II

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack offHand = p.getInventory().getItemInOffHand();
            if (offHand.getType() != Material.DRAGON_EGG) continue;

            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    DURATION_TICKS, REGEN_AMPLIFIER, true, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    DURATION_TICKS, SPEED_AMPLIFIER, true, false));
        }
    }
}
