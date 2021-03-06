package net.canarymod.api;

import net.canarymod.Canary;
import net.canarymod.ToolBox;
import net.canarymod.api.inventory.CanaryOfflineEnderChestInventory;
import net.canarymod.api.inventory.CanaryOfflinePlayerInventory;
import net.canarymod.api.inventory.Inventory;
import net.canarymod.api.inventory.PlayerInventory;
import net.canarymod.api.nbt.CanaryBaseTag;
import net.canarymod.api.nbt.CanaryCompoundTag;
import net.canarymod.api.nbt.CanaryDoubleTag;
import net.canarymod.api.nbt.CompoundTag;
import net.canarymod.api.nbt.ListTag;
import net.canarymod.api.statistics.Achievement;
import net.canarymod.api.statistics.Achievements;
import net.canarymod.api.statistics.CanaryAchievement;
import net.canarymod.api.statistics.CanaryStat;
import net.canarymod.api.statistics.Stat;
import net.canarymod.api.statistics.Statistics;
import net.canarymod.api.world.CanaryWorld;
import net.canarymod.api.world.DimensionType;
import net.canarymod.api.world.UnknownWorldException;
import net.canarymod.api.world.World;
import net.canarymod.api.world.position.Location;
import net.canarymod.api.world.position.Position;
import net.canarymod.permissionsystem.PermissionProvider;
import net.canarymod.user.Group;
import net.canarymod.warp.Warp;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.SaveHandler;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static net.canarymod.Canary.log;

/**
 * Offline Player implementation
 *
 * @author Chris (damagefilter)
 * @author Jason (darkdiplomat)
 */
public class CanaryOfflinePlayer implements OfflinePlayer {

    private CanaryCompoundTag data;
    private PermissionProvider provider;
    private List<Group> groups = null;
    private String prefix = null;
    private String name;
    private boolean isMuted;
    private UUID uuid;
    private StatisticsFile statisticsFile;
    private Inventory playerInv, enderInv;

    public CanaryOfflinePlayer(String name, UUID uuid, CanaryCompoundTag tag) {
        this.data = tag;
        this.name = name;
        this.uuid = uuid;
        provider = Canary.permissionManager().getPlayerProvider(getUUIDString(), getWorld().getFqName());
        String[] data = Canary.usersAndGroups().getPlayerData(getUUIDString());
        Group[] subs = Canary.usersAndGroups().getModuleGroupsForPlayer(getUUIDString());
        groups = new LinkedList<Group>();
        groups.add(Canary.usersAndGroups().getGroup(data[1]));
        for (Group g : subs) {
            if (g != null) {
                groups.add(g);
            }
        }
        prefix = data[0];
        isMuted = Boolean.parseBoolean(data[2]);

        // Statistics file
        File file1 = new File(new File("worlds"), "stats");
        File file2 = new File(file1, uuid.toString() + ".json");

        if (!file2.exists()) {
            File file3 = new File(file1, name + ".json");

            if (file3.exists() && file3.isFile()) {
                file3.renameTo(file2);
            }
        }

        this.statisticsFile = new StatisticsFile(((CanaryServer)Canary.getServer()).getHandle(), file2);
        this.statisticsFile.a();

        // Initialize inventories
        this.playerInv = new CanaryOfflinePlayerInventory(this);
        this.enderInv = new CanaryOfflineEnderChestInventory(this);
    }

    @Override
    public PermissionProvider getPermissionProvider() {
        return provider;
    }

