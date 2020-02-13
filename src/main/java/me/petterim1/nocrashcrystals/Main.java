package me.petterim1.nocrashcrystals;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityEndCrystal;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.plugin.PluginBase;

public class Main extends PluginBase implements Listener {

    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onBigBoom(EntityExplodeEvent e) {
        Entity ent = e.getEntity();
        if (!e.isCancelled() && ent instanceof EntityEndCrystal) {
            e.setCancelled(true);
            CrystalExplosion explosion = new CrystalExplosion(ent, ent, e.getBlockList(), e.getYield());
            explosion.explodeB();
        }
    }
}
