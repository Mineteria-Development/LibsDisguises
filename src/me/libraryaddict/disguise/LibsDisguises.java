package me.libraryaddict.disguise;

import com.comphenix.protocol.reflect.FieldAccessException;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.FlagWatcher;
import me.libraryaddict.disguise.disguisetypes.MetaIndex;
import me.libraryaddict.disguise.disguisetypes.watchers.*;
import me.libraryaddict.disguise.utilities.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

public class LibsDisguises extends JavaPlugin {
    private static LibsDisguises instance;
    private DisguiseListener listener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getLogger().info("Discovered nms version: " + ReflectionManager.getBukkitVersion());

        if (!new File(getDataFolder(), "disguises.yml").exists()) {
            saveResource("disguises.yml", false);
        }

        LibsPremium.check(getDescription().getVersion());

        if (ReflectionManager.getMinecraftVersion().startsWith("1.13")) {
            if (!LibsPremium.isPremium()) {
                getLogger().severe("You must purchase the plugin to use 1.13!");
                getLogger().severe("This will be released free two weeks after all bugs have been fixed!");
                getLogger().severe("If you've already purchased the plugin, place the purchased jar inside the " +
                        "Lib's Disguises plugin folder");
                getPluginLoader().disablePlugin(this);
                return;
            }
        } else {
            getLogger().severe("You're using the wrong version of Lib's Disguises for your server! This is " +
                    "intended for 1.13!");
            getPluginLoader().disablePlugin(this);
            return;
        }

        PacketsManager.init(this);
        DisguiseUtilities.init(this);

        registerValues();

        DisguiseConfig.initConfig(getConfig());

        PacketsManager.addPacketListeners();

        listener = new DisguiseListener(this);

