package camchua.phoban.phobanpro.game.listener.sapi;

import camchua.phoban.phobanpro.DungeonsCore;
import camchua.phoban.phobanpro.game.DeathHandler;
import camchua.phoban.phobanpro.game.EntityData;
import camchua.phoban.phobanpro.game.PlayerData;
import camchua.phoban.phobanpro.mythicmobs.BukkitAPIHelper;
import camchua.phoban.phobanpro.utils.DebugLogger;

import com.sucy.skill.api.event.SkillDamageEvent;
import com.sucy.skill.api.event.TrueDamageEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class SAPIListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSkillDamage(SkillDamageEvent e) {
        if (!(e.getTarget() instanceof Player p)) return;
        if (!PlayerData.contains(p)) return;
        if (DeathHandler.isProtected(p)) { e.setCancelled(true); return; }

        if (p.getHealth() <= e.getDamage()) {
            DeathHandler.handleDeath(p, () -> e.setCancelled(true));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrueDamage(TrueDamageEvent e) {
        if (!(e.getTarget() instanceof Player p)) return;
        if (!PlayerData.contains(p)) return;
        if (DeathHandler.isProtected(p)) { e.setCancelled(true); return; }

        if (p.getHealth() <= e.getDamage()) {
            DeathHandler.handleDeath(p, () -> e.setCancelled(true));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void damageCountSkill(SkillDamageEvent e) {
        handleDamageCount(e.getTarget(), e.getDamager(), e.getDamage());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void damageCountTrue(TrueDamageEvent e) {
        handleDamageCount(e.getTarget(), e.getDamager(), e.getDamage());
    }

    private void handleDamageCount(LivingEntity target, LivingEntity damager, double damage) {
        if (!(damager instanceof Player p)) return;
        BukkitAPIHelper mm = DungeonsCore.inst().getBukkitAPIHelper();
        if (!mm.isMythicMob((Entity) target)) return;
        PlayerData data = PlayerData.get(p);
        if (data == null) return;
        EntityData entityData = EntityData.data().get((Entity) target);
        if (entityData != null) {
            entityData.recordDamage(p);
            DebugLogger.log("SkillAPI mob damage recorded. room=" + entityData.getGame().getName()
                    + ", mob=" + entityData.getMobKey()
                    + ", player=" + p.getName()
                    + ", damage=" + damage);
        }
        data.getGame().addDamage(p.getName(), damage);
    }
}
