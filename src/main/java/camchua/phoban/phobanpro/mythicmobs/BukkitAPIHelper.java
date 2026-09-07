package camchua.phoban.phobanpro.mythicmobs;

import camchua.phoban.phobanpro.DungeonsCore;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Hook MythicMobs API Lumine 5.x (<i>io.lumine.mythic.*</i>) chỉ qua reflection.
 * Tránh {@link NoClassDefFoundError} khi chạy trên server chỉ có Mythic bản cũ / không có class {@code MythicBukkit}.
 */
public class BukkitAPIHelper {

    private static final String LUMINE_MYTHIC_BUKKIT = "io.lumine.mythic.bukkit.MythicBukkit";
    private static final String MYTHIC_PROVIDER = "io.lumine.mythic.api.MythicProvider";
    private static final String BUKKIT_ADAPTER = "io.lumine.mythic.bukkit.BukkitAdapter";
    private static final Set<String> LOGGED_WARNINGS = ConcurrentHashMap.newKeySet();

    /** Chuỗi nhận dạng khi hook Lumine thành công; rỗng nếu không có API. */
    public final String packageName;

    private final Object mythicBukkitInst;

    public BukkitAPIHelper() {
        Object inst = null;
        String pkg = "";
        try {
            Class<?> mbClass = Class.forName(LUMINE_MYTHIC_BUKKIT);
            Method instMethod = mbClass.getMethod("inst");
            inst = instMethod.invoke(null);
            pkg = "io.lumine.mythic.bukkit";
            DungeonsCore.inst().getLogger().info("MythicMobs: Lumine API (reflection, v5.x)");
        } catch (Throwable t) {
            DungeonsCore.inst().getLogger().warning("MythicMobs Lumine API không khả dụng — spawn/check Mythic tắt. " + t.getMessage());
        }
        this.mythicBukkitInst = inst;
        this.packageName = pkg;
    }

    public boolean isLumineHooked() {
        return mythicBukkitInst != null;
    }

    public Entity spawnMythicMob(String key, Location loc) {
        if (mythicBukkitInst == null) return null;
        try {
            Class<?> providerCl = Class.forName(MYTHIC_PROVIDER);
            Object provider = providerCl.getMethod("get").invoke(null);
            Object mobManager = provider.getClass().getMethod("getMobManager").invoke(provider);
            @SuppressWarnings("unchecked")
            Optional<Object> mmOpt = (Optional<Object>) mobManager.getClass().getMethod("getMythicMob", String.class).invoke(mobManager, key);
            if (mmOpt == null || mmOpt.isEmpty()) {
                warnOnce("unknown:" + key, "Unknown MythicMob key: " + key);
                return null;
            }
            Object mythicMob = mmOpt.get();

            Class<?> bukkitAdapter = Class.forName(BUKKIT_ADAPTER);
            Object adapted = bukkitAdapter.getMethod("adapt", Location.class).invoke(null, loc);
            Object activeMob = invokeSpawn(key, mythicMob, adapted);
            if (activeMob == null) return null;

            return toBukkitEntity(activeMob);
        } catch (Throwable e) {
            String warningKey = "spawn:" + key + ":" + e.getClass().getName() + ":" + e.getMessage();
            if (LOGGED_WARNINGS.add(warningKey)) {
                DungeonsCore.inst().getLogger().log(Level.WARNING, "spawnMythicMob failed once for " + key, e);
            }
            return null;
        }
    }

    public boolean isMythicMob(Entity entity) {
        if (mythicBukkitInst == null) return false;
        try {
            Object mobManager = mythicBukkitInst.getClass().getMethod("getMobManager").invoke(mythicBukkitInst);
            @SuppressWarnings("unchecked")
            Optional<?> opt = (Optional<?>) mobManager.getClass().getMethod("getActiveMob", UUID.class).invoke(mobManager, entity.getUniqueId());
            return opt != null && opt.isPresent();
        } catch (Throwable e) {
            return false;
        }
    }

    public String getMythicMobDisplayNameGet(Entity entity) {
        if (mythicBukkitInst == null) return "";
        try {
            Object mobManager = mythicBukkitInst.getClass().getMethod("getMobManager").invoke(mythicBukkitInst);
            @SuppressWarnings("unchecked")
            Optional<Object> amOpt = (Optional<Object>) mobManager.getClass().getMethod("getActiveMob", UUID.class).invoke(mobManager, entity.getUniqueId());
            if (amOpt == null || amOpt.isEmpty()) return "";
            Object activeMob = amOpt.get();
            Object mobType = activeMob.getClass().getMethod("getType").invoke(activeMob);
            Object displayOpt = mobType.getClass().getMethod("getDisplayName").invoke(mobType);
            if (displayOpt instanceof Optional) {
                return ((Optional<?>) displayOpt).map(o -> o != null ? o.toString() : "").orElse("");
            }
            return displayOpt != null ? displayOpt.toString() : "";
        } catch (Throwable e) {
            return "";
        }
    }

