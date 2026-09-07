package camchua.phoban.phobanpro.game;

import camchua.phoban.phobanpro.DungeonsCore;
import camchua.phoban.phobanpro.mythicmobs.BukkitAPIHelper;
import camchua.phoban.phobanpro.utils.DebugLogger;
import camchua.phoban.phobanpro.utils.Messages;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

public class GameListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityExplosive(ExplosionPrimeEvent e) {
        handleTrackedMobCleared(e.getEntity(), null, false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent e) {
        handleTrackedMobCleared(e.getEntity(), e.getEntity().getKiller(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedPlayerDamaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!PlayerData.contains(p)) return;
        if (!DeathHandler.isProtected(p)) return;
        e.setCancelled(true);
        DebugLogger.log("Protected player damage cancelled. player=" + p.getName()
                + ", cause=" + e.getCause()
                + ", damage=" + e.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!PlayerData.contains(p)) return;
        if (DeathHandler.isProtected(p)) { e.setCancelled(true); return; }

        if (p.getHealth() <= e.getFinalDamage()) {
            DeathHandler.handleDeath(p, () -> e.setCancelled(true));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTargetProtectedPlayer(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player p)) return;
        if (!PlayerData.contains(p)) return;
        if (!DeathHandler.isProtected(p)) return;
        e.setCancelled(true);
        e.setTarget(null);
        DebugLogger.log("Protected player target cancelled. player=" + p.getName()
                + ", entity=" + e.getEntity().getType());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void damageCount(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity)) return;
        Player p = resolvePlayerDamager(e.getDamager());
        if (p == null) return;
        BukkitAPIHelper mm = DungeonsCore.inst().getBukkitAPIHelper();
        if (!mm.isMythicMob(e.getEntity())) return;
        PlayerData data = PlayerData.get(p);
        if (data == null) return;
        EntityData entityData = EntityData.data().get(e.getEntity());
        if (entityData != null) {
            entityData.recordDamage(p);
            DebugLogger.log("Tracked mob damage recorded. room=" + entityData.getGame().getName()
                    + ", mob=" + entityData.getMobKey()
                    + ", player=" + p.getName()
                    + ", damage=" + e.getFinalDamage()
                    + ", damagerType=" + e.getDamager().getType());
        }
        data.getGame().addDamage(p.getName(), e.getFinalDamage());
    }

    private Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) return p;
        return null;
    }

    private void handleTrackedMobCleared(Entity entity, Player killer, boolean respawnWithoutKiller) {
        EntityData data = EntityData.data().remove(entity);
        if (data == null) {
            DebugLogger.log("Ignored untracked entity clear. entity=" + entity.getUniqueId()
                    + ", type=" + entity.getType()
                    + ", killer=" + (killer != null ? killer.getName() : "null"));
            return;
        }

        BukkitAPIHelper mm = DungeonsCore.inst().getBukkitAPIHelper();
        Game game = data.getGame();
        String mobKey = data.getMobKey();
        if (mobKey == null || mobKey.isEmpty()) {
            mobKey = mm.getMythicMobInternalName(entity);
        }
        if (mobKey == null || mobKey.isEmpty()) {
            DebugLogger.log("Tracked entity cleared but mob key is unknown. room=" + game.getName()
                    + ", entity=" + entity.getUniqueId()
                    + ", killer=" + (killer != null ? killer.getName() : "null"));
            return;
        }
        if (game.getStatus() != GameStatus.PLAYING || game.isCompleted()) {
            DebugLogger.log("Tracked mob cleared outside active gameplay. room=" + game.getName()
                    + ", mob=" + mobKey
                    + ", status=" + game.getStatus()
                    + ", completed=" + game.isCompleted());
            return;
        }

        Player creditedKiller = resolveValidKiller(killer, data);
        if (creditedKiller == null) {
            if (!respawnWithoutKiller) {
                game.addProgress(mobKey, 1);
                DebugLogger.log("Tracked mob cleared by non-kill event. room=" + game.getName()
                        + ", mob=" + mobKey
                        + ", entity=" + entity.getUniqueId()
                        + ", progress=" + game.getProgressCurrent(mobKey) + "/" + game.getProgressMax(mobKey));
                return;
            }
            DebugLogger.log("Tracked mob cleared without valid dungeon killer; respawning if current stage needs it. room="
                    + game.getName()
                    + ", mob=" + mobKey
                    + ", entity=" + entity.getUniqueId()
                    + ", killer=" + (killer != null ? killer.getName() : "null")
                    + ", lastDamageAt=" + data.getLastDamageAt());
            game.respawnLostMob(mobKey);
            return;
        }

        game.addProgress(mobKey, 1);
        game.addKill(creditedKiller.getName(), 1);
        DebugLogger.log("Tracked mob kill counted. room=" + game.getName()
                + ", mob=" + mobKey
                + ", killer=" + creditedKiller.getName()
                + ", directKiller=" + (killer != null ? killer.getName() : "null")
                + ", progress=" + game.getProgressCurrent(mobKey) + "/" + game.getProgressMax(mobKey));
        broadcastMobLeft(game, mm, entity, mobKey);
    }

    private Player resolveValidKiller(Player directKiller, EntityData data) {
        if (directKiller != null) {
            PlayerData killerData = PlayerData.get(directKiller);
            if (killerData != null && killerData.getGame() == data.getGame()) {
                data.recordDamage(directKiller);
                return directKiller;
            }
        }
        return data.getValidLastDamager();
    }

    private void broadcastMobLeft(Game game, BukkitAPIHelper mm, Entity entity, String mobKey) {
        int max = game.getProgressMax(mobKey);
        int current = game.getProgressCurrent(mobKey);
        String displayName = mm.getMythicMobDisplayNameGet(entity);
        if (displayName == null || displayName.isEmpty()) {
            displayName = mm.getMythicMobDisplayNameGet(mobKey);
        }
        String msg = Messages.get("MobsLeft")
                .replace("<name>", displayName)
                .replace("<max>", String.valueOf(max))
                .replace("<current>", String.valueOf(current));
        game.getPlayers().forEach(player -> player.sendMessage(msg));
    }
}
