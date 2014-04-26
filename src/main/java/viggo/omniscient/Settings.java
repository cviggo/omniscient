package viggo.omniscient;

public class Settings {
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
    public boolean autoRemoveUnknownBlocksEnabled;
    public boolean autoReplaceUnknownBlocksEnabled;
    public int autoReplaceUnknownBlocksId;
    public int autoReplaceUnknownBlocksSubValue;
    public boolean autoReplaceUnknownBlocksWithSignEnabled;
    public boolean autoSync;
    public boolean enablePlayerInfoOnBlockEvents;
    public String devUser;

    public static Settings load(Plugin plugin) {
        Settings settings = new Settings();

        settings.dbHost = plugin.getConfig().getString("dbHost");
        settings.dbPort = plugin.getConfig().getInt("dbPort");
        settings.dbCatalog = plugin.getConfig().getString("dbCatalog");
        settings.dbUser = plugin.getConfig().getString("dbUser");
        settings.dbPassword = plugin.getConfig().getString("dbPassword");
        settings.blockLimitsEnabled = plugin.getConfig().getBoolean("blockLimitsEnabled");
        settings.blockStatsEnabled = plugin.getConfig().getBoolean("blockStatsEnabled");
        settings.autoWhiteListOffEnabled = plugin.getConfig().getBoolean("autoWhiteListOff");
        settings.autoWhiteListOffDelaySeconds = plugin.getConfig().getInt("autoWhiteListOffDelaySeconds");
        settings.scanChunksOnLoad = plugin.getConfig().getBoolean("scanChunksOnLoad");
        settings.scanChunksPeriodicallyEnabled = plugin.getConfig().getBoolean("scanChunksPeriodicallyEnabled");
        settings.scanChunksPeriodicallyDelaySeconds = plugin.getConfig().getInt("scanChunksPeriodicallyDelaySeconds");
        settings.scanChunksPeriodicallyIntervalSeconds = plugin.getConfig().getInt("scanChunksPeriodicallyIntervalSeconds");
        settings.scanChunksMinimumIntervalForSameChunkSeconds = plugin.getConfig().getInt("scanChunksMinimumIntervalForSameChunkSeconds");
        settings.autoRemoveUnknownBlocksEnabled = plugin.getConfig().getBoolean("autoRemoveUnknownBlocksEnabled");
        settings.autoReplaceUnknownBlocksEnabled = plugin.getConfig().getBoolean("autoReplaceUnknownBlocksEnabled");
        settings.autoReplaceUnknownBlocksId = plugin.getConfig().getInt("autoReplaceUnknownBlocksId");
        settings.autoReplaceUnknownBlocksSubValue = plugin.getConfig().getInt("autoReplaceUnknownBlocksSubValue");
        settings.autoReplaceUnknownBlocksWithSignEnabled = plugin.getConfig().getBoolean("autoReplaceUnknownBlocksWithSignEnabled");
        settings.autoSync = plugin.getConfig().getBoolean("autoSync");
        settings.enablePlayerInfoOnBlockEvents = plugin.getConfig().getBoolean("enablePlayerInfoOnBlockEvents");
        settings.devUser = plugin.getConfig().getString("devUser");

        return settings;
    }
}
