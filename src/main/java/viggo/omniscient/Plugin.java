package viggo.omniscient;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("deprecation")
public class Plugin extends JavaPlugin implements Listener {

    private static final Integer WAIT_FOR_ENGINE_TO_STOP_TIMEOUT_MSECS = 100;
    private static int TICKS_PER_SECOND = 20;
    public Logger logger = null;
    public Settings settings;
    public DatabaseEngine databaseEngine;
    public WorldScannerEngine worldScannerEngine;
    public AtomicInteger unknownBlocksProcessingState = new AtomicInteger();
    public AtomicInteger worldScannerState = new AtomicInteger();
    public ConcurrentLinkedQueue<String> broadcastQueue = new ConcurrentLinkedQueue<String>();
    /**
     * Map from blockKey to player name. This is used to perform a lookup from a world position to a player name and info.
     */
    ConcurrentHashMap<String, BlockInfo> playerToBlockCoordsMap;
    /**
     * Map from player name to Map of block types, which then maps to a list of block info.
     */
    Map<String, BlockGroupAndInfos> playerBlocks;
    Map<String, BlockLimit> blockLimits;
    Map<String, BlockStat> blockStats;
    private Map<String, BlockLimitGroup> blockLimitGroups;
    private ConcurrentLinkedQueue<BlockInfo> unknownBlocksFound;
    private BukkitTask worldScanSchedule;
    private BukkitTask syncSchedule;
    private Set<String> debugPlayers;
    private volatile PluginState state;
    private BukkitTask tickSchedule;
    private ProtocolHook protocolHook;
    private Date lastBroadcast = new Date();