    @Override
    public Group getGroup() {
        return groups.get(0);
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public boolean hasPermission(String path) {
        return provider.queryPermission(path);
    }

    @Override
    public void setGroup(Group group) {
        this.groups.set(0, group);
        Canary.usersAndGroups().addOrUpdateOfflinePlayer(this);
    }

    @Override
    public void setPrefix(String prefix) {
        this.prefix = prefix;
        Canary.usersAndGroups().addOrUpdateOfflinePlayer(this);
    }

    @Override
    public World getWorld() {
        if (data == null) {
            return Canary.getServer().getDefaultWorld();
        }
        int dim = data.getInt("Dimension");
        String world = data.getString("LevelName");
        try {
            return Canary.getServer().getWorldManager().getWorld(world, DimensionType.fromId(dim), false);
        }
        catch (UnknownWorldException e) {
            return Canary.getServer().getDefaultWorld();
        }
    }

    @Override
    public Position getPosition() {
        if (data == null) {
            return new Position();
        }
        ListTag<? extends CanaryBaseTag> poslist = data.getListTag("Pos");
        Position p = new Position();
        p.setX(((CanaryDoubleTag)poslist.get(0)).getValue());
        p.setY(((CanaryDoubleTag)poslist.get(1)).getValue());
        p.setZ(((CanaryDoubleTag)poslist.get(2)).getValue());
        return p;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UUID getUUID() {
        String uuid = ToolBox.usernameToUUID(getName());
        return uuid == null ? null : UUID.fromString(uuid);
    }

    @Override
    public String getUUIDString() {
        return ToolBox.usernameToUUID(getName());
    }

    @Override
    public boolean isMuted() {
        return isMuted;
    }

    @Override
    public void setMuted(boolean muted) {
        this.isMuted = muted;
        Canary.usersAndGroups().addOrUpdateOfflinePlayer(this);
    }

    @Override
    public void addGroup(Group group) {
        if (!groups.contains(group)) {
            groups.add(group);
            Canary.usersAndGroups().addOrUpdateOfflinePlayer(this);
        }
    }

    @Override
    public Group[] getPlayerGroups() {
        return groups.toArray(new Group[groups.size()]);
    }

    @Override
    public boolean removeGroup(Group g) {
        boolean success = groups.remove(g);
        if (success) {
            Canary.usersAndGroups().addOrUpdateOfflinePlayer(this);
        }
        return success;
    }

    @Override
    public boolean removeGroup(String g) {
        Group gr = Canary.usersAndGroups().getGroup(g);
        if (gr == null) {
            return false;
        }
        return removeGroup(gr);
    }

    @Override
    public boolean isInGroup(Group group, boolean parents) {
        for (Group g : groups) {
            if (g.getName().equals(group.getName())) {
                return true;
            }
            if (parents) {
                for (Group parent : g.parentsToList()) {
                    if (parent.getName().equals(group.getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean isInGroup(String group, boolean parents) {
        for (Group g : groups) {
            if (g.getName().equals(group)) {
                return true;
            }
            if (parents) {
                for (Group parent : g.parentsToList()) {
                    if (parent.getName().equals(group)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean isOnline() {
        return Canary.getServer().getPlayer(name) != null;
    }

    @Override
    public CompoundTag getNBT() {
        return data;
    }

    public CompoundTag getMetaData() {
        return data.containsKey("Canary") ? data.getCompoundTag("Canary") : null;
    }

    @Override
    public void save() {
        if (isOnline()) {
            log.warn("Attempted to save an online player! (" + getName() + ")");
            return;
        }
        if (getNBT() != null) {
            ISaveHandler handler = ((CanaryWorld)getWorld()).getHandle().O();
            if (handler instanceof SaveHandler) {
                SaveHandler shandler = (SaveHandler)handler;
                shandler.writePlayerNbt(uuid, (CanaryCompoundTag)getNBT());
            }
            else {
                log.error(getName() + "'s OfflinePlayer could not be saved! Unsupported SaveHandler!");
            }
        }
    }

    @Override
    public String getFirstJoined() {
        if (getMetaData() != null && getMetaData().containsKey("FirstJoin")) {
            return getMetaData().getString("FirstJoin");
        }
        return "NEVER";
    }

    @Override
    public String getLastJoined() {
        if (getMetaData() != null && getMetaData().containsKey("LastJoin")) {
            return getMetaData().getString("LastJoin");
        }
        return "NEVER";
    }

    @Override
    public long getTimePlayed() {
        if (getMetaData() != null && getMetaData().containsKey("TimePlayed")) {
            return getMetaData().getLong("TimePlayed");
        }
        return 0;
    }

    @Override
    public int getModeId() {
        if (getNBT() != null && getNBT().containsKey("playerGameType")) {
            return getNBT().getInt("playerGameType");
        }
        return 0;
    }

    @Override
    public GameMode getMode() {
        return GameMode.fromId(getModeId());
    }

    @Override
    public void setMode(GameMode mode) {
        if (getNBT() != null) {
            getNBT().put("playerGameType", mode.getId());
            save();
        }
    }

    @Override
    public void setModeId(int mode) {
        if (getNBT() != null) {
            getNBT().put("playerGameType", mode);
            save();
        }
    }

    @Override
    public boolean isOperator() {
        return Canary.ops().isOpped(this);
    }

    @Override
    public boolean isAdmin() {
        return isOperator() || hasPermission("canary.super.administrator");
    }

    @Override
    public boolean canBuild() {
        return isAdmin() || hasPermission("canary.world.build");
    }

    @Override
    public void setCanBuild(boolean canModify) {
        provider.addPermission("canary.world.build", canModify);
    }

    @Override
    public boolean canIgnoreRestrictions() {
        return isAdmin() || hasPermission("canary.super.ignoreRestrictions");
    }

    @Override
    public void setCanIgnoreRestrictions(boolean canIgnore) {
        provider.addPermission("canary.super.ignoreRestrictions", canIgnore, -1);
    }

    @Override
    public void addExhaustion(float exhaustion) {
        if (getNBT() != null) {
            float oldEx = getExhaustionLevel();
            getNBT().put("foodExhaustionLevel", oldEx + exhaustion);
            save();
        }
    }

    @Override
    public void setExhaustion(float exhaustion) {
        if (getNBT() != null) {
            getNBT().put("foodExhaustionLevel", exhaustion);
            save();
        }
    }

    @Override
    public float getExhaustionLevel() {
        if (getNBT() != null && getNBT().containsKey("foodExhaustionLevel")) {
            return getNBT().getFloat("foodExhaustionLevel");
        }
        return 0;
    }

    @Override
    public void addSaturation(float saturation) {
        if (getNBT() != null) {
            float oldSat = getSaturationLevel();
            getNBT().put("foodSaturationLevel", oldSat + saturation);
            save();
        }
    }

    @Override
    public void setSaturation(float saturation) {
        if (getNBT() != null) {
            getNBT().put("foodSaturationLevel", saturation);
            save();
        }
    }

    @Override
    public float getSaturationLevel() {
        if (getNBT() != null && getNBT().containsKey("foodSaturationLevel")) {
            return getNBT().getFloat("foodSaturationLevel");
        }
        return 0;
    }

    @Override
    public void setHunger(int hunger) {
        if (getNBT() != null) {
            getNBT().put("foodLevel", hunger);
            save();
        }
    }

    @Override
    public int getHunger() {
        if (getNBT() != null && getNBT().containsKey("foodLevel")) {
            return getNBT().getInt("foodLevel");
        }
        return 0;
    }

    @Override
    public void addExperience(int experience) {
        if (getNBT() != null) {
            int oldXp = getExperience();
            getNBT().put("XpTotal", oldXp + experience);
            save();
        }
    }

    @Override
    public void removeExperience(int experience) {
        if (getNBT() != null) {
            int oldXp = getExperience();
            getNBT().put("XpTotal", oldXp - experience);
            save();
        }
    }

    @Override
    public int getExperience() {
        if (getNBT() != null && getNBT().containsKey("XpTotal")) {
            return getNBT().getInt("XpTotal");
        }
        return 0;
    }

    @Override
    public void setExperience(int xp) {
        if (getNBT() != null) {
            getNBT().put("XpTotal", xp);
            save();
        }
    }

    @Override
    public int getLevel() {
        if (getNBT() != null && getNBT().containsKey("XpLevel")) {
            return getNBT().getInt("XpLevel");
        }
        return 0;
    }

    @Override
    public void setLevel(int lvl) {
        if (getNBT() != null) {
            getNBT().put("XpLevel", lvl);
            save();
        }
    }

    @Override
    public void addLevel(int lvl) {
        if (getNBT() != null) {
            setLevel(getLevel() + lvl);
        }
    }

    @Override
    public void removeLevel(int lvl) {
        if (getNBT() != null) {
            setLevel(getLevel() - lvl);
        }
    }

    @Override
    public void setHome(Location location) {

    }

    @Override
    public Location getHome() {
        Warp home = Canary.warps().getHome(getName());

        if (home != null) {
            return home.getLocation();
        }
        return Canary.getServer().getDefaultWorld().getSpawnLocation(); //TODO: Read NBT to get real position
    }

    @Override
    public boolean hasHome() {
        return Canary.warps().getHome(getName()) != null;
    }

    @Override
    public String[] getAllowedIPs() { //TODO: find out why this isn't implemented
        return new String[0];
    }

    @Override
    public String getIP() {
        if (getMetaData() != null && getMetaData().containsKey("PreviousIP")) {
            return getMetaData().getString("PreviousIP");
        }
        return "UNKNOWN";
    }

    @Override
    public float getHealth() {
        if (getNBT() != null) {
            return getNBT().getFloat("HealF");
        }
        return 0.0F;
    }

    @Override
    public void setHealth(float health) {
        if (getNBT() != null) {
            float newHealth = Math.max(0, health);
            getNBT().put("HealF", newHealth);
            getNBT().put("Health", (short)((int)Math.ceil((double)newHealth)));
        }
    }

    @Override
    public void increaseHealth(float health) {
        if (getNBT() != null) {
            float newHealth = Math.max(0, getHealth() + health);
            getNBT().put("HealF", newHealth);
            getNBT().put("Health", (short)((int)Math.ceil((double)newHealth)));
        }
    }

    @Override
    public void setStat(Stat stat, int value) {
        statisticsFile.a(null, ((CanaryStat)stat).getHandle(), value);
    }

    @Override
    public void setStat(Statistics statistics, int value) {
        setStat(statistics.getInstance(), value);
    }

    @Override
    public void increaseStat(Stat stat, int value) {
        if (value < 0) {
            return;
        }
        setStat(stat, getStat(stat) + value);
    }

    @Override
    public void increaseStat(Statistics statistics, int value) {
        increaseStat(statistics.getInstance(), value);
    }

    @Override
    public void decreaseStat(Stat stat, int value) {
        if (value < 0) {
            return;
        }
        setStat(stat, getStat(stat) - value);
    }

    @Override
    public void decreaseStat(Statistics statistics, int value) {
        decreaseStat(statistics.getInstance(), value);
    }

    @Override
    public int getStat(Stat stat) {
        return statisticsFile.a(((CanaryStat)stat).getHandle());
    }

    @Override
    public int getStat(Statistics statistics) {
        return getStat(statistics.getInstance());
    }

    @Override
    public void awardAchievement(Achievement achievement) {
        // Need to give all parent achievements
        if (achievement.getParent() != null && !hasAchievement(achievement.getParent())) {
            awardAchievement(achievement.getParent());
        }
        statisticsFile.b(null, ((CanaryAchievement)achievement).getHandle(), 1);
        statisticsFile.b((EntityPlayerMP) null);
    }

    @Override
    public void awardAchievement(Achievements achievements) {
        awardAchievement(achievements.getInstance());
    }

    @Override
    public PlayerInventory getInventory() {
        return (PlayerInventory)this.playerInv;
    }

    @Override
    public Inventory getEnderChestInventory() {
        return this.enderInv;
    }

    @Override
    public void removeAchievement(Achievement achievement) {
        // Need to remove any children achievements
        for (Achievements achieve : Achievements.values()) {
            Achievement child = achieve.getInstance();
            if (child.getParent() == achievement && hasAchievement(child)) {
                removeAchievement(child);
            }
        }
        statisticsFile.a(null, ((CanaryAchievement) achievement).getHandle(), 0);
    }

    @Override
    public void removeAchievement(Achievements achievements) {
        removeAchievement(achievements.getInstance());
    }

    @Override
    public boolean hasAchievement(Achievement achievement) {
        return statisticsFile.a(((CanaryAchievement)achievement).getHandle());
    }

    @Override
    public boolean hasAchievement(Achievements achievements) {
        return hasAchievement(achievements.getInstance());
    }
}
