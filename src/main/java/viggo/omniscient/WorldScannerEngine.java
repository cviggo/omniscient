package viggo.omniscient;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WorldScannerEngine implements Runnable {
    private final Plugin plugin;
    private final Map<String, BlockLimit> blockLimits;
    private final Set<Integer> blackListedBlocks;
    private final Object engineThreadLock = new Object();
    Thread engineThread;
    private ConcurrentLinkedQueue<ChunkScanTask> chunkScanTasks;
    private ConcurrentHashMap<Long, Date> previouslyScannedChunks;
    private boolean doExitEngine;

    WorldScannerEngine(Plugin plugin, Map<String, BlockLimit> blockLimits, Set<Integer> blackListedBlocks) {

        this.plugin = plugin;
        this.blockLimits = blockLimits;
        this.blackListedBlocks = blackListedBlocks;
        this.chunkScanTasks = new ConcurrentLinkedQueue<ChunkScanTask>();
        this.previouslyScannedChunks = new ConcurrentHashMap<Long, Date>();
    }

    public void queueChunkForScanning(Chunk chunk, boolean isScheduled, boolean forceEnqueue) {

        long id = (((long) chunk.getX()) << 32) | (chunk.getZ() & 0xffffffffL);

        if (!forceEnqueue) {

            if (previouslyScannedChunks.containsKey(id)) {
                int minChunkScanIntervalMilliseconds = plugin.settings.scanChunksMinimumIntervalForSameChunkSeconds * 1000;
                final long lastScanned = new Date().getTime() - previouslyScannedChunks.get(id).getTime();
                if (lastScanned < minChunkScanIntervalMilliseconds) {
                    //plugin.logger.logInfo(id + ": skipping chunk due to recently scanned: " + lastScanned + " msecs ago");
                    return;
                }
            }
        }

//        if (previouslyScannedChunks.containsKey(id)){
//            final long ago = new Date().getTime() - previouslyScannedChunks.get(id).getTime();
//            plugin.logger.logInfo(id + ": readding chunk after time: " + ago);
//        }

        previouslyScannedChunks.remove(id);

        previouslyScannedChunks.put(id, new Date());
        chunkScanTasks.add(new ChunkScanTask(chunk.getChunkSnapshot(true, false, false), isScheduled));
    }

    @Override
    public void run() {

        while (!doExitEngine) {


            try {

                // make sure to never feed more into the queue than server can synchronously process
                while (plugin.hasUnknownBlocksQueued()) {
                    Thread.sleep(1000);
                }

                final Date begin = new Date();

                long processedBlocks = 0;

                ArrayList<BlockInfo> unknownBlocksFound = unknownBlocksFound = new ArrayList<BlockInfo>();

                while (!doExitEngine && chunkScanTasks.size() > 0) {

                    final ChunkScanTask chunkScanTask = chunkScanTasks.poll();

                    final ChunkSnapshot chunkSnapshot = chunkScanTask.chunkSnapshot;

                    for (int x = 0; !doExitEngine && x < 16; x++) {
                        for (int z = 0; !doExitEngine && z < 16; z++) {

                            // would be nice if this worked... sadly some mods does not cause the height map to get updated...
                            // int startY = chunkSnapshot.getHighestBlockYAt(x, z);

                            int startY = 255;

                            for (int y = startY; !doExitEngine && y >= 0; y--) {

                                int blockX = chunkSnapshot.getX() * 16 + x;
                                int blockZ = chunkSnapshot.getZ() * 16 + z;
                                int blockY = y;

                                final int blockTypeId = chunkSnapshot.getBlockTypeId(x, y, z);

                                if (blackListedBlocks.contains(blockTypeId)) {
                                    continue;
                                }

                                String blockId = blockTypeId + ":" + chunkSnapshot.getBlockData(x, y, z);

                                if (blockLimits.containsKey(blockId)) {
//                                    plugin.logInfo(
//                                            String.format("Found a limited block (%s) at: %d, %d, %d.",
//                                                    blockId, blockX, blockY, blockZ
//                                            )
//                                    );

                                    final BlockInfo blockInfo = new BlockInfo(
                                            -1,
                                            blockId,
                                            chunkSnapshot.getWorldName(),
                                            blockX,
                                            blockY,
                                            blockZ,
                                            null,
                                            null
                                    );

                                    final String blockKeyFromInfo = plugin.getBlockKeyFromInfo(blockInfo);
                                    if (!plugin.playerToBlockCoordsMap.containsKey(blockKeyFromInfo)) {
                                        unknownBlocksFound.add(blockInfo);
                                    }
                                }
                            }

                            processedBlocks += startY;
                        }
                    }
                }

                if (processedBlocks > 0) {

                    final Date now = new Date();
                    final long elapsedMsecs = now.getTime() - begin.getTime();

                    plugin.logger.logInfo(String.format("Scanned %d blocks in %d msecs (%f blocks / sec). Found %d unknown blocks",

                            processedBlocks, elapsedMsecs, (double) processedBlocks / ((double) elapsedMsecs / 1000.0), unknownBlocksFound.size()));


                    final boolean unknownBlocksSubmissionSuccess = plugin.setUnknowBlocksToBeProcessed(unknownBlocksFound);

                    if (!unknownBlocksSubmissionSuccess) {
                        plugin.logger.logWarn("WorldScannerEngine is shutting down");
                        return; // die engine
                    }

                    // allow for more scheduled work to be queued
                    plugin.worldScannerState.set(0);
                }

                Thread.sleep(1000);
            } catch (Throwable t) {
                plugin.logger.logSevere(t);

                // clear on error
                chunkScanTasks.clear();

                // allow for more scheduled work to be queued
                plugin.worldScannerState.set(0);
            }
        }
    }

    public void stop(Integer waitForEngineToStopTimeoutMsecs) throws InterruptedException {

        synchronized (engineThreadLock) {
            doExitEngine = true;

            if (engineThread != null) {
                engineThread.join(waitForEngineToStopTimeoutMsecs);
                engineThread = null;
            }
        }
    }

    public void start() throws InterruptedException {

        synchronized (engineThreadLock) {
            if (engineThread == null) {
                engineThread = new Thread(this);
                engineThread.start();
            }
        }
    }

    class ChunkScanTask {
        ChunkSnapshot chunkSnapshot;
        boolean isScheduled;

        ChunkScanTask(ChunkSnapshot chunkSnapshot, boolean isScheduled) {
            this.chunkSnapshot = chunkSnapshot;
            this.isScheduled = isScheduled;
        }
    }
}
