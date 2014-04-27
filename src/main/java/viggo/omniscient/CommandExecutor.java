package viggo.omniscient;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Logger;

public class CommandExecutor implements org.bukkit.command.CommandExecutor {
    private final Plugin plugin;
    private final Logger logger;

    public CommandExecutor(Plugin plugin, Logger logger) {

        this.plugin = plugin;
        this.logger = logger;
    }

    static String join(Collection<?> s, String delimiter) {
        StringBuilder builder = new StringBuilder();
        Iterator<?> iter = s.iterator();
        while (iter.hasNext()) {
            builder.append(iter.next());
            if (!iter.hasNext()) {
                break;
            }
            builder.append(delimiter);
        }
        return builder.toString();
    }

    private boolean isCommand(String[] args, RemainingCommands remainingCommands, String... expectedCommands) {

        if (expectedCommands == null) {
            remainingCommands.remainingCommands.addAll(Arrays.asList(args));
            return true;
        }

        for (int i = 0; i < expectedCommands.length && i < args.length; i++) {
            String actual = args[i];
            String expected = expectedCommands[i];
            if (!actual.equals(expected)) {
                return false;
            }
        }

        for (int i = args.length - expectedCommands.length; i < args.length; i++) {
            remainingCommands.remainingCommands.add(args[i]);
        }

        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd == null) {
            return false;
        }


