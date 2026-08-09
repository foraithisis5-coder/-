package com.colorwars.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * 엔더드래곤 알은 획득 후 어떤 상황에서도 블록으로 설치할 수 없다.
 * (바닐라의 "클릭하면 순간이동" 특성과는 별개로, 혹시라도 설치가 가능한
 *  경로가 있을 경우를 대비해 BlockPlaceEvent 자체를 막는다.)
 */
public class DragonEggListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getType() != Material.DRAGON_EGG) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(Component.text("엔더드래곤 알은 설치할 수 없습니다.")
                .color(NamedTextColor.RED));
    }
}
