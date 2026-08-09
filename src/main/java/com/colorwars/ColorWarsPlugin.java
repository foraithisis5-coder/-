package com.colorwars;

import com.colorwars.listeners.CombatListener;
import com.colorwars.listeners.CompassListener;
import com.colorwars.listeners.DeathListener;
import com.colorwars.listeners.DragonEggListener;
import com.colorwars.listeners.MovementListener;
import com.colorwars.tasks.DragonEggEffectTask;
import org.bukkit.plugin.java.JavaPlugin;

public class ColorWarsPlugin extends JavaPlugin {

    private GameManager gameManager;

    @Override
    public void onEnable() {
        this.gameManager = new GameManager(this);

        getServer().getPluginManager().registerEvents(new CombatListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new DeathListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new MovementListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new DragonEggListener(), this);
        getServer().getPluginManager().registerEvents(new CompassListener(gameManager), this);

        new DragonEggEffectTask().runTaskTimer(this, 0L, 20L);

        var cmd = getCommand("colorwars");
        if (cmd != null) {
            ColorWarsCommand executor = new ColorWarsCommand(gameManager);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("ColorWars 활성화 완료.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.stop();
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}
