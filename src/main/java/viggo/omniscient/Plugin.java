package viggo.omniscient;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class Plugin extends JavaPlugin implements Listener {

    private static int TICKS_PER_SECOND = 20;
    public Logger logger = null;
    public Settings settings;
    public DatabaseEngine databaseEngine;
    public WorldScannerEngine worldScannerEngine;
    public AtomicInteger unknownBlocksProcessingState = new AtomicInteger();
    public AtomicInteger worldScannerState = new AtomicInteger();
    ConcurrentHashMap<String, String> playerToBlockCoordsMap; // map from coords to player name
    Map<String, Map<String, ArrayList<BlockInfo>>> playerBlocks;
    Map<String, BlockLimit> blockLimits;
    Map<String, BlockStat> blockStats;
    private ConcurrentLinkedQueue<BlockInfo> unknownBlocksFound;
    private BukkitTask worldScanSchedule;
    private BukkitTask masterSchedule;
    private Set<String> debugPlayers;

    public void onDisable() {
        logger.logInfo("Disable invoked. Stopping engines.");

        if (databaseEngine != null) {
            try {
                databaseEngine.stop();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (worldScannerEngine != null) {
            try {
                worldScannerEngine.stop();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        logger.logInfo("Disabled");
    }

    public void onEnable() {

        logger = new Logger(this, getLogger());

        // make sure datafolder is present
        getDataFolder().mkdirs();

        // try load
        if (!reload(null)) {
            logger.logSevere("Failed to load. Please fix the problem (likely shown above).");
        }

        // register commands
        getCommand("omni").setExecutor(new viggo.omniscient.CommandExecutor(this, getLogger()));

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);


        if (settings.autoWhiteListOffEnabled) {
            DelayedWhiteListEnabler delayedWhiteListEnabler = new DelayedWhiteListEnabler(this);
            BukkitTask task = Bukkit.getScheduler().runTaskLater(this, delayedWhiteListEnabler, 20 * settings.autoWhiteListOffDelaySeconds);
            getServer().broadcastMessage("Omniscient will automatically disable white listing in %d seconds.");
        }


        logger.logInfo("Enabled: " + getDescription().getVersion());
    }

    public boolean processUnknownBlocks() {

        // CAS to prevent reentry / buildup by commands and/or schedule
        if (!unknownBlocksProcessingState.compareAndSet(0, 1)) {
            logger.logWarn("no re-entry allowed in processUnknownBlocks");
            return false;
        }

        try {

            if (unknownBlocksFound != null && unknownBlocksFound.size() > 0) {

                if (!settings.autoRemoveUnknownBlocksEnabled && !settings.autoReplaceUnknownBlocksEnabled && !settings.autoReplaceUnknownBlocksWithSignEnabled) {
                    unknownBlocksFound.clear();
                    return true;
                }


                if (settings.autoRemoveUnknownBlocksEnabled) {

                    while (unknownBlocksFound.size() > 0) {
                        final BlockInfo blockInfo = unknownBlocksFound.poll();

                        logger.logWarn(String.format("Removing block of type: %s @ %s:%d.%d.%d",
                                        blockInfo.blockId, blockInfo.world, blockInfo.x, blockInfo.y, blockInfo.z)
                        );

                        getServer().getWorld(blockInfo.world).getBlockAt(blockInfo.x, blockInfo.y, blockInfo.z).setType(Material.AIR);
                    }

                    return true;

                }

                if (settings.autoReplaceUnknownBlocksEnabled) {

                    while (unknownBlocksFound.size() > 0) {
                        final BlockInfo blockInfo = unknownBlocksFound.poll();

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

                    return true;
                }

                if (settings.autoReplaceUnknownBlocksWithSignEnabled) {
                    while (unknownBlocksFound.size() > 0) {
                        final BlockInfo blockInfo = unknownBlocksFound.poll();

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

    private BukkitTask createSchedule() {
        return new BukkitRunnable() {

            @Override
            public void run() {
                try {

                    // only enqueue chunks if not already busy
                    if (!worldScannerState.compareAndSet(0, 1)) {
                        return;
                    }

                    final Date begin = new Date();

                    final List<World> worldList = getServer().getWorlds();

                    for (World world : worldList) {
                        final Chunk[] loadedChunks = world.getLoadedChunks();
                        for (Chunk loadedChunk : loadedChunks) {
                            worldScannerEngine.queueChunkForScanning(loadedChunk, true, false);
                            //totalChunks++;
                        }
                    }

                } catch (Throwable t) {
                    logger.logSevere(t);
                }
            }

        }.runTaskTimer(this, TICKS_PER_SECOND * settings.scanChunksPeriodicallyDelaySeconds, TICKS_PER_SECOND * settings.scanChunksPeriodicallyIntervalSeconds);
    }

    private BukkitTask create5secSchedule() {
        return new BukkitRunnable() {

            @Override
            public void run() {
                try {

                    if (settings.autoSync) {
                        sync(null);
                    }

                    processUnknownBlocks();

                } catch (Throwable t) {
                    logger.logSevere(t);
                }
            }

        }.runTaskTimer(this, TICKS_PER_SECOND * 5, TICKS_PER_SECOND * 5);
    }

    public String getBlockKeyFromInfo(BlockInfo blockInfo) {
        return String.format("%s:%d.%d.%d", blockInfo.world, blockInfo.x, blockInfo.y, blockInfo.z);
    }

    public boolean reload(CommandSender sender) {
        try {

            if (masterSchedule != null) {
                logger.logInfo("stopping 5 sec scheduler...");
                masterSchedule.cancel();
            }

            if (worldScanSchedule != null) {
                logger.logInfo("stopping world scan scheduler...");
                worldScanSchedule.cancel();
            }

            if (worldScannerEngine != null) {
                logger.logInfo("stopping world scanner engine...");
                worldScannerEngine.stop();
            }

            if (databaseEngine != null) {
                logger.logInfo("stopping db engine...");
                databaseEngine.stop();
            }

            unknownBlocksFound = new ConcurrentLinkedQueue<BlockInfo>();
            debugPlayers = new HashSet<String>();

            logger.logInfo("reloading settings...");
            reloadSettings();

            logger.logInfo("starting db engine...");

            databaseEngine = new DatabaseEngine(
                    this, settings.dbHost, settings.dbPort,
                    settings.dbCatalog, settings.dbUser, settings.dbPassword
            );
            databaseEngine.start();

            logger.logInfo("reloading data...");
            reloadData();

            logger.logInfo("starting world scanner engine...");
            worldScannerState.set(0);
            worldScannerEngine = new WorldScannerEngine(this, blockLimits);
            worldScannerEngine.start();

            if (settings.scanChunksPeriodicallyEnabled) {
            /* queue all loaded chunks for scanning */
                worldScanSchedule = createSchedule();
            }

            masterSchedule = create5secSchedule();

            return true;
        } catch (Throwable t) {
            logger.logSevere(t, sender);
            return false;
        }
    }

    public void reloadData() {
        playerBlocks = databaseEngine.getPlayerBlocks();
        playerToBlockCoordsMap = new ConcurrentHashMap<String, String>();

        Set<String> playerNames = playerBlocks.keySet();
        for (String playerName : playerNames) {
            Collection<ArrayList<BlockInfo>> blockTypes = playerBlocks.get(playerName).values();
            for (ArrayList<BlockInfo> blockType : blockTypes) {
                for (BlockInfo blockInfo : blockType) {
                    playerToBlockCoordsMap.put(
                            getBlockKeyFromInfo(blockInfo),
                            playerName
                    );
                }
            }
        }

        blockLimits = databaseEngine.getBlockLimits();

        blockStats = databaseEngine.getBlockStats();
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

    }

    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent event) {

        try {
            if (event.getPlayer().getItemInHand().getTypeId() != 288) {
                return;
            }

            final Block clickedBlock = event.getClickedBlock();

            if (clickedBlock == null) {
                return;
            }

            final String blockIdFromBlock = getBlockIdFromBlock(clickedBlock);


            if (!blockLimits.containsKey(blockIdFromBlock)) {
                return;
            }

            BlockLimit blockLimit = blockLimits.get(blockIdFromBlock);

            BlockInfo blockInfo = new BlockInfo(0, blockIdFromBlock, clickedBlock.getWorld().getName(),
                    clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ(), null, null);

            String blockKey = getBlockKeyFromInfo(blockInfo);

            String owner = null;
            int current = -1;

             /* attempt to get information */
            if (playerToBlockCoordsMap.containsKey(blockKey)) {
                owner = playerToBlockCoordsMap.get(blockKey);

                final Map<String, ArrayList<BlockInfo>> map = playerBlocks.get(owner);

                if (map.containsKey(blockIdFromBlock)) {
                    final ArrayList<BlockInfo> blockInfos = map.get(blockIdFromBlock);
                    current = blockInfos.size();
                }
            }

            event.getPlayer().sendMessage(
                    String.format("%s is owned by: %s. Limit: %s of %d",
                            blockLimit.blockDisplayName,
                            owner != null ? owner : "unknown",
                            current > -1 ? current : "unknown",
                            blockLimit.limit
                    )
            );

            event.setCancelled(true);

        } catch (Throwable t) {
            logger.logSevere(t);
        }

    }

    @EventHandler
    public void onPlayerInteractEntityEvent(PlayerInteractEntityEvent event) {

    }

    public String getBlockIdFromBlock(Block block) {
        return block.getType().getId() + ":" + (int) block.getData();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!settings.scanChunksOnLoad || event.isNewChunk()) {
            return;
        }

        worldScannerEngine.queueChunkForScanning(event.getChunk(), false, false);
    }

    private void processLimitedBlockRemoval(Block block, String blockId, String worldName, Player player) {

        if (!settings.blockLimitsEnabled) {
            return;
        }

        final BlockLimit blockLimit = blockLimits.get(blockId);
        final int limit = blockLimit.limit;

        // TODO: optimize
        BlockInfo blockInfo = new BlockInfo(0, blockId, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), null, null);
        String blockKey = getBlockKeyFromInfo(blockInfo);

        // do we have the players name whom placed the block?
        if (!playerToBlockCoordsMap.containsKey(blockKey)) {
            return;
        }

        String playerNameWhomPlacedTheBlock = playerToBlockCoordsMap.get(blockKey);

        Map<String, ArrayList<BlockInfo>> map = playerBlocks.get(playerNameWhomPlacedTheBlock);

        if (!map.containsKey(blockId)) {
            return;
        }

        ArrayList<BlockInfo> blockList = map.get(blockId);


        // TODO: seems rather redundant to check again, we know that a player has that block at given position...
        // but we need to find it in the list ofc.

        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        for (Iterator<?> it = blockList.iterator(); it.hasNext(); ) {
            BlockInfo blockInfoFromList = (BlockInfo) it.next();
            if (worldName.equals(blockInfoFromList.world)
                    && x == blockInfoFromList.x
                    && y == blockInfoFromList.y
                    && z == blockInfoFromList.z) {
                // remove from list
                it.remove();

                databaseEngine.deleteBlockInfo(blockInfoFromList);
            }
        }

        // remove from coordinate to player map as well
        playerToBlockCoordsMap.remove(playerNameWhomPlacedTheBlock);

        if (settings.enablePlayerInfoOnBlockEvents) {

            if (playerNameWhomPlacedTheBlock.equals(player.getName())) {
                // players own block
                player.sendMessage("Removed " + blockLimit.blockDisplayName + ". You now have " + (limit - blockList.size()) + " remaining.");
            } else {


                // another players block
                String toPlayer = String.format("Removed %s. %s now has %d remaining.", blockLimit.blockDisplayName, playerNameWhomPlacedTheBlock, (limit - blockList.size()));
                player.sendMessage(toPlayer);

                // is playerNameWhomPlacedTheBlock online - if so, tell the player?
                final Player player2 = getServer().getPlayer(playerNameWhomPlacedTheBlock);
                if (player2 != null) {
                    player2.sendMessage(
                            String.format(
                                    "%s removed your %s. You now have %d remaining.",
                                    player.getName(),
                                    blockLimit.blockDisplayName,
                                    limit - blockList.size()
                            )
                    );
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {

        if (event.isCancelled()) {
            return;
        }

        try {

            messageIfDebugSender(event.getPlayer(), "placed a block: " + getBlockIdFromBlock(event.getBlock()));

            Block block = event.getBlock();
            String blockId = getBlockIdFromBlock(block);

            if (event.getItemInHand().getType().getId() == 10259) { // builders wand

                Block blockAgainst = event.getBlockAgainst();
                String blockIdAgainst = getBlockIdFromBlock(blockAgainst);

                event.getPlayer().sendMessage("Your holding: "
                                + event.getPlayer().getItemInHand().getType().getId()
                                + ":" + event.getItemInHand().getType().name()
                                + "_" + blockIdAgainst
                );


                if (blockLimits.containsKey(blockIdAgainst)) {
                    event.setCancelled(true);
                    return;
                }
            }


            // skip if block is not limited
            if (!blockLimits.containsKey(blockId)) {

                // do not track vanilla / mined blocks
                if (!settings.blockStatsEnabled || block.getType().getId() < 256) {
                    return;
                }

                // add to block stats
                if (blockStats.containsKey(blockId)) {
                    BlockStat blockStat = blockStats.get(blockId);
                    blockStat.current++;
                    blockStat.placed++;
                    databaseEngine.setBlockStat(blockStat);

                } else {
                    final BlockStat blockStat = new BlockStat(-1, 1, 1, 0, block.getType().getId(), (int) block.getData(), false);
                    blockStats.put(blockId, blockStat);
                    databaseEngine.setBlockStat(blockStat);
                }

                return;
            }

            if (!settings.blockLimitsEnabled) {
                return;
            }

            final BlockLimit blockLimit = blockLimits.get(blockId);

            int limit = blockLimit.limit;

            String playerName = event.getPlayer().getName();

            // make sure there is a map for the player
            if (!playerBlocks.containsKey(playerName)) {
                playerBlocks.put(playerName, new HashMap<String, ArrayList<BlockInfo>>());
            }

            Map<String, ArrayList<BlockInfo>> map = playerBlocks.get(playerName);

            // make sure there is a list available for the type of block
            if (!map.containsKey(blockId)) {
                ArrayList<BlockInfo> list = new ArrayList<BlockInfo>();
                map.put(blockId, list);
            }

            ArrayList<BlockInfo> blockList = map.get(blockId);

            if ((blockList.size() + 1) > limit) {
                event.setCancelled(true);

                if (settings.enablePlayerInfoOnBlockEvents) {
                    event.getPlayer().sendMessage("You cannot place more of that type of block. The maximum amount allowed is: " + limit);
                }

                return;
            } else {
                BlockInfo blockInfo = new BlockInfo(0, blockId, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), playerName, new Date());

                // add to list of blocks
                blockList.add(blockInfo);

                // add to coordinate to player map
                playerToBlockCoordsMap.put(getBlockKeyFromInfo(blockInfo), playerName);

                // add to database
                databaseEngine.setBlockInfo(blockInfo);

                if (settings.enablePlayerInfoOnBlockEvents) {
                    event.getPlayer().sendMessage("You can place an additional " + (limit - blockList.size()) + " of " + blockLimit.blockDisplayName + ".");
                }
            }

        } catch (Throwable t) {
            logger.logSevere(t, event.getPlayer());
        }
    }

    public boolean sync(CommandSender sender) {

        try {

            if (databaseEngine.hasUnsavedItems()) {
                return false;
            }

            // TODO: we should prevent further placement / breaking of blocks during sync etc.

            Set<String> playerNames = playerBlocks.keySet();
            for (String playerName : playerNames) {
                Collection<ArrayList<BlockInfo>> blockTypes = playerBlocks.get(playerName).values();
                for (ArrayList<BlockInfo> blockType : blockTypes) {

                    for (Iterator<?> it = blockType.iterator(); it.hasNext(); ) {
                        BlockInfo blockInfoFromList = (BlockInfo) it.next();
                        String blockKey = getBlockKeyFromInfo(blockInfoFromList);

                        World world = getServer().getWorld(blockInfoFromList.world);

                        String blockIdInWorld = null;

                        if (world != null) {
                            Block blockInWorld = world.getBlockAt(blockInfoFromList.x, blockInfoFromList.y, blockInfoFromList.z);
                            blockIdInWorld = getBlockIdFromBlock(blockInWorld);
                        }


                        if (blockIdInWorld == null || !blockInfoFromList.blockId.equals(blockIdInWorld)) {
                            logger.logInfo("Found mismatching block at "
                                            + String.format("%s:%d.%d.%d", blockInfoFromList.world, blockInfoFromList.x, blockInfoFromList.y, blockInfoFromList.z)
                            );

                            playerToBlockCoordsMap.remove(blockKey);
                            databaseEngine.deleteBlockInfo(blockInfoFromList);
                            it.remove();
                        }

                    }

                }
            }

            return true;

        } catch (Throwable t) {
            logger.logSevere(t);
            return false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        try {
            Block block = event.getBlock();
            String worldName = event.getBlock().getWorld().getName();
            String blockId = getBlockIdFromBlock(block);

            // skip if block is not limited
            if (!blockLimits.containsKey(blockId)) {

                // do not track vanilla / mined blocks
                if (!settings.blockStatsEnabled || block.getType().getId() < 256) {
                    return;
                }

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

                return;
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

    private void reloadSettings() {

        try {

            // make sure that config is up to date
            super.getConfig().options().copyDefaults(true);

            super.reloadConfig();

            super.saveConfig();

            /* load values from config */
            settings = Settings.load(this);

            logger.logInfo("reloadSettings done");

        } catch (Throwable t) {
            logger.logSevere(t);
        }
    }

    public boolean setDebugPlayer(Player player, boolean doEnable) {

        final String playerName = player.getName();

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

    public void setUnknowBlocksToBeProcessed(ArrayList<BlockInfo> unknownBlocksFound) {
        this.unknownBlocksFound.addAll(unknownBlocksFound);
    }
}

