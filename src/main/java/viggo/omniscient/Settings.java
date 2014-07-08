package viggo.omniscient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Settings implements Serializable {
    private final Set<String> defaultKeys;
    public String dbHost;
    public int dbPort;
    public String dbCatalog;
    public String dbUser;
    public String dbPassword;
    public boolean blockLimitsEnabled;
    public boolean blockStatsEnabled;
    public boolean autoWhiteListOffEnabled;
    public int autoWhiteListOffDelaySeconds;
    public boolean scanChunksOnLoad;
    public boolean scanChunksPeriodicallyEnabled;
    public int scanChunksPeriodicallyDelaySeconds;
    public int scanChunksPeriodicallyIntervalSeconds;
    public int scanChunksMinimumIntervalForSameChunkSeconds;
    public Set<Integer> blockIdScanBlackList;
    public Set<Integer> blockIdEventBlackList;
    public boolean autoRemoveUnknownBlocksEnabled;
    public boolean autoReplaceUnknownBlocksEnabled;
    public int autoReplaceUnknownBlocksId;
    public int autoReplaceUnknownBlocksSubValue;
    public boolean autoReplaceUnknownBlocksWithSignEnabled;
    public boolean enablePlayerInfoOnBlockEvents;
    public String devUser;
    public boolean syncRemovedBlocksPeriodicallyEnabled;
    public int syncRemovedBlocksPeriodicallyIntervalSeconds;
    public boolean syncRemovedBlocksOnEventsEnabled;
    public boolean doAllowEmptyBlockInfo;
    public int notificationIntervalSeconds;
    public int maximumUnknownBlocksToProcessPerTick;
    public int maximumUnknownBlocksToProcessBeforeSafetySwitch;
    public boolean dumpUnknownBlockInfoToDiskOnSafetySwitch;
    public boolean autoAssignUnknownBlocksToOmniscientFakePlayerEnabled;
    public boolean disableWitherSound;
    public boolean disableEnderDragonSound;
    public boolean interactionToolEnabled;
    public int interactionToolItemId;
    public int interactionToolItemSubValue;
    public boolean broadcastOnSoundDisablingEnabled;
    public int onlinePlayersLimitMembers;
    public int onlinePlayersLimitSupporters;

    private Plugin plugin;


    public Settings(Plugin plugin) throws Exception {

        this.plugin = plugin;

        if (plugin.getConfig().getDefaults() != null) {
            this.defaultKeys = plugin.getConfig().getDefaults().getKeys(true);
        } else {
            this.defaultKeys = plugin.getConfig().getKeys(true);

            if (defaultKeys == null) {
                throw new Exception("Failed to read default configuration");
            }
        }
    }

    public void load(boolean saveNewDefaults) throws Exception {

        // make sure a config exists
        plugin.saveDefaultConfig();


        // save config to persist any new defaults added
        if (saveNewDefaults) {
            plugin.getConfig().options().copyDefaults(true);
            plugin.saveConfig();
        }

        // reload config
        plugin.reloadConfig();


        this.verifyKeysPresent();


        this.dbHost = getString("dbHost");
        this.dbPort = getInt("dbPort");
        this.dbCatalog = getString("dbCatalog");
        this.dbUser = getString("dbUser");
        this.dbPassword = getString("dbPassword");
        this.blockLimitsEnabled = getBoolean("blockLimitsEnabled");
        this.blockStatsEnabled = getBoolean("blockStatsEnabled");
        this.autoWhiteListOffEnabled = getBoolean("autoWhiteListOff");
        this.autoWhiteListOffDelaySeconds = getInt("autoWhiteListOffDelaySeconds");
        this.scanChunksOnLoad = getBoolean("scanChunksOnLoad");
        this.scanChunksPeriodicallyEnabled = getBoolean("scanChunksPeriodicallyEnabled");
        this.scanChunksPeriodicallyDelaySeconds = getInt("scanChunksPeriodicallyDelaySeconds");
        this.scanChunksPeriodicallyIntervalSeconds = getInt("scanChunksPeriodicallyIntervalSeconds");
        this.scanChunksMinimumIntervalForSameChunkSeconds = getInt("scanChunksMinimumIntervalForSameChunkSeconds");
        this.autoRemoveUnknownBlocksEnabled = getBoolean("autoRemoveUnknownBlocksEnabled");
        this.autoReplaceUnknownBlocksEnabled = getBoolean("autoReplaceUnknownBlocksEnabled");
        this.autoReplaceUnknownBlocksId = getInt("autoReplaceUnknownBlocksId");
        this.autoReplaceUnknownBlocksSubValue = getInt("autoReplaceUnknownBlocksSubValue");
        this.autoReplaceUnknownBlocksWithSignEnabled = getBoolean("autoReplaceUnknownBlocksWithSignEnabled");
        this.autoAssignUnknownBlocksToOmniscientFakePlayerEnabled = getBoolean("autoAssignUnknownBlocksToOmniscientFakePlayerEnabled");

        this.syncRemovedBlocksPeriodicallyEnabled = getBoolean("syncRemovedBlocksPeriodicallyEnabled");
        this.syncRemovedBlocksPeriodicallyIntervalSeconds = getInt("syncRemovedBlocksPeriodicallyIntervalSeconds");
        this.syncRemovedBlocksOnEventsEnabled = getBoolean("syncRemovedBlocksOnEventsEnabled");
        this.doAllowEmptyBlockInfo = getBoolean("doAllowEmptyBlockInfo");

        this.notificationIntervalSeconds = getInt("notificationIntervalSeconds");

        this.enablePlayerInfoOnBlockEvents = getBoolean("enablePlayerInfoOnBlockEvents");
        this.devUser = getString("devUser");

        this.blockIdScanBlackList = getListWithIntegerRanges("blockIdScanBlackList", false);
        this.blockIdEventBlackList = getListWithIntegerRanges("blockIdEventBlackList", true);

        this.maximumUnknownBlocksToProcessPerTick = getInt("maximumUnknownBlocksToProcessPerTick");
        this.maximumUnknownBlocksToProcessBeforeSafetySwitch = getInt("maximumUnknownBlocksToProcessBeforeSafetySwitch");
        this.dumpUnknownBlockInfoToDiskOnSafetySwitch = getBoolean("dumpUnknownBlockInfoToDiskOnSafetySwitch");

        this.disableWitherSound = getBoolean("disableWitherSound");
        this.disableEnderDragonSound = getBoolean("disableEnderDragonSound");
        this.broadcastOnSoundDisablingEnabled = getBoolean("broadcastOnSoundDisablingEnabled");

        this.interactionToolEnabled = getBoolean("interactionToolEnabled");
        this.interactionToolItemId = getInt("interactionToolItemId");
        this.interactionToolItemSubValue = getInt("interactionToolItemSubValue");

        this.onlinePlayersLimitMembers = getInt("onlinePlayersLimitMembers");
        this.onlinePlayersLimitSupporters = getInt("onlinePlayersLimitSupporters");
    }

    private void verifyKeysPresent() throws Exception {

        ArrayList<String> keysMissing = new ArrayList<String>();

        // has all keys defined?
        for (String key : defaultKeys) {
            if (!plugin.getConfig().contains(key)) {
                keysMissing.add(key);
            }
        }

        if (keysMissing.size() > 0) {
            throw new Exception("Missing configuration keys: " + Utils.join(keysMissing, ", "));
        }
    }

    private int getInt(String path) throws Exception {
        if (!plugin.getConfig().isInt(path)) {
            throw new Exception("Invalid integer in configuration: " + path);
        }
        return this.plugin.getConfig().getInt(path);
    }

    private String getString(String path) throws Exception {

        if (this.plugin.getConfig().getString(path) == null) {
            throw new Exception("Invalid string in configuration: " + path);
        }

        return this.plugin.getConfig().getString(path);
    }

    private boolean getBoolean(String path) throws Exception {
        if (!plugin.getConfig().isBoolean(path)) {
            throw new Exception("Invalid boolean in configuration: " + path);
        }
        return this.plugin.getConfig().getBoolean(path);
    }

    private Set<Integer> getListWithIntegerRanges(String path, boolean allowEmpty) throws Exception {

        Set<Integer> set = new HashSet<Integer>();

        final String stringRaw = this.plugin.getConfig().getString(path);

        if (stringRaw == null || stringRaw.length() < 1) {
            if (!allowEmpty) {
                throw new Exception("Invalid string with ranges in configuration: " + path);
            } else {
                return set;
            }
        }

        final String string = stringRaw.replace(" ", "");

        if (string == null || string.length() < 1) {
            throw new Exception("Invalid length of string with ranges in configuration: " + path);
        }

        if (string.contains(",")) {
            final String[] ranges = string.split(",");

            if (ranges.length < 1) {
                throw new Exception("Invalid range length detected in string with ranges in configuration: " + path);
            }

            for (String range : ranges) {

                if (range == null) {
                    throw new Exception("Null range detected in string with ranges in configuration: " + path);
                }

                parseRange(path, set, range);
            }
        } else {
            parseRange(path, set, string);
        }

        return set;
    }

    private void parseRange(String path, Set<Integer> set, String range) throws Exception {
        if (range.contains("-")) {
            // its an actual range with '-'

            final String[] rangeParts = range.split("-");
            if (rangeParts.length != 2) {
                throw new Exception("Invalid range detected in string with ranges in configuration: " + path);
            }

            int begin = Integer.parseInt(rangeParts[0]);
            int end = Integer.parseInt(rangeParts[1]);

            for (int i = begin; i <= end; i++) {
                set.add(i);
            }
        } else {
            set.add(Integer.parseInt(range));
        }
    }
}