    public void onDisable() {
        logger.logInfo("Disable invoked. Stopping engines.");
        setState(PluginState.Disabled);

        if (worldScannerEngine != null) {
            try {
                worldScannerEngine.stop(WAIT_FOR_ENGINE_TO_STOP_TIMEOUT_MSECS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (databaseEngine != null) {

            while (true) {
                try {
                    databaseEngine.stop(1000);
                    break;
                } catch (InterruptedException e) {
                    getServer().broadcastMessage(ChatColor.RED + "Waiting for Omniscient Database Engine to finish. Please hold on!");
                }
            }
        }


        logger.logInfo("Disabled");
    }

    public void onEnable() {
        logger = new Logger(this, getLogger());
        setState(PluginState.Unknown);

        // make sure datafolder is present
        getDataFolder().mkdirs();


        // register commands
        getCommand("omni").setExecutor(new viggo.omniscient.CommandExecutor(this, getLogger()));

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);

        // reload synchronously
        setState(PluginState.Reloading);
        if (!reload(null, false, true)) {
            logger.logSevere("Failed to load. Please fix the problem (likely shown above).");
            startNotifyScheduler(10);
        } else {

            if (settings.autoWhiteListOffEnabled) {
                DelayedWhiteListEnabler delayedWhiteListEnabler = new DelayedWhiteListEnabler(this);
                BukkitTask task = Bukkit.getScheduler().runTaskLater(this, delayedWhiteListEnabler, 20 * settings.autoWhiteListOffDelaySeconds);
                getServer().broadcastMessage("Omniscient will automatically disable white listing in %d seconds.");
            }

            startNotifyScheduler(settings.notificationIntervalSeconds);
            setState(PluginState.Running);
        }


        logger.logInfo("Enabled: " + getDescription().getVersion() + ", Current state is: " + state);
    }

    @Override
    public void onLoad() {

        try {
            final Class<?> aClass = Class.forName("com.comphenix.protocol.ProtocolLibrary", false, this.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            getLogger().warning("Omniscient cannot listen to network events as ProtocolLib was not found. Add ProtocolLib to plugins to fix.");
            return;
        }

        this.protocolHook = new ProtocolHook(this);
        protocolHook.load();

        getLogger().info("Added packet listener");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        try {
            final Player player = event.getPlayer();
            logger.logInfo("Player logging in: " + player.getName());

            if (player.isOp()) {
                return;
            }

            new BukkitRunnable() {

                @Override
                public void run() {

                    int memberLimit = 35;
                    int supporterLimit = 50;

                    final int onlinePlayerCount = getServer().getOnlinePlayers().length;

                    final boolean isSupporter = player.hasPermission("group.supporter_1");

                    if (isSupporter && onlinePlayerCount <= supporterLimit) {
                        return;
                    }

                    if (onlinePlayerCount <= memberLimit) {
                        return;
                    }

                    if (!isSupporter) {
                        player.kickPlayer("Server is full. Limit for members is: " + memberLimit);
                    } else {
                        player.kickPlayer("Server is full. Limit for supporters is: " + supporterLimit);
                    }

                }

            }.runTaskLater(this, 20);

        } catch (Exception e) {
            logger.logSevere(e);
        }
    }

    private void startNotifyScheduler(int notificationIntervalSeconds) {
        new BukkitRunnable() {

            @Override
            public void run() {
                try {

                    if (getState() != PluginState.Running) {

                        String message = ChatColor.RED + "";

                        switch (getState()) {

                            case SafetyModeEmptyBlockInfo:

                                message +=
                                        "Omniscient is in safety mode because information about limited blocks could not be loaded."
                                                + " This is a normal event when first starting to use Omniscient. If you just started using Omniscient:  " +
                                                "issue the command \"/omni reload ignoreEmptyBlockInfo\"";
                                break;

                            default:
                                message += String.format("Omniscient is in %s state. ", getState().toString());
                                break;
                        }

                        logger.logWarn(message);
                        getServer().broadcastMessage(message);

                    }

                } catch (Throwable t) {
                    logger.logSevere(t);
                }
            }

        }.runTaskTimer(this, TICKS_PER_SECOND * 10, TICKS_PER_SECOND * notificationIntervalSeconds);
    }

    public boolean isBlockLimitedAndUnknown(BlockInfo blockInfo) {
        return blockLimits.containsKey(blockInfo.blockId) && !playerToBlockCoordsMap.containsKey(getBlockKeyFromInfo(blockInfo));
    }

    public boolean processUnknownBlocks() {

        // CAS to prevent reentry / buildup by commands and/or schedule
        if (!unknownBlocksProcessingState.compareAndSet(0, 1)) {
            logger.logWarn("no re-entry allowed in processUnknownBlocks");
            return false;
        }

        int blockFixCount = 0;

        try {

            if (unknownBlocksFound != null && unknownBlocksFound.size() > 0) {

                if (!settings.autoRemoveUnknownBlocksEnabled && !settings.autoReplaceUnknownBlocksEnabled && !settings.autoReplaceUnknownBlocksWithSignEnabled && !settings.autoAssignUnknownBlocksToOmniscientFakePlayerEnabled) {
                    unknownBlocksFound.clear();

                    unknownBlocksProcessingState.set(0);
                    return true;
                }

                if (settings.autoRemoveUnknownBlocksEnabled) {

                    while (unknownBlocksFound.size() > 0) {

                        if (blockFixCount++ > settings.maximumUnknownBlocksToProcessPerTick) {
                            unknownBlocksProcessingState.set(0);
                            return true;
                        }

                        final BlockInfo blockInfo = unknownBlocksFound.poll();

                        // perform a safety check that the block is still unknown
                        if (!isBlockLimitedAndUnknown(blockInfo)) {
                            continue;
                        }

                        World world = getServer().getWorld(blockInfo.world);

                        Block block = world.getBlockAt(blockInfo.x, blockInfo.y, blockInfo.z);

//                        if (block.getTypeId() == 85){
//                            continue;
//                        }

                        logger.logWarn(String.format("Removing block of type: %s @ %s:%d.%d.%d",
                                        blockInfo.blockId, blockInfo.world, blockInfo.x, blockInfo.y, blockInfo.z)
                        );

                        block.setType(Material.AIR);
                    }

                    unknownBlocksProcessingState.set(0);
                    return true;

                }

                if (settings.autoReplaceUnknownBlocksEnabled) {

                    while (unknownBlocksFound.size() > 0) {

                        if (blockFixCount++ > settings.maximumUnknownBlocksToProcessPerTick) {
                            unknownBlocksProcessingState.set(0);
                            return true;
                        }

                        final BlockInfo blockInfo = unknownBlocksFound.poll();

                        // perform a safety check that the block is still unknown
                        if (!isBlockLimitedAndUnknown(blockInfo)) {
                            continue;
                        }

                        if (settings.autoReplaceUnknownBlocksId < 0) {
                            continue;
                        }

                        logger.logWarn(String.format("Replacing block of type: %s @ %s:%d.%d.%d",
                                        blockInfo.blockId, blockInfo.world, blockInfo.x, blockInfo.y, blockInfo.z)
                        );

                        World world = getServer().getWorld(blockInfo.world);

                        Block block = world.getBlockAt(blockInfo.x, blockInfo.y, blockInfo.z);

                        block.setTypeIdAndData(settings.autoReplaceUnknownBlocksId, (byte) settings.autoReplaceUnknownBlocksSubValue, false);
                    }

                    unknownBlocksProcessingState.set(0);
                    return true;
                }

                if (settings.autoReplaceUnknownBlocksWithSignEnabled) {
                    while (unknownBlocksFound.size() > 0) {

                        if (blockFixCount++ > settings.maximumUnknownBlocksToProcessPerTick) {
                            unknownBlocksProcessingState.set(0);
                            return true;
                        }

                        final BlockInfo blockInfo = unknownBlocksFound.poll();

                        // perform a safety check that the block is still unknown
                        if (!isBlockLimitedAndUnknown(blockInfo)) {
                            continue;
                        }

                        logger.logWarn(String.format("Replacing block of type: %s @ %s:%d.%d.%d with a sign.",
                                        blockInfo.blockId, blockInfo.world, blockInfo.x, blockInfo.y, blockInfo.z)
                        );

                        World world = getServer().getWorld(blockInfo.world);

                        Block block = world.getBlockAt(blockInfo.x, blockInfo.y, blockInfo.z);

                        final BlockFace blockFace = block.getFace(block);

                        Block blockBelow = world.getBlockAt(blockInfo.x, Math.max(blockInfo.y - 1, 0), blockInfo.z);

                        if (blockBelow.getType() == Material.AIR) {
                            block.setTypeIdAndData(settings.autoReplaceUnknownBlocksId, (byte) settings.autoReplaceUnknownBlocksSubValue, false);
                            continue;
                        }


                        final String blockIdFromBlock = getBlockIdFromBlock(block);
                        block.setType(Material.AIR);
                        block.setType(Material.SIGN_POST);

                        Sign sign = (Sign) block.getState();

                        final org.bukkit.material.Sign signData = new org.bukkit.material.Sign(Material.SIGN_POST);
                        signData.setFacingDirection(blockFace);
                        sign.setData(signData);

                        sign.setLine(0, blockIdFromBlock);
                        sign.setLine(1, "was replaced by");
                        sign.setLine(2, "Omniscient");

                        java.text.SimpleDateFormat sdf =
                                new java.text.SimpleDateFormat("dd/MM HH:mm:ss");

                        String time = sdf.format(new Date());

                        sign.setLine(3, time);


                        sign.update(true, false);


                    }

                    unknownBlocksProcessingState.set(0);
                    return true;
                }

                if (settings.autoAssignUnknownBlocksToOmniscientFakePlayerEnabled) {

                    while (unknownBlocksFound.size() > 0) {

                        if (blockFixCount++ > settings.maximumUnknownBlocksToProcessPerTick) {
                            unknownBlocksProcessingState.set(0);
                            return true;
                        }

                        final BlockInfo blockInfo = unknownBlocksFound.poll();

                        // perform a safety check that the block is still unknown
                        if (!isBlockLimitedAndUnknown(blockInfo)) {
                            continue;
                        }

                        String playerName = "FakePlayer";
                        blockInfo.placedBy = playerName;
                        blockInfo.placedWhen = new Date();

                        // HACK: code copy
                        // make sure there is a map for the player
                        if (!playerBlocks.containsKey(playerName)) {
                            BlockGroupAndInfos blockGroupAndInfos = new BlockGroupAndInfos();
                            blockGroupAndInfos.groupMap = new HashMap<String, GroupCount>();
                            blockGroupAndInfos.blockMap = new HashMap<String, ArrayList<BlockInfo>>();

                            playerBlocks.put(playerName, blockGroupAndInfos);
                        }

                        BlockGroupAndInfos groupAndInfos = playerBlocks.get(playerName);

//                        /* if a limit exist for this type of block, we also add to the group count */
//                        BlockLimit limit = blockLimits.get(blockInfo.blockId);
//                        if (limit != null) {
//                            Map<String, GroupCount> groupMap = groupAndInfos.groupMap;
//
//                            GroupCount groupCount = groupMap.get(limit.limitGroup);
//
//                            if (groupCount == null){
//                                groupCount = new GroupCount();
//                                groupMap.put(limit.limitGroup, groupCount);
//                            }
//
//                            groupCount.value++;
//                        }


                        Map<String, ArrayList<BlockInfo>> map = groupAndInfos.blockMap;

                        // make sure there is a list available for the type of block
                        if (!map.containsKey(blockInfo.blockId)) {
                            ArrayList<BlockInfo> list = new ArrayList<BlockInfo>();
                            map.put(blockInfo.blockId, list);
                        }

                        ArrayList<BlockInfo> blockList = map.get(blockInfo.blockId);


                        //BlockInfo blockInfo = new BlockInfo(0, blockId, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), playerName, new Date());

                        // add to list of blocks
                        blockList.add(blockInfo);

                        // add to coordinate to player map
                        playerToBlockCoordsMap.put(getBlockKeyFromInfo(blockInfo), blockInfo);

                        // add to database
                        databaseEngine.setBlockInfo(blockInfo);


                    }

                    unknownBlocksProcessingState.set(0);
                    return true;

                }
            }

            unknownBlocksProcessingState.set(0);
            return true;

        } catch (Throwable t) {
            logger.logSevere(t);
            unknownBlocksProcessingState.set(0);
            return false;
        }
    }

    private BukkitTask createWorldScannerSchedule() {
        return new BukkitRunnable() {

            @Override
            public void run() {
                try {

                    if (!settings.scanChunksPeriodicallyEnabled) {
                        return;
                    }

                    // only enqueue chunks if not already busy
                    if (!worldScannerState.compareAndSet(0, 1)) {
                        return;
                    }

                    final List<World> worldList = getServer().getWorlds();

                    for (World world : worldList) {
                        final Chunk[] loadedChunks = world.getLoadedChunks();
                        for (Chunk loadedChunk : loadedChunks) {
                            worldScannerEngine.queueChunkForScanning(loadedChunk, true, true);
                        }
                    }

                } catch (Throwable t) {
                    logger.logSevere(t);
                }
            }

        }.runTaskTimer(this, TICKS_PER_SECOND * settings.scanChunksPeriodicallyDelaySeconds, TICKS_PER_SECOND * settings.scanChunksPeriodicallyIntervalSeconds);
    }

    private BukkitTask createSyncSchedule(int delaySeconds, int intervalSeconds) {
        return new BukkitRunnable() {

            @Override
            public void run() {
                try {

                    if (databaseEngine.getState() != DatabaseEngine.DataBaseEngineState.Running) {
                        setState(PluginState.DatabaseConnectionPending);
                        return;
                    } else {
                        if (state == PluginState.DatabaseConnectionPending) {
                            setState(PluginState.Running);
                            return;
                        }
                    }

                    if (settings.syncRemovedBlocksPeriodicallyEnabled) {
                        syncRemovedBlocks(null);
                    }

                    //final long playerBlockBytes = MemoryMeasurer.measureBytes(playerBlocks);

                } catch (Throwable t) {
                    logger.logSevere(t);
                }
            }

        }.runTaskTimer(this, TICKS_PER_SECOND * delaySeconds, TICKS_PER_SECOND * intervalSeconds);
    }

    private BukkitTask createTickSchedule() {
        return new BukkitRunnable() {

            @Override
            public void run() {
                try {

                    processUnknownBlocks();

                    // HACK: broadcast once every 5 secs at most
                    if (
                            (new Date().getTime() - lastBroadcast.getTime()) > 5000
                                    && broadcastQueue.size() > 0) {
                        final String broadcastString = broadcastQueue.poll();
                        getServer().broadcastMessage(broadcastString);
                    }

                    broadcastQueue.clear();


                } catch (Throwable t) {
                    logger.logSevere(t);
                }
            }

        }.runTaskTimer(this, TICKS_PER_SECOND * 10, 1);
    }

    public String getBlockKeyFromInfo(BlockInfo blockInfo) {
        return String.format("%s:%d.%d.%d", blockInfo.world, blockInfo.x, blockInfo.y, blockInfo.z);
    }

    public boolean beginReload(final CommandSender sender, final boolean ignoreEmptyBlocks, final boolean saveNewDefaultConfigs) {

        setState(PluginState.Reloading);

        try {

            if (tickSchedule != null) {
                logger.logInfo("stopping tick scheduler...");
                tickSchedule.cancel();
            }


            if (syncSchedule != null) {
                logger.logInfo("stopping sync scheduler...");
                syncSchedule.cancel();
            }

            if (worldScanSchedule != null) {
                logger.logInfo("stopping world scan scheduler...");
                worldScanSchedule.cancel();
            }

            if (worldScannerEngine != null) {
                logger.logInfo("stopping world scanner engine...");
                worldScannerEngine.stop(WAIT_FOR_ENGINE_TO_STOP_TIMEOUT_MSECS);
            }

            if (databaseEngine != null) {
                logger.logInfo("stopping db engine...");
                databaseEngine.stop(WAIT_FOR_ENGINE_TO_STOP_TIMEOUT_MSECS);
            }

            final Plugin plugin = this;
            Bukkit.getScheduler().runTaskAsynchronously(
                    this,
                    new BukkitRunnable() {

                        @Override
                        public void run() {

                            final boolean wasReloadedWithSuccess = reload(sender, ignoreEmptyBlocks, saveNewDefaultConfigs);

                            new BukkitRunnable() {

                                @Override
                                public void run() {
                                    onEndReload(sender, wasReloadedWithSuccess);
                                }
                            }.runTaskAsynchronously(plugin);
                        }
                    }
            );


            return true;
        } catch (Throwable t) {
            logger.logSevere(t, sender);
            return false;
        }
    }

    private void onEndReload(final CommandSender sender, boolean wasReloadedWithSuccess) {
        sender.sendMessage("Was reloaded with success: " + wasReloadedWithSuccess);

        if (wasReloadedWithSuccess) {
            setState(PluginState.Running);
        } else {
            setState(PluginState.ReloadError);
        }
    }

    private boolean reload(CommandSender sender, boolean ignoreEmptyBlocks, boolean saveNewDefaultConfigs) {

        try {

            unknownBlocksFound = new ConcurrentLinkedQueue<BlockInfo>();
            unknownBlocksProcessingState.set(0);
            debugPlayers = new HashSet<String>();
            broadcastQueue = new ConcurrentLinkedQueue<String>();

            logger.logInfo("reloading settings...");
            if (!reloadSettings(saveNewDefaultConfigs)) {
                logger.logSevere("failed to reload settings. ");
                return false;
            }

            logger.logInfo("starting db engine...");

            databaseEngine = new DatabaseEngine(
                    this, settings.dbHost, settings.dbPort,
                    settings.dbCatalog, settings.dbUser, settings.dbPassword
            );
            databaseEngine.start();

            logger.logInfo("reloading data...");
            reloadData(ignoreEmptyBlocks);

            logger.logInfo("starting world scanner engine...");
            worldScannerState.set(0);

            final Map<String, BlockLimit> blockLimitsCopy = (Map<String, BlockLimit>) Utils.copyObject(blockLimits);
            final Set<Integer> blackListCopy = (Set<Integer>) Utils.copyObject(settings.blockIdScanBlackList);

            worldScannerEngine = new WorldScannerEngine(this, blockLimitsCopy, blackListCopy);
            worldScannerEngine.start();

            if (settings.scanChunksPeriodicallyEnabled) {
            /* queue all loaded chunks for scanning */
                worldScanSchedule = createWorldScannerSchedule();
            }

            if (settings.syncRemovedBlocksPeriodicallyEnabled) {
                syncSchedule = createSyncSchedule(10, settings.syncRemovedBlocksPeriodicallyIntervalSeconds);
            }

            tickSchedule = createTickSchedule();


            return true;

        } catch (Throwable t) {
            logger.logSevere(t, sender);
            return false;
        }
    }

    public void reloadData(boolean ignoreEmptyBlocks) throws Exception {

        blockLimits = databaseEngine.getBlockLimits();

        if (blockLimits == null || blockLimits.size() < 1) {
            throw new Exception("blockLimits are not allowed to be null or empty");
        }

        blockLimitGroups = databaseEngine.getBlockLimitGroups();

        if (blockLimitGroups == null || blockLimitGroups.size() < 1) {
            throw new Exception("blockLimitGroups are not allowed to be null or empty");
        }

        playerBlocks = databaseEngine.getPlayerBlocks(blockLimits, blockLimitGroups);

        if (playerBlocks == null || playerBlocks.size() < 1) {
            throw new Exception("playerBlocks are not allowed to be null or empty");
        }

        playerToBlockCoordsMap = new ConcurrentHashMap<String, BlockInfo>();

        Set<String> playerNames = playerBlocks.keySet();
        for (String playerName : playerNames) {
            Collection<ArrayList<BlockInfo>> blockTypes = playerBlocks.get(playerName).blockMap.values();
            for (ArrayList<BlockInfo> blockType : blockTypes) {
                for (BlockInfo blockInfo : blockType) {
                    playerToBlockCoordsMap.put(
                            getBlockKeyFromInfo(blockInfo),
                            blockInfo
                    );
                }
            }
        }

        blockStats = databaseEngine.getBlockStats();


        // if auto replace / removal is enabled
        if (!ignoreEmptyBlocks && blockLimits.size() > 0 && playerBlocks.size() < 1 && !settings.doAllowEmptyBlockInfo
                // makes no sense to warn about this, if auto removal is deactivated
                && (
                settings.autoRemoveUnknownBlocksEnabled
                        || settings.autoReplaceUnknownBlocksEnabled
                        || settings.autoReplaceUnknownBlocksWithSignEnabled
        )
                ) {
            setState(PluginState.SafetyModeEmptyBlockInfo);
            throw new Exception("No player blocks could be retrieved from the database. This could be a grave error or " +
                    "maybe it is the first time omniscient is run on the server but with limits already specified in the database.");
        }

    }

    public void doDisableWhiteList() {

        if (getServer().hasWhitelist()) {
            getServer().setWhitelist(false);
            getServer().broadcastMessage("Omniscient automatically disabled white listing.");
        }

        logger.logInfo("Omniscient automatically disabled white listing.");
    }

    @EventHandler
    public void onPlayerJoined(PlayerJoinEvent event) {
        if (this.getState() != PluginState.Running) {
            return;
        }
    }

    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent event) {

//        if (this.getState() != PluginState.Running) {
//            return;
//        }

        try {

            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }

            // HACK: ignore events for non-ops
            if (!event.getPlayer().isOp()) {
                return;
            }

            // TODO: include damage / sub value
            if (!settings.interactionToolEnabled ||

                    (event.getPlayer().getItemInHand().getTypeId() != settings.interactionToolItemId
                            && event.getPlayer().getItemInHand().getTypeId() != 290 // wooden hoe

                    )) {
                return;
            }

            final Block clickedBlock = event.getClickedBlock();

            if (clickedBlock == null) {
                return;
            }

            final String blockIdFromBlock = getBlockIdFromBlock(clickedBlock);


            BlockInfo blockInfo = new BlockInfo(0, blockIdFromBlock, clickedBlock.getWorld().getName(),
                    clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ(), null, null);

            String blockKey = getBlockKeyFromInfo(blockInfo);

            String owner = null;
            int current = -1;

             /* attempt to get information */
            if (playerToBlockCoordsMap.containsKey(blockKey)) {
                owner = playerToBlockCoordsMap.get(blockKey).placedBy;

                final Map<String, ArrayList<BlockInfo>> map = playerBlocks.get(owner).blockMap;

                if (map.containsKey(blockIdFromBlock)) {
                    final ArrayList<BlockInfo> blockInfos = map.get(blockIdFromBlock);
                    current = blockInfos.size();
                }
            }

            BlockLimit blockLimit = null;
            if (blockLimits.containsKey(blockIdFromBlock)) {
                blockLimit = blockLimits.get(blockIdFromBlock);
            }

            String message;

            if (blockLimit != null && blockLimit.limit > -1) {
                message = String.format("%s is owned by: %s. Limit: %s of %s",
                        blockLimit.blockDisplayName,
                        owner != null ? owner : "unknown",
                        current > -1 ? current : "unknown",
                        blockLimit.limit
                );
            } else {

                if (owner != null) {
                    message = String.format("The position is owned by: %s. (%s @ %s)",
                            owner != null ? owner : "unknown", blockInfo.blockId, blockKey
                    );
                } else {

                    // HACK: assign to server if wooden hoe tool
                    if (event.getPlayer().isOp() && event.getPlayer().getItemInHand().getTypeId() == 290) {
                        blockInfo.placedBy = "server";
                        blockInfo.placedWhen = new Date();
                        databaseEngine.setBlockInfo(blockInfo);
                        message = "assigned the block to server";
                    } else {
                        message = String.format("Mother earth owns this block (%s @ %s)", blockInfo.blockId, blockKey);
                    }
                }

            }

            event.getPlayer().sendMessage(message);

            event.setCancelled(true);

        } catch (Throwable t) {
            logger.logSevere(t);
        }

    }

    @EventHandler
    public void onPlayerInteractEntityEvent(PlayerInteractEntityEvent event) {
        if (this.getState() != PluginState.Running) {
            return;
        }
    }

    public String getBlockIdFromBlock(Block block) {
        return block.getType().getId() + ":" + (int) block.getData();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (this.getState() != PluginState.Running) {
            return;
        }

        if (!settings.scanChunksOnLoad || event.isNewChunk()) {
            return;
        }

        worldScannerEngine.queueChunkForScanning(event.getChunk(), false, false);
    }

    private void processLimitedBlockRemoval(Block block, String blockId, String worldName, Player player) {

        //logger.logInfo("processLimitedBlockRemoval entry");

        if (!settings.blockLimitsEnabled) {
            //logger.logInfo("processLimitedBlockRemoval exit 1");
            return;
        }

        //logger.logInfo("has metaData: " +  block.getState().getRawData() );

        // TODO: optimize
        String blockKey = getBlockKeyFromInfo(new BlockInfo(0, blockId, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), null, null));

        // do we have the players name whom placed the block?
        if (!playerToBlockCoordsMap.containsKey(blockKey)) {
            //logger.logInfo("processLimitedBlockRemoval exit 2");
            return;
        }

        final BlockInfo originallyPlacedBlockInfo = playerToBlockCoordsMap.get(blockKey);

        if (originallyPlacedBlockInfo == null) {
            logger.logWarn(String.format("originallyPlacedBlockInfo did not exist. blockKey: %s", blockKey));
            return;
        }

        BlockGroupAndInfos groupAndInfos = playerBlocks.get(originallyPlacedBlockInfo.placedBy);
        Map<String, GroupCount> groupMap = groupAndInfos.groupMap;
        Map<String, ArrayList<BlockInfo>> map = groupAndInfos.blockMap;

        if (!map.containsKey(originallyPlacedBlockInfo.blockId)) {
            // logger.logInfo("processLimitedBlockRemoval exit 3");
            return;
        }

        ArrayList<BlockInfo> blockList = map.get(originallyPlacedBlockInfo.blockId);

        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        BlockInfo blockInfoRemoved = null;
        int groupCountValue = -1;
        int groupCountLimit = -1;

        for (Iterator<?> it = blockList.iterator(); it.hasNext(); ) {
            BlockInfo blockInfoFromList = (BlockInfo) it.next();
            if (worldName.equals(blockInfoFromList.world)
                    && x == blockInfoFromList.x
                    && y == blockInfoFromList.y
                    && z == blockInfoFromList.z) {
                // remove from list
                blockInfoRemoved = blockInfoFromList;
                it.remove();

                BlockLimit blockLimitFromSpecificOrRange;

                String blockIdRange = block.getTypeId() + ":-1";

                if (blockLimits.containsKey(blockIdRange)) {
                    blockLimitFromSpecificOrRange = blockLimits.get(blockIdRange);
                } else {
                    blockLimitFromSpecificOrRange = blockLimits.get(blockInfoRemoved.blockId);
                }

                if (blockLimitFromSpecificOrRange != null && blockLimitFromSpecificOrRange.limitGroup != null) {
                    GroupCount groupCount = groupMap.get(blockLimitFromSpecificOrRange.limitGroup);
                    if (groupCount != null) {
                        groupCount.value--;
                        //player.sendMessage(String.format("Decreased groupCount. Now %d of %d", groupCount.value, groupCount.limit));
                        groupCountValue = groupCount.value;
                        groupCountLimit = groupCount.limit;
                    } else {
                        //player.sendMessage("groupCount null. blockLimit.limitGroup: " + blockLimitFromSpecificOrRange.limitGroup + ", groupMap.size(): " + groupMap.size());
                    }
                } else {
                    //player.sendMessage("blockLimit null");
                }


                databaseEngine.deleteBlockInfo(blockInfoFromList);
            }
        }

        // remove from coordinate to player map as well
        playerToBlockCoordsMap.remove(blockKey);

        //player.sendMessage(String.format("block removed. groupCountValue: %d,  groupCountLimit: %d", groupCountValue, groupCountLimit));

        if (settings.enablePlayerInfoOnBlockEvents) {

            BlockLimit blockLimitFromSpecificOrRange = null;
            int limit = -1;

            if (blockInfoRemoved != null && blockLimits.containsKey(blockInfoRemoved.blockId)) {

                String blockIdRange = blockInfoRemoved.id + ":-1";

                if (blockLimits.containsKey(blockIdRange)) {
                    blockLimitFromSpecificOrRange = blockLimits.get(blockIdRange);
                } else {
                    blockLimitFromSpecificOrRange = blockLimits.get(blockInfoRemoved.blockId);
                }

                limit = blockLimitFromSpecificOrRange.limit;
            }

            int remaining = Math.max(
                    limit == -1 ? -1 : (limit - blockList.size()),
                    groupCountLimit == -1 ? -1 : groupCountLimit - groupCountValue
            );

            if (remaining > -1 && remaining < 3) {

                if (originallyPlacedBlockInfo.placedBy.equals(player.getName())) {
                    // players own block

                    String remainingStr = (blockLimitFromSpecificOrRange != null ? (limit - blockList.size()) + "" : "unknown");

                    player.sendMessage(
                            "Removed " + (blockLimitFromSpecificOrRange != null ? blockLimitFromSpecificOrRange.blockDisplayName : "unknown")
                                    + ". You now have " + remainingStr + " remaining."
                    );

                } else {


                    // another players block
                    String toPlayer = String.format("Removed %s. %s now has %d remaining.",
                            blockLimitFromSpecificOrRange != null ? blockLimitFromSpecificOrRange.blockDisplayName : "unknown",
                            originallyPlacedBlockInfo != null ? originallyPlacedBlockInfo.placedBy : "unknown",
                            remaining);

                    player.sendMessage(toPlayer);


                    // is playerNameWhomPlacedTheBlock online - if so, tell the player?
                    final Player player2 = getServer().getPlayer(originallyPlacedBlockInfo.placedBy);
                    if (player2 != null) {
                        player2.sendMessage(
                                String.format(
                                        "%s removed your %s. You now have %d remaining.",
                                        player.getName(),
                                        blockLimitFromSpecificOrRange != null ? blockLimitFromSpecificOrRange.blockDisplayName : "unknown",
                                        remaining
                                )
                        );
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) // should force this to be invoked after protection plugins etc.
    public void onBlockPlace(BlockPlaceEvent event) {

        if (event.isCancelled()) {
            return;
        }

        if (this.getState() != PluginState.Running) {

            event.getPlayer().sendMessage("Omniscient is not processing events. " +
                            "It may be reloading or in an erroneous state. Contact server admin if this continues for more than a minute."
            );

            event.setCancelled(true);

            return;
        }

        try {

            messageIfDebugSender(event.getPlayer(), "placed a block: " + getBlockIdFromBlock(event.getBlock())
                    + ", with item in hand: " + event.getItemInHand().getTypeId() + ":" + event.getItemInHand().getData().getData());

            Block block = event.getBlock();
            //String blockId = getBlockIdFromBlock(block);

            // HACK: support gregtech blocks
            int blockId;
            int blockSubValue;
            int typeId = event.getBlock().getType().getId();
            //if (typeId != 2271 && typeId != 2444 && typeId != 2446 && typeId != 2447) {

            blockId = block.getType().getId();
            blockSubValue = (int) block.getData();
//            } else {
//                blockId = event.getItemInHand().getTypeId();
//                blockSubValue = (int) event.getItemInHand().getData().getData();
//            }
            String blockIdAndSubValue = blockId + ":" + blockSubValue;
            String blockIdRange = blockId + ":-1";


            if (event.getItemInHand().getType().getId() == 10259) { // builders wand

                Block blockAgainst = event.getBlockAgainst();
                String blockIdAgainst = getBlockIdFromBlock(blockAgainst);

                if (blockLimits.containsKey(blockIdAgainst)) {

                    event.getPlayer().sendMessage("Your holding: "
                                    + event.getPlayer().getItemInHand().getType().getId()
                                    + ":" + event.getItemInHand().getType().name()
                                    + "_" + blockIdAgainst
                    );

                    event.setCancelled(true);
                    return;
                }
            }


            // skip if block is not limited
            if (!blockLimits.containsKey(blockIdAndSubValue) && !blockLimits.containsKey(blockIdRange)) {

                /* hotfix: if placed block is not limited - how about the one in hand?
                * this supports gregtech, mekanism and likely other mods which also
                * changes the sub value during placement.
                * */

                int blockIdInHand = event.getItemInHand().getTypeId();
                int blockSubValueInHand = (int) event.getItemInHand().getData().getData();
                String blockIdAndSubValueInHand = blockIdInHand + ":" + blockSubValueInHand;

                if (blockLimits.containsKey(blockIdAndSubValueInHand)) {
                    blockId = blockIdInHand;
                    blockSubValue = blockSubValueInHand;
                    blockIdAndSubValue = blockIdAndSubValueInHand;

                } else {

                    // do not track vanilla / mined blocks
                    if (!settings.blockStatsEnabled || blockId < 256) {
                        return;
                    }

                    // add to block stats
                    if (blockStats.containsKey(blockIdAndSubValue)) {
                        BlockStat blockStat = blockStats.get(blockIdAndSubValue);
                        blockStat.current++;
                        blockStat.placed++;
                        databaseEngine.setBlockStat(blockStat);

                    } else {
                        final BlockStat blockStat = new BlockStat(-1, 1, 1, 0, blockId, blockSubValue, false);
                        blockStats.put(blockIdAndSubValue, blockStat);
                        databaseEngine.setBlockStat(blockStat);
                    }

                    return;
                }
            }

            if (!settings.blockLimitsEnabled) {
                return;
            }

            BlockLimit blockLimitFromSpecificOrRange;

            if (blockLimits.containsKey(blockIdRange)) {
                blockLimitFromSpecificOrRange = blockLimits.get(blockIdRange);
                messageIfDebugSender(event.getPlayer(), "limit is from range");
            } else {
                blockLimitFromSpecificOrRange = blockLimits.get(blockIdAndSubValue);
            }

            if (!blockLimitFromSpecificOrRange.isLimitedInWorld(block.getWorld().getName())) {
                return;
            }

            String playerName = event.getPlayer().getName();

            // make sure there are maps for the player
            if (!playerBlocks.containsKey(playerName)) {

                BlockGroupAndInfos blockGroupAndInfos = new BlockGroupAndInfos();
                blockGroupAndInfos.groupMap = new HashMap<String, GroupCount>();
                blockGroupAndInfos.blockMap = new HashMap<String, ArrayList<BlockInfo>>();

                playerBlocks.put(playerName, blockGroupAndInfos);
            }

            BlockGroupAndInfos groupAndInfos = playerBlocks.get(playerName);


            int groupCountValue = -1;
            int groupCountLimit = -1;

            Map<String, GroupCount> groupMap = groupAndInfos.groupMap;

            GroupCount groupCount = null;

            /* are we placing a block of this type for the first time, if so, the group map have likely not been initialized */
            if (blockLimitFromSpecificOrRange.limitGroup != null && !groupMap.containsKey(blockLimitFromSpecificOrRange.limitGroup)) {
                final BlockLimitGroup limitGroup = blockLimitGroups.get(blockLimitFromSpecificOrRange.limitGroup);

                if (limitGroup != null) {
                    groupCount = new GroupCount();
                    groupCount.limit = limitGroup.limit;
                    groupCount.value = 0;
                    groupMap.put(blockLimitFromSpecificOrRange.limitGroup, groupCount);
                }
            }

            if (blockLimitFromSpecificOrRange.limitGroup != null && groupMap.containsKey(blockLimitFromSpecificOrRange.limitGroup)) {
                groupCount = groupMap.get(blockLimitFromSpecificOrRange.limitGroup);

                if (groupCount != null) {
                    groupCountValue = groupCount.value;
                    groupCountLimit = groupCount.limit;
                }
            }

            Map<String, ArrayList<BlockInfo>> map = groupAndInfos.blockMap;


            // make sure there is a list available for the type of block
            if (!map.containsKey(blockIdAndSubValue)) {
                ArrayList<BlockInfo> list = new ArrayList<BlockInfo>();
                map.put(blockIdAndSubValue, list);
            }

            ArrayList<BlockInfo> blockList = map.get(blockIdAndSubValue);

            int remaining = 0;

            if (groupCount != null) {

                if (groupCountLimit < 0) {
                    remaining = -1;
                } else {
                    remaining = Math.min(
                            blockLimitFromSpecificOrRange.limit == -1 ? Integer.MAX_VALUE : blockLimitFromSpecificOrRange.limit - blockList.size(),
                            groupCountLimit - groupCountValue
                    );
                }

                // event.getPlayer().sendMessage("set with group");
            } else {
                remaining = blockLimitFromSpecificOrRange.limit == -1 ? -1 : (blockLimitFromSpecificOrRange.limit - blockList.size());
            }

//            event.getPlayer().sendMessage(
//                    "remaining: " + remaining +
//                            ", limit: " + blockLimitFromSpecificOrRange.limit +
//                            ", groupCountLimit: " + groupCountLimit
//            );


            if (
                    remaining != -1 && remaining < 1 // unlimited ?

//                            && (
//                            (blockList.size() + 1) > remaining // exceed block limit ?
//                                    || groupCountValue + 1 > remaining // exceed group limit ?
//                    )
                            && !event.getPlayer().isOp() // op ignores limits
                    ) {

                event.setCancelled(true);

                if (settings.enablePlayerInfoOnBlockEvents) {
                    event.getPlayer().sendMessage("You cannot place more of that type of block. Based on limit id: " + blockLimitFromSpecificOrRange.id);
                }

                return;
            } else {
                BlockInfo blockInfo = new BlockInfo(0, blockIdAndSubValue, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), playerName, new Date());

                // add to list of blocks
                blockList.add(blockInfo);

                if (groupCount != null) {
                    groupCount.value++;
                    //event.getPlayer().sendMessage(String.format("Increased groupCount. Now %d of %d", groupCount.value, groupCount.limit));
                }

                // add to coordinate to player map
                playerToBlockCoordsMap.put(getBlockKeyFromInfo(blockInfo), blockInfo);

                // add to database
                databaseEngine.setBlockInfo(blockInfo);

                if (remaining > 0 && settings.enablePlayerInfoOnBlockEvents && (blockList.size() + 3) > remaining) { // HOTFIX: show limit only when 5 or less remaining
                    event.getPlayer().sendMessage("You can place an additional " + remaining + " of " + blockLimitFromSpecificOrRange.blockDisplayName + ".");
                }
            }

        } catch (Throwable t) {
            logger.logSevere(t, event.getPlayer());
        }
    }

    public boolean syncRemovedBlocks(CommandSender sender) {

        try {

            if (state != PluginState.Running) {
                return false;
            }

//            if (databaseEngine.hasUnsavedItems()) {
//                return false;
//            }


            final Date begin = new Date();

            state = PluginState.Synchronizing;
            int totalBlocksSynced = 0;

            Set<String> playerNames = playerBlocks.keySet();
            for (String playerName : playerNames) {

                final Map<String, ArrayList<BlockInfo>> map = playerBlocks.get(playerName).blockMap;
                final Map<String, GroupCount> groupMap = playerBlocks.get(playerName).groupMap;

                Collection<ArrayList<BlockInfo>> blockTypes = map.values();
                for (ArrayList<BlockInfo> blockType : blockTypes) {

                    for (Iterator<?> it = blockType.iterator(); it.hasNext(); ) {
                        BlockInfo blockInfoFromList = (BlockInfo) it.next();
                        String blockKey = getBlockKeyFromInfo(blockInfoFromList);

                        World world = getServer().getWorld(blockInfoFromList.world);

                        String blockIdInWorld = null;

                        if (world != null) {
                            Block blockInWorld = world.getBlockAt(blockInfoFromList.x, blockInfoFromList.y, blockInfoFromList.z);
                            if (blockInWorld != null && blockInWorld.getType() != Material.AIR) {
                                blockIdInWorld = getBlockIdFromBlock(blockInWorld);
                            }
                        }


                        if (blockIdInWorld == null/* || */) {
//                            logger.logInfo("Found missing block at "
//                                            + String.format("%s:%d.%d.%d", blockInfoFromList.world, blockInfoFromList.x, blockInfoFromList.y, blockInfoFromList.z)
//                            );

                            playerToBlockCoordsMap.remove(blockKey);
                            databaseEngine.deleteBlockInfo(blockInfoFromList);
                            it.remove();

                            if (groupMap != null) {

                                BlockLimit limit = blockLimits.get(blockInfoFromList.blockId);
                                if (limit != null) {
                                    final GroupCount groupCount = groupMap.get(limit.limitGroup);
                                    if (groupCount != null) {
                                        groupCount.value = Math.max(0, groupCount.value - 1);
                                    }
                                }
                            }

                        } else {
                            // it was a non-air block, but is it the same block type that was originally placed?
                            if (!blockInfoFromList.blockId.equals(blockIdInWorld)) {
                                // TODO: mark the block info as a changed block
                            }
                        }

                        totalBlocksSynced++;
                    }

                }
            }

            final Date end = new Date();
            logger.logInfo(String.format(
                    "Omniscient synchronized %d player blocks in a total of %d msec",
                    totalBlocksSynced,
                    (end.getTime() - begin.getTime())
            ));


            state = PluginState.Running;
            return true;

        } catch (Throwable t) {
            logger.logSevere(t);
            state = PluginState.Running;
            return false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {

        if (event.isCancelled()) {
            logger.logInfo("onBlockBreak cancelled");
            return;
        }

        //logger.logInfo("block break event: "+ event.getBlock().getTypeId());

        if (this.getState() != PluginState.Running) {

            event.getPlayer().sendMessage("Omniscient is not processing events. " +
                            "It may be reloading or in an erroneous state. Contact server admin if this continues for more than a minute."
            );

            event.setCancelled(true);

            return;
        }

        try {
            Block block = event.getBlock();
            String worldName = event.getBlock().getWorld().getName();
            String blockId = getBlockIdFromBlock(block);

            // do not track vanilla / mined blocks
            if (settings.blockStatsEnabled) {
                // add to block stats
                if (blockStats.containsKey(blockId)) {
                    BlockStat blockStat = blockStats.get(blockId);
                    blockStat.current--;
                    blockStat.breaked++;
                    databaseEngine.setBlockStat(blockStat);
                } else {
                    final BlockStat blockStat = new BlockStat(-1, -1, 0, 1, block.getType().getId(), (int) block.getData(), false);
                    blockStats.put(blockId, blockStat);
                    databaseEngine.setBlockStat(blockStat);
                }
            }

            processLimitedBlockRemoval(block, blockId, worldName, event.getPlayer());

        } catch (Throwable t) {
            logger.logSevere(t, event.getPlayer());
        }


    }

    public void messageIfDebugSender(CommandSender sender, String message) {
        if (debugPlayers.contains(sender.getName())) {
            sender.sendMessage(message);
        }
    }

    private boolean reloadSettings(boolean saveNewDefaultConfigs) {

        try {

            /* load values from config */
            settings = new Settings(this);
            settings.load(saveNewDefaultConfigs);

            logger.logInfo("reloadSettings done");

            return true;

        } catch (Throwable t) {
            logger.logSevere(t);
            return false;
        }
    }

    public boolean setDebugPlayer(Player player, boolean doEnable) {

        // HACK: many hacks...
//        getServer().broadcastMessage("UBs: " + unknownBlocksFound.size());
//
//        final org.bukkit.plugin.Plugin safeEdit = getServer().getPluginManager().getPlugin("SafeEdit");
//        if (safeEdit != null) {
//            getServer().broadcastMessage("found SafeEdit shit plugin...");
//            getServer().getPluginManager().disablePlugin(safeEdit);
//        } else {
//            getServer().broadcastMessage("could not find SafeEdit shit plugin...");
//        }
//
        final String playerName = player.getName();
//
//
//        int currentCnt = 0;
//        int maxCnt = 100;
//
//
//        final Object[] blockInfoObjs = unknownBlocksFound.toArray();
//
//        for (Object blockInfoObj : blockInfoObjs) {
//
//            BlockInfo blockInfo = (BlockInfo) blockInfoObj;
//
//            player.sendMessage(String.format("%s: %s", blockInfo.blockId, getBlockKeyFromInfo(blockInfo)));
//
//            if (++currentCnt > maxCnt) {
//
//                player.sendMessage(String.format("Reached max count of %d", currentCnt));
//                return true;
//            }
//        }
//
//        player.sendMessage(String.format("Found a total of %d blocks", currentCnt));


        if (doEnable && !debugPlayers.contains(playerName)) {
            debugPlayers.add(playerName);
            return true;
        }

        if (!doEnable && debugPlayers.contains(playerName)) {
            debugPlayers.remove(playerName);
            return true;
        }

        return false;
    }

    public boolean setUnknowBlocksToBeProcessed(ArrayList<BlockInfo> unknownBlocksFound) {

        if (unknownBlocksFound.size() > settings.maximumUnknownBlocksToProcessBeforeSafetySwitch) {

            setState(PluginState.SafetyModeTooManyUnknownBlocksFound);

            if (settings.dumpUnknownBlockInfoToDiskOnSafetySwitch) {
                final String dumpLogFilename = String.format("%d.unknownBlocksDump.txt", new Date().getTime());
                final String dumpLogFilePath = getDataFolder().toString() + File.separator + dumpLogFilename;


                try {
                    PrintWriter out = new PrintWriter(dumpLogFilePath);

                    for (BlockInfo blockInfo : unknownBlocksFound) {

                        String message = blockInfo.blockId + ":" + getBlockKeyFromInfo(blockInfo);


                        out.println(message);
                    }
                    out.flush();
                    out.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }


            }

            return false;
        }

        this.unknownBlocksFound.addAll(unknownBlocksFound);

        return true;
    }

    public PluginState getState() {
        return state;
    }

    public void setState(PluginState state) {
        logger.logInfo(String.format("Plugin state transition %s to %s", this.state, state));

        if (state == PluginState.Running) {
            getServer().broadcastMessage(ChatColor.GREEN + "Omniscient is now running.");
        }

        this.state = state;
    }

    public boolean hasUnknownBlocksQueued() {
        return unknownBlocksFound.size() > 0;
    }
}