        try {

            RemainingCommands r = new RemainingCommands();

            String name = cmd.getName();
            if (!name.equals("omni")) {
                return false;
            }

            if (args == null || args.length < 1) {
                return false;
            }

            if (isCommand(args, r, "dev", "enable")) {
                plugin.setDebugPlayer(sender.getServer().getPlayer(sender.getName()), true);
                sender.sendMessage("dev enabled");
                return true;
            }

            if (isCommand(args, r, "dev", "disable")) {
                plugin.setDebugPlayer(sender.getServer().getPlayer(sender.getName()), false);
                sender.sendMessage("dev disabled");
                return true;
            }

//            if (isCommand(args, r, "blocks", "player", 3)) {
//                return blocksPlayer(sender, args);
//            }
//
//            if (isCommand(args, r, "blocks", "radius", 1)) {
//
//                //plugin.
//
//                return blocksRadius(sender, args);
//            }

            if (isCommand(args, r, "sync", "data")) {
                final boolean wasSynced = plugin.sync(sender);
                sender.sendMessage("Omniscient was synced: " + wasSynced);
                return true;
            }

            if (isCommand(args, r, "reload")) {
                boolean wasReloaded = plugin.reload(sender);
                sender.sendMessage(String.format("Reloaded with success: %s", wasReloaded));
                return true;
            }

            if (isCommand(args, r, "limit", "enable")) {
                plugin.settings.blockLimitsEnabled = true;
                sender.sendMessage("limits are now enabled");
                return true;
            }

            if (isCommand(args, r, "limit", "disable")) {
                plugin.settings.blockLimitsEnabled = false;
                sender.sendMessage("limits are now disabled");
                return true;
            }

            if (isCommand(args, r, "stats", "enable")) {
                plugin.settings.blockStatsEnabled = true;
                sender.sendMessage("stats are now enabled");
                return true;
            }

            if (isCommand(args, r, "stats", "disable")) {
                plugin.settings.blockStatsEnabled = false;
                sender.sendMessage("stats are now disabled");
                return true;
            }

            if (isCommand(args, r, "limit", "set", "hand")) {

                final ItemStack itemInHand = sender.getServer().getPlayer(sender.getName()).getItemInHand();

                if (itemInHand == null) {
                    sender.sendMessage("you have no item in your hand");
                    return true;
                }

                final int blockId = itemInHand.getTypeId();
                final int subValue = itemInHand.getData().getData();
                final String blockIdStr = blockId + ":" + subValue;

                final int limit = r.getInt(0);
                final int limitGroup = r.getInt(1);
                final String blockDisplayName = r.getString(2);

                if (plugin.blockLimits.containsKey(blockIdStr)) {

                    final BlockLimit currentBlockLimit = plugin.blockLimits.remove(blockIdStr);
                    plugin.databaseEngine.deleteBlockLimit(currentBlockLimit);
                }


                final BlockLimit blockLimit = new BlockLimit(-1, limit, limitGroup, blockId, subValue, blockDisplayName, null, null);
                plugin.blockLimits.put(blockIdStr, blockLimit);
                plugin.databaseEngine.setBlockLimit(blockLimit);

                return true;
            }


            if (isCommand(args, r, "scan", "loaded")) {

                final List<World> worldList = plugin.getServer().getWorlds();

                int chunkCnt = 0;

                for (World world : worldList) {
                    final Chunk[] loadedChunks = world.getLoadedChunks();
                    for (Chunk loadedChunk : loadedChunks) {
                        plugin.worldScannerEngine.queueChunkForScanning(loadedChunk, false, true);
                        chunkCnt++;
                    }
                }

                sender.sendMessage(String.format("Enqueued %d chunks for scanning", chunkCnt));

                return true;
            }

            if (isCommand(args, r, "scan", "chunk")) {
                final Location location = plugin.getServer().getPlayerExact(sender.getName()).getLocation();
                plugin.worldScannerEngine.queueChunkForScanning(location.getChunk(), false, true);
                sender.sendMessage(String.format("Enqueued current chunk for scanning"));

                return true;
            }

            if (isCommand(args, r, "scan", "chunks")) {
                final Location location = plugin.getServer().getPlayerExact(sender.getName()).getLocation();
                final Chunk locationChunk = location.getChunk();
                int radius = Integer.parseInt(args[2]);

                radius = Math.min(10, radius);

                plugin.worldScannerEngine.queueChunkForScanning(locationChunk, false, true);
                int enqueuedChunks = 1;

                final World world = location.getWorld();

                for (int x = locationChunk.getX() - radius; x <= locationChunk.getX() + radius; x++) {
                    for (int z = locationChunk.getZ() - radius; z <= locationChunk.getZ() + radius; z++) {
                        final Chunk chunk = world.getChunkAt(x, z);
                        if (chunk.isLoaded()) {
                            plugin.worldScannerEngine.queueChunkForScanning(chunk, false, true);
                            enqueuedChunks++;
                        }
                    }
                }

                sender.sendMessage(String.format("Enqueued %d chunks for scanning", enqueuedChunks));
                return true;
            }

            // TODO: refactor - is currently mostly a copy of the above, but with chunk loading
            if (isCommand(args, r, "scan", "chunks")) {
                final Location location = plugin.getServer().getPlayerExact(sender.getName()).getLocation();
                final Chunk locationChunk = location.getChunk();
                int radius = r.getInt(0);

                final boolean forceChunkLoading = r.getBoolean(1);

                radius = Math.min(10, radius);

                int enqueuedChunks = 0;

                final World world = location.getWorld();

                for (int x = locationChunk.getX() - radius; x <= locationChunk.getX() + radius; x++) {
                    for (int z = locationChunk.getZ() - radius; z <= locationChunk.getZ() + radius; z++) {
                        final Chunk chunk = world.getChunkAt(x, z);
                        if (chunk.isLoaded()) {
                            plugin.worldScannerEngine.queueChunkForScanning(chunk, false, true);
                            enqueuedChunks++;
                        } else {
                            if (forceChunkLoading) {
                                chunk.load();

                                if (chunk.isLoaded()) {
                                    plugin.worldScannerEngine.queueChunkForScanning(chunk, false, true);
                                    enqueuedChunks++;
                                }
                            }
                        }
                    }
                }

                sender.sendMessage(String.format("Enqueued %d chunks for scanning", enqueuedChunks));
                return true;
            }

            if (isCommand(args, r, "scan", "enable")) {
                plugin.settings.scanChunksOnLoad = true;
                plugin.settings.scanChunksPeriodicallyEnabled = true;
                sender.sendMessage("scan is now enabled (on chunk load and periodically)");
                return true;
            }

            if (isCommand(args, r, "scan", "disable")) {
                plugin.settings.scanChunksOnLoad = false;
                plugin.settings.scanChunksPeriodicallyEnabled = false;
                sender.sendMessage("scan is now disabled (on chunk load and periodically)");
                return true;
            }

            if (isCommand(args, r, "state")) {
                sender.sendMessage(
                        String.format("Last processed items %d secs ago.",
                                (new Date().getTime() - plugin.databaseEngine._lastRun.getTime()) / 1000
                        )
                );

                return true;
            }

            if (isCommand(args, r, "listWorlds")) {
                final List<World> worlds = plugin.getServer().getWorlds();

                final List<String> worldNames = new ArrayList<String>();

                for (World world : worlds) {
                    worldNames.add(world.getName());
                }

                final String worldsString = join(worldNames, ", ").toLowerCase();

                sender.sendMessage(worldsString);

                return true;
            }

            if (isCommand(args, r, "chunk", "resend")) {
                final Location playerLocation = plugin.getServer().getPlayer(sender.getName()).getLocation();
                playerLocation.getWorld().refreshChunk(playerLocation.getChunk().getX(), playerLocation.getChunk().getZ());
                return true;
            }

            if (isCommand(args, r, "blocks", "list")) {
                for (String item : r.remainingCommands) {
                    plugin.logger.logInfo(item);
                }

                final int blockId = r.getInt(0);
                final int subValue = r.getInt(1);
                final String blockIdStr = blockId + ":" + subValue;

                final Map<String, ArrayList<BlockInfo>> map = plugin.playerBlocks.get(sender.getName());

                if (map == null || !map.containsKey(blockIdStr)) {
                    sender.sendMessage("No blocks of that type for given player.");
                    return true;
                }

                final ArrayList<BlockInfo> blockInfos = map.get(blockIdStr);

                if (blockInfos.size() > 0) {
                    int cnt = 0;
                    for (BlockInfo blockInfo : blockInfos) {
                        sender.sendMessage(String.format("%s: %d.%d.%d", blockInfo.world, blockInfo.x, blockInfo.y, blockInfo.z));

                        if (++cnt > 20) {
                            sender.sendMessage("Reached maximum amount of blocks to list.");
                            break;
                        }
                    }
                } else {
                    sender.sendMessage("No blocks of that type for given player.");
                }

                return true;
            }

        } catch (Throwable throwable) {
            plugin.logger.logSevere(throwable);
        }

        return false;
    }

    class RemainingCommands {
        ArrayList<String> remainingCommands = new ArrayList<String>();

        String getString(int i) {
            return remainingCommands.get(i);
        }

        int getInt(int i) {
            return Integer.parseInt(remainingCommands.get(i));
        }

        double getDouble(int i) {
            return Float.parseFloat(remainingCommands.get(i));
        }

        boolean getBoolean(int i) {
            return Boolean.parseBoolean(remainingCommands.get(i));
        }
    }
}