    public String getMythicMobDisplayNameGet(String key) {
        if (mythicBukkitInst == null) return key;
        try {
            Class<?> providerCl = Class.forName(MYTHIC_PROVIDER);
            Object provider = providerCl.getMethod("get").invoke(null);
            Object mobManager = provider.getClass().getMethod("getMobManager").invoke(provider);
            @SuppressWarnings("unchecked")
            Optional<Object> mmOpt = (Optional<Object>) mobManager.getClass().getMethod("getMythicMob", String.class).invoke(mobManager, key);
            if (mmOpt == null || mmOpt.isEmpty()) return key;
            Object mythicMob = mmOpt.get();
            Object displayOpt = mythicMob.getClass().getMethod("getDisplayName").invoke(mythicMob);
            if (displayOpt instanceof Optional) {
                return ((Optional<?>) displayOpt).map(o -> o != null ? o.toString() : key).orElse(key);
            }
            return displayOpt != null ? displayOpt.toString() : key;
        } catch (Throwable e) {
            return key;
        }
    }

    public String getMythicMobInternalName(Entity entity) {
        if (mythicBukkitInst == null) return null;
        try {
            Object mobManager = mythicBukkitInst.getClass().getMethod("getMobManager").invoke(mythicBukkitInst);
            @SuppressWarnings("unchecked")
            Optional<Object> amOpt = (Optional<Object>) mobManager.getClass().getMethod("getActiveMob", UUID.class).invoke(mobManager, entity.getUniqueId());
            if (amOpt == null || amOpt.isEmpty()) return null;
            Object activeMob = amOpt.get();
            Object mobType = activeMob.getClass().getMethod("getType").invoke(activeMob);
            return (String) mobType.getClass().getMethod("getInternalName").invoke(mobType);
        } catch (Throwable e) {
            return null;
        }
    }

    private static Object invokeSpawn(String key, Object mythicMob, Object adaptedLoc) throws Throwable {
        Method[] methods = Arrays.stream(mythicMob.getClass().getMethods())
                .filter(m -> "spawn".equals(m.getName()))
                .sorted((a, b) -> Integer.compare(spawnScore(b, adaptedLoc), spawnScore(a, adaptedLoc)))
                .toArray(Method[]::new);

        Set<String> failedSignatures = new HashSet<>();
        for (Method method : methods) {
            Object[] args = buildSpawnArgs(method, adaptedLoc);
            if (args == null) continue;
            try {
                return method.invoke(mythicMob, args);
            } catch (Throwable t) {
                failedSignatures.add(signature(method));
            }
        }

        String warningKey = "no-spawn:" + mythicMob.getClass().getName();
        warnOnce(warningKey, "No compatible MythicMobs spawn(...) overload for '" + key + "' on "
                + mythicMob.getClass().getName() + ". Found: " + spawnSignatures(methods)
                + (failedSignatures.isEmpty() ? "" : ". Tried but failed: " + String.join(", ", failedSignatures)));
        return null;
    }

    private static int spawnScore(Method method, Object adaptedLoc) {
        Object[] args = buildSpawnArgs(method, adaptedLoc);
        if (args == null) return -1;
        int score = 100 - method.getParameterCount();
        for (Class<?> type : method.getParameterTypes()) {
            if (type.isInstance(adaptedLoc)) score += 100;
            if (type == int.class || type == Integer.class) score += 10;
        }
        return score;
    }

    private static Object[] buildSpawnArgs(Method method, Object adaptedLoc) {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        boolean usedLocation = false;

        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (!usedLocation && type.isInstance(adaptedLoc)) {
                args[i] = adaptedLoc;
                usedLocation = true;
                continue;
            }
            Object defaultValue = defaultSpawnArg(type);
            if (defaultValue == UnsupportedDefault.INSTANCE) {
                return null;
            }
            args[i] = defaultValue;
        }

        return usedLocation ? args : null;
    }

    private static Object defaultSpawnArg(Class<?> type) {
        if (type == int.class || type == Integer.class) return 1;
        if (type == long.class || type == Long.class) return 1L;
        if (type == float.class || type == Float.class) return 1.0f;
        if (type == double.class || type == Double.class) return 1.0d;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == short.class || type == Short.class) return (short) 1;
        if (type == byte.class || type == Byte.class) return (byte) 1;
        if (type == char.class || type == Character.class) return '\0';
        if (type.isEnum()) {
            Object[] values = type.getEnumConstants();
            return values != null && values.length > 0 ? values[0] : null;
        }
        if (Optional.class.isAssignableFrom(type)) return Optional.empty();
        return type.isPrimitive() ? UnsupportedDefault.INSTANCE : null;
    }

    private static Entity toBukkitEntity(Object activeMob) throws ReflectiveOperationException {
        if (activeMob instanceof Optional<?> opt) {
            if (opt.isEmpty()) return null;
            activeMob = opt.get();
        }
        if (activeMob instanceof Entity entity) return entity;
        Object abstractEntity;
        try {
            abstractEntity = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
        } catch (NoSuchMethodException ignored) {
            abstractEntity = activeMob;
        }
        if (abstractEntity instanceof Entity entity) return entity;
        Object bukkitEntity = abstractEntity.getClass().getMethod("getBukkitEntity").invoke(abstractEntity);
        return bukkitEntity instanceof Entity entity ? entity : null;
    }

    private static String spawnSignatures(Method[] methods) {
        if (methods.length == 0) return "<none>";
        StringBuilder sb = new StringBuilder();
        for (Method method : methods) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(signature(method));
        }
        return sb.toString();
    }

    private static String signature(Method method) {
        StringBuilder sb = new StringBuilder(method.getName()).append('(');
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(types[i].getSimpleName());
        }
        return sb.append(')').toString();
    }

    private static void warnOnce(String key, String message) {
        if (LOGGED_WARNINGS.add(key)) {
            DungeonsCore.inst().getLogger().warning(message);
        }
    }

    private enum UnsupportedDefault {
        INSTANCE
    }
}
