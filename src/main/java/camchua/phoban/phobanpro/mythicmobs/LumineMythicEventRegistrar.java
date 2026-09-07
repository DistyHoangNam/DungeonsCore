package camchua.phoban.phobanpro.mythicmobs;

import camchua.phoban.phobanpro.DungeonsCore;
import camchua.phoban.phobanpro.game.DeathHandler;
import camchua.phoban.phobanpro.game.EntityData;
import camchua.phoban.phobanpro.game.PlayerData;
import camchua.phoban.phobanpro.utils.DebugLogger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Đăng ký {@code MythicDamageEvent} bằng reflection — không import class Lumine (tránh lỗi khi API không có).
 */
public final class LumineMythicEventRegistrar {

    private static final String MYTHIC_DAMAGE_EVENT = "io.lumine.mythic.bukkit.events.MythicDamageEvent";
    private static final String MYTHIC_MOB_DEATH_EVENT = "io.lumine.mythic.bukkit.events.MythicMobDeathEvent";

    private LumineMythicEventRegistrar() {}

    @SuppressWarnings("unchecked")
    public static void register(JavaPlugin plugin) {
        registerDamageEvent(plugin);
        registerMobDeathEvent(plugin);
    }

    @SuppressWarnings("unchecked")
    private static void registerDamageEvent(JavaPlugin plugin) {
        try {
            Class<? extends Event> evtClass = (Class<? extends Event>) Class.forName(MYTHIC_DAMAGE_EVENT).asSubclass(Event.class);
            Listener marker = new Listener() {};

            EventExecutor onHighest = (l, event) -> handleHighest(event);
            Bukkit.getPluginManager().registerEvent(evtClass, marker, EventPriority.HIGHEST, onHighest, plugin, true);

            EventExecutor onNormal = (l, event) -> handleNormal(event);
            Bukkit.getPluginManager().registerEvent(evtClass, marker, EventPriority.NORMAL, onNormal, plugin, true);
        } catch (ClassNotFoundException e) {
            DungeonsCore.inst().getLogger().info("MythicDamageEvent (Lumine) không có — bỏ listener damage Mythic.");
        } catch (Throwable t) {
            DungeonsCore.inst().getLogger().log(Level.WARNING, "Không đăng ký Mythic damage events", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerMobDeathEvent(JavaPlugin plugin) {
        try {
            Class<? extends Event> evtClass = (Class<? extends Event>) Class.forName(MYTHIC_MOB_DEATH_EVENT).asSubclass(Event.class);
            Listener marker = new Listener() {};
            EventExecutor onDeath = (l, event) -> handleMobDeath(event);
            Bukkit.getPluginManager().registerEvent(evtClass, marker, EventPriority.HIGH, onDeath, plugin, true);
            DebugLogger.log("Registered MythicMobDeathEvent listener");
        } catch (ClassNotFoundException e) {
            DungeonsCore.inst().getLogger().info("MythicMobDeathEvent (Lumine) không có — dùng Bukkit EntityDeathEvent fallback.");
        } catch (Throwable t) {
            DungeonsCore.inst().getLogger().log(Level.WARNING, "Không đăng ký Mythic mob death events", t);
        }
    }

    private static void handleHighest(Event event) {
        try {
            Class<?> ec = event.getClass();
            Object targetAbs = ec.getMethod("getTarget").invoke(event);
            if (targetAbs == null) return;
            Entity entity = (Entity) targetAbs.getClass().getMethod("getBukkitEntity").invoke(targetAbs);
            if (!(entity instanceof Player p)) return;
            if (!PlayerData.contains(p)) return;

            double damage = toDouble(ec.getMethod("getDamage").invoke(event));
            if (DeathHandler.isProtected(p)) {
                ec.getMethod("setCancelled", boolean.class).invoke(event, true);
                return;
            }
            if (p.getHealth() <= damage) {
                DeathHandler.handleDeath(p, () -> {
                    try {
                        ec.getMethod("setCancelled", boolean.class).invoke(event, true);
                    } catch (Throwable t) {
                        DungeonsCore.inst().getLogger().log(Level.FINE,
                                "Không huỷ được MythicDamageEvent qua reflection (chết riel)", t);
                    }
                });
            }
        } catch (Throwable t) {
            DungeonsCore.inst().getLogger().log(Level.FINE, "Mythic damage (highest)", t);
        }
    }

    private static void handleNormal(Event event) {
        try {
            Class<?> ec = event.getClass();
            Object targetAbs = ec.getMethod("getTarget").invoke(event);
            if (targetAbs == null) return;
            Entity target = (Entity) targetAbs.getClass().getMethod("getBukkitEntity").invoke(targetAbs);
            if (!(target instanceof LivingEntity)) return;

            Object casterObj = ec.getMethod("getCaster").invoke(event);
            if (casterObj == null) return;
            Object casterEntity = casterObj.getClass().getMethod("getEntity").invoke(casterObj);
            if (casterEntity == null) return;
            Entity casterBukkit = (Entity) casterEntity.getClass().getMethod("getBukkitEntity").invoke(casterEntity);
            if (!(casterBukkit instanceof Player p)) return;

            BukkitAPIHelper mm = DungeonsCore.inst().getBukkitAPIHelper();
            if (!mm.isMythicMob(target)) return;
            PlayerData data = PlayerData.get(p);
            if (data == null) return;
            double damage = toDouble(ec.getMethod("getDamage").invoke(event));
            EntityData entityData = EntityData.data().get(target);
            if (entityData != null) {
                entityData.recordDamage(p);
                DebugLogger.log("Mythic mob damage recorded. room=" + entityData.getGame().getName()
                        + ", mob=" + entityData.getMobKey()
                        + ", player=" + p.getName()
                        + ", damage=" + damage);
            }
            data.getGame().addDamage(p.getName(), damage);
        } catch (Throwable t) {
            DungeonsCore.inst().getLogger().log(Level.FINE, "Mythic damage (normal)", t);
        }
    }

    private static void handleMobDeath(Event event) {
        try {
            Class<?> ec = event.getClass();
            Object rawEntity = ec.getMethod("getEntity").invoke(event);
            if (!(rawEntity instanceof Entity entity)) return;

            EntityData entityData = EntityData.data().get(entity);
            if (entityData == null) return;

            Object rawKiller = ec.getMethod("getKiller").invoke(event);
            if (rawKiller instanceof Player p) {
                entityData.recordDamage(p);
                DebugLogger.log("Mythic mob death direct killer recorded. room=" + entityData.getGame().getName()
                        + ", mob=" + entityData.getMobKey()
                        + ", killer=" + p.getName());
            } else if (rawKiller instanceof LivingEntity living) {
                DebugLogger.log("Mythic mob death killer was non-player. room=" + entityData.getGame().getName()
                        + ", mob=" + entityData.getMobKey()
                        + ", killerType=" + living.getType());
            } else {
                DebugLogger.log("Mythic mob death had no direct killer. room=" + entityData.getGame().getName()
                        + ", mob=" + entityData.getMobKey());
            }
        } catch (Throwable t) {
            DungeonsCore.inst().getLogger().log(Level.FINE, "Mythic mob death", t);
        }
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return 0;
    }
}