        Bukkit.getPluginManager().registerEvents(listener, this);
    }

    @Override
    public void onDisable() {
        DisguiseUtilities.saveDisguises();
    }

    /**
     * Reloads the config with new config options.
     */
    public void reload() {
        reloadConfig();
        DisguiseConfig.initConfig(getConfig());
    }

    /**
     * Here we create a nms entity for each disguise. Then grab their default values in their datawatcher. Then their
     * sound volume
     * for mob noises. As well as setting their watcher class and entity size.
     */
    private void registerValues() {
        for (DisguiseType disguiseType : DisguiseType.values()) {
            if (disguiseType.getEntityType() == null) {
                continue;
            }

            Class watcherClass;

            try {
                switch (disguiseType) {
                    case ARROW:
                        watcherClass = TippedArrowWatcher.class;
                        break;
                    case COD:
                    case SALMON:
                        watcherClass = FishWatcher.class;
                        break;
                    case SPECTRAL_ARROW:
                        watcherClass = ArrowWatcher.class;
                        break;
                    case PRIMED_TNT:
                        watcherClass = TNTWatcher.class;
                        break;
                    case MINECART_CHEST:
                    case MINECART_HOPPER:
                    case MINECART_MOB_SPAWNER:
                    case MINECART_TNT:
                        watcherClass = MinecartWatcher.class;
                        break;
                    case SPIDER:
                    case CAVE_SPIDER:
                        watcherClass = SpiderWatcher.class;
                        break;
                    case PIG_ZOMBIE:
                    case HUSK:
                    case DROWNED:
                        watcherClass = ZombieWatcher.class;
                        break;
                    case MAGMA_CUBE:
                        watcherClass = SlimeWatcher.class;
                        break;
                    case ELDER_GUARDIAN:
                        watcherClass = GuardianWatcher.class;
                        break;
                    case WITHER_SKELETON:
                    case STRAY:
                        watcherClass = SkeletonWatcher.class;
                        break;
                    case ILLUSIONER:
                    case EVOKER:
                        watcherClass = IllagerWizardWatcher.class;
                        break;
                    case PUFFERFISH:
                        watcherClass = PufferFishWatcher.class;
                        break;
                    default:
                        watcherClass = Class.forName(
                                "me.libraryaddict.disguise.disguisetypes.watchers." + toReadable(disguiseType.name()) +
                                        "Watcher");
                        break;
                }
            }
            catch (ClassNotFoundException ex) {
                // There is no explicit watcher for this entity.
                Class entityClass = disguiseType.getEntityType().getEntityClass();

                if (entityClass != null) {
                    if (Tameable.class.isAssignableFrom(entityClass)) {
                        watcherClass = TameableWatcher.class;
                    } else if (Ageable.class.isAssignableFrom(entityClass)) {
                        watcherClass = AgeableWatcher.class;
                    } else if (Creature.class.isAssignableFrom(entityClass)) {
                        watcherClass = InsentientWatcher.class;
                    } else if (LivingEntity.class.isAssignableFrom(entityClass)) {
                        watcherClass = LivingWatcher.class;
                    } else if (Fish.class.isAssignableFrom(entityClass)) {
                        watcherClass = FishWatcher.class;
                    } else {
                        watcherClass = FlagWatcher.class;
                    }
                } else {
                    watcherClass = FlagWatcher.class; // Disguise is unknown type
                }
            }

            if (watcherClass == null) {
                getLogger().severe("Error loading " + disguiseType.name() + ", FlagWatcher not assigned");
                continue;
            }

            disguiseType.setWatcherClass(watcherClass);

            if (DisguiseValues.getDisguiseValues(disguiseType) != null) {
                continue;
            }

            String nmsEntityName = toReadable(disguiseType.name());
            Class nmsClass = ReflectionManager.getNmsClassIgnoreErrors("Entity" + nmsEntityName);

            if (nmsClass == null || Modifier.isAbstract(nmsClass.getModifiers())) {
                String[] split = splitReadable(disguiseType.name());
                ArrayUtils.reverse(split);

                nmsEntityName = StringUtils.join(split);
                nmsClass = ReflectionManager.getNmsClassIgnoreErrors("Entity" + nmsEntityName);

                if (nmsClass == null || Modifier.isAbstract(nmsClass.getModifiers())) {
                    nmsEntityName = null;
                }
            }

            if (nmsEntityName == null) {
                switch (disguiseType) {
                    case DONKEY:
                        nmsEntityName = "HorseDonkey";
                        break;
                    case ARROW:
                        nmsEntityName = "TippedArrow";
                        break;
                    case DROPPED_ITEM:
                        nmsEntityName = "Item";
                        break;
                    case FIREBALL:
                        nmsEntityName = "LargeFireball";
                        break;
                    case FIREWORK:
                        nmsEntityName = "Fireworks";
                        break;
                    case GIANT:
                        nmsEntityName = "GiantZombie";
                        break;
                    case HUSK:
                        nmsEntityName = "ZombieHusk";
                        break;
                    case ILLUSIONER:
                        nmsEntityName = "IllagerIllusioner";
                        break;
                    case LEASH_HITCH:
                        nmsEntityName = "Leash";
                        break;
                    case MINECART:
                        nmsEntityName = "MinecartRideable";
                        break;
                    case MINECART_COMMAND:
                        nmsEntityName = "MinecartCommandBlock";
                        break;
                    case MINECART_TNT:
                        nmsEntityName = "MinecartTNT";
                        break;
                    case MULE:
                        nmsEntityName = "HorseMule";
                        break;
                    case PRIMED_TNT:
                        nmsEntityName = "TNTPrimed";
                        break;
                    case PUFFERFISH:
                        nmsEntityName = "PufferFish";
                        break;
                    case SPLASH_POTION:
                        nmsEntityName = "Potion";
                        break;
                    case STRAY:
                        nmsEntityName = "SkeletonStray";
                        break;
                    case TRIDENT:
                        nmsEntityName = "ThrownTrident";
                        break;
                    default:
                        break;
                }

                if (nmsEntityName != null) {
                    nmsClass = ReflectionManager.getNmsClass("Entity" + nmsEntityName);
                }
            }

            try {
                if (disguiseType == DisguiseType.UNKNOWN) {
                    DisguiseValues disguiseValues = new DisguiseValues(disguiseType, null, 0, 0);

                    disguiseValues.setAdultBox(new FakeBoundingBox(0, 0, 0));

                    DisguiseSound sound = DisguiseSound.getType(disguiseType.name());

                    if (sound != null) {
                        sound.setDamageAndIdleSoundVolume(1f);
                    }

                    continue;
                }

                if (nmsEntityName == null) {
                    getLogger().warning("Entity name not found! (" + disguiseType.name() + ")");
                    continue;
                }

                Object nmsEntity = ReflectionManager.createEntityInstance(nmsEntityName);

                if (nmsEntity == null) {
                    getLogger().warning("Entity not found! (" + nmsEntityName + ")");
                    continue;
                }

                disguiseType.setTypeId(ReflectionManager.getEntityType(nmsEntity));
                Entity bukkitEntity = ReflectionManager.getBukkitEntity(nmsEntity);

                int entitySize = 0;

                for (Field field : ReflectionManager.getNmsClass("Entity").getFields()) {
                    if (field.getType().getName().equals("EnumEntitySize")) {
                        Enum enumEntitySize = (Enum) field.get(nmsEntity);

                        entitySize = enumEntitySize.ordinal();

                        break;
                    }
                }

                DisguiseValues disguiseValues = new DisguiseValues(disguiseType, nmsEntity.getClass(), entitySize,
                        bukkitEntity instanceof Damageable ? ((Damageable) bukkitEntity).getMaxHealth() : 0);

                WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(bukkitEntity);
                ArrayList<MetaIndex> indexes = MetaIndex.getFlags(disguiseType.getWatcherClass());

                for (WrappedWatchableObject watch : watcher.getWatchableObjects()) {
                    MetaIndex flagType = MetaIndex.getFlag(watcherClass, watch.getIndex());

                    if (flagType == null) {
                        getLogger().severe("MetaIndex not found for " + disguiseType + "! Index: " + watch.getIndex());
                        getLogger().severe("Value: " + watch.getRawValue() + " (" + watch.getRawValue().getClass() +
                                ") (" + nmsEntity.getClass() + ") & " + watcherClass.getSimpleName());
                        continue;
                    }

                    indexes.remove(flagType);

                    Object ourValue = ReflectionManager.convertInvalidMeta(flagType.getDefault());
                    Object nmsValue = ReflectionManager.convertInvalidMeta(watch.getValue());

                    if (ourValue != nmsValue &&
                            ((ourValue == null || nmsValue == null) || ourValue.getClass() != nmsValue.getClass())) {
                        getLogger().severe("[MetaIndex mismatch for " + disguiseType + "! Index: " + watch.getIndex());
                        getLogger().severe("MetaIndex: " + flagType.getDefault() + " (" +
                                flagType.getDefault().getClass() + ") (" + nmsEntity.getClass() + ") & " +
                                watcherClass.getSimpleName());
                        getLogger().severe("Minecraft: " + watch.getRawValue() + " (" + watch.getRawValue().getClass() +
                                ")");
                    }
                }

                for (MetaIndex index : indexes) {
                    getLogger().warning(
                            disguiseType + " has MetaIndex remaining! " + index.getFlagWatcher().getSimpleName() +
                                    " at index " + index.getIndex());
                }

                DisguiseSound sound = DisguiseSound.getType(disguiseType.name());

                if (sound != null) {
                    Float soundStrength = ReflectionManager.getSoundModifier(nmsEntity);

                    if (soundStrength != null) {
                        sound.setDamageAndIdleSoundVolume(soundStrength);
                    }
                }

                // Get the bounding box
                disguiseValues.setAdultBox(ReflectionManager.getBoundingBox(bukkitEntity));

                if (bukkitEntity instanceof Ageable) {
                    ((Ageable) bukkitEntity).setBaby();

                    disguiseValues.setBabyBox(ReflectionManager.getBoundingBox(bukkitEntity));
                } else if (bukkitEntity instanceof Zombie) {
                    ((Zombie) bukkitEntity).setBaby(true);

                    disguiseValues.setBabyBox(ReflectionManager.getBoundingBox(bukkitEntity));
                }

                disguiseValues.setEntitySize(ReflectionManager.getSize(bukkitEntity));
            }
            catch (SecurityException | IllegalArgumentException | IllegalAccessException | FieldAccessException ex) {
                getLogger().severe("Uh oh! Trouble while making values for the disguise " + disguiseType.name() + "!");
                getLogger().severe("Before reporting this error, " +
                        "please make sure you are using the latest version of LibsDisguises and ProtocolLib.");
                getLogger().severe("Development builds are available at (ProtocolLib) " +
                        "http://ci.dmulloy2.net/job/ProtocolLib/ and (LibsDisguises) https://ci.md-5" +
                        ".net/job/LibsDisguises/");

                ex.printStackTrace();
            }
        }
    }

    private String[] splitReadable(String string) {
        String[] split = string.split("_");

        for (int i = 0; i < split.length; i++) {
            split[i] = split[i].substring(0, 1) + split[i].substring(1).toLowerCase();
        }

        return split;
    }

    private String toReadable(String string) {
        return StringUtils.join(splitReadable(string));
    }

    public DisguiseListener getListener() {
        return listener;
    }

    /**
     * External APIs shouldn't actually need this instance. DisguiseAPI should be enough to handle most cases.
     *
     * @return The instance of this plugin
     */
    public static LibsDisguises getInstance() {
        return instance;
    }
}
