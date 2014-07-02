package viggo.omniscient;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

import static org.reflections.ReflectionUtils.getAllMethods;

public class CommandExecutor implements org.bukkit.command.CommandExecutor {
    private final Plugin plugin;
    private final Logger logger;

    public CommandExecutor(Plugin plugin, Logger logger) {

        this.plugin = plugin;
        this.logger = logger;
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
                //plugin.logger.logInfo("NEX: " + expectedCommands[0]);
                return false;
            }
        }

        int remainingCommandsCnt = args.length - expectedCommands.length;

        for (int i = args.length - remainingCommandsCnt; i < args.length && i >= 0; i++) {
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

            if (isCommand(args, r, "disable")) {
                plugin.onDisable();
                return true;
            }

            if (isCommand(args, r, "enable")) {
                plugin.onEnable();
                return true;
            }

//            if (isCommand(args, r, "test", "placeBlocks") /*&& r.remainingCommands.size() == 3*/) {
//                final int dist = r.getInt(0);
//                final int id = r.getInt(1);
//                final int blockSubValue = r.getInt(2);
//                final String blockId = id + ":" + blockSubValue;
//
//                boolean assignPlayer = r.remainingCommands.size() == 4 && r.getBoolean(3);
//
//                final Player player = sender.getServer().getPlayer(sender.getName());
//                final Location location = player.getLocation();
//                final World world = location.getWorld();
//
//                final int x = (int) location.getX();
//                final int y = (int) location.getY();
//                final int z = (int) location.getZ();
//
//                String playerName = player.getName();
//
//                // make sure there is a map for the player
//                if (!plugin.playerBlocks.containsKey(playerName)) {
//                    plugin.playerBlocks.put(playerName, new HashMap<String, ArrayList<BlockInfo>>());
//                }
//
//                Map<String, ArrayList<BlockInfo>> map = plugin.playerBlocks.get(playerName);
//
//                // make sure there is a list available for the type of block
//                if (!map.containsKey(blockId)) {
//                    ArrayList<BlockInfo> list = new ArrayList<BlockInfo>();
//                    map.put(blockId, list);
//                }
//
//                ArrayList<BlockInfo> blockList = map.get(blockId);
//
//
//                for (int ix = -dist; ix < dist; ix++) {
//                    for (int iy = -dist; iy < dist; iy++) {
//                        for (int iz = -dist; iz < dist; iz++) {
//                            final Block block = world.getBlockAt(x + ix, (255 - dist) + iy, z + iz);
//                            block.setTypeIdAndData(id, (byte) blockSubValue, false);
//
//                            if (assignPlayer) {
//                                BlockInfo blockInfo = new BlockInfo(0, blockId, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), playerName, new Date());
//
//                                // add to list of blocks
//                                blockList.add(blockInfo);
//
//                                // add to coordinate to player map
//                                plugin.playerToBlockCoordsMap.put(plugin.getBlockKeyFromInfo(blockInfo), blockInfo);
//
//                                // add to database
//                                plugin.databaseEngine.setBlockInfo(blockInfo);
//                            }
//                        }
//                    }
//                }
//
//                sender.sendMessage("placed blocks");
//                return true;
//            }

            if (isCommand(args, r, "test", "removeBlocks") && r.remainingCommands.size() == 1) {
                final int dist = r.getInt(0);
//                final int blockId = r.getInt(1);
//                final int blockSubValue = r.getInt(2);

                final Player player = sender.getServer().getPlayer(sender.getName());
                final Location location = player.getLocation();
                final World world = location.getWorld();

                final int x = (int) location.getX();
                final int y = (int) location.getY();
                final int z = (int) location.getZ();

                for (int ix = -dist; ix < dist; ix++) {
                    for (int iy = -dist; iy < dist; iy++) {
                        for (int iz = -dist; iz < dist; iz++) {
                            world.getBlockAt(x + ix, (255 - dist) + iy, z + iz).setType(Material.AIR);
                        }
                    }
                }

                sender.sendMessage("removed blocks");
                return true;
            }

//            if (isCommand(args, r, "test", "removeBlocks") && r.remainingCommands.size() == 3) {
//                final int dist = r.getInt(0);
//                final int x = r.getInt(1);
//                final int z = r.getInt(2);
//
//                final Player player = sender.getServer().getPlayer(sender.getName());
//                final Location location = player.getLocation();
//                final World world = location.getWorld();
//
//
//                //final int x = (int) location.getX();
//                final int y = (int) location.getY();
//                //final int z = (int) location.getZ();
//
//                for (int ix = -dist; ix < dist; ix++) {
//                    for (int iy = -dist; iy < dist; iy++) {
//                        for (int iz = -dist; iz < dist; iz++) {
//                            world.getBlockAt(x + ix, (255 - dist) + iy, z + iz).setType(Material.AIR);
//                        }
//                    }
//                }
//
//                sender.sendMessage("removed blocks");
//                return true;
//            }

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

            if (isCommand(args, r, "settings", "dump")) {

                Reflections reflections = new Reflections("viggo.omniscient");

                //reflections.

                //final Set<Field> allFields = getAllFields(Settings.class);
//                for (Field field : allFields) {
//                    plugin.logger.logInfo(field.getName() + ": " + field.toString());
//                }

                plugin.logger.logInfo(plugin.settings.maximumUnknownBlocksToProcessPerTick + "");

                return true;
            }

            if (isCommand(args, r, "sign")) {
                final Player player = sender.getServer().getPlayer(sender.getName());
//                final ItemStack itemOnCursor = player.getItemOnCursor();
//                if (itemOnCursor == null){
//                    return true;
//                }

                final Location location = player.getLocation();

                final World world = player.getWorld();

                final Block blockAt = world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ());

                if (blockAt.getType() == Material.AIR) {
                    return true;
                }

                final Block blockAtLegs = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());

                if (blockAtLegs.getType() != Material.AIR) {
                    return true;
                }

                blockAtLegs.setTypeIdAndData(3391, (byte) 0, true);

                sender.sendMessage(blockAtLegs.getState().toString());

                Reflections reflections = new Reflections("viggo.omniscient");

                //final Set<Class<? extends DatabaseEngine>> subTypesOf = reflections.getSubTypesOf(DatabaseEngine.class);


                final Set<Method> allMethods = getAllMethods(Class.forName("myrathi.flatsigns.FlatSigns"));

                sender.sendMessage("allMethods: " + allMethods.size());

                //blockAtLegs.getState();

//                final org.bukkit.material.Sign signData = new org.bukkit.material.Sign(Material.SIGN_POST);
//                signData.setFacingDirection(blockFace);
//                sign.setData(signData);
//
//                sign.setLine(0, "99:99");
//                sign.setLine(1, "was replaced by");
//                sign.setLine(2, "Omniscient");
//
//                java.text.SimpleDateFormat sdf =
//                        new java.text.SimpleDateFormat("dd/MM HH:mm:ss");
//
//                String time = sdf.format(new Date());
//
//                sign.setLine(3, time);
//
//
//                sign.update(true, false);



                return true;
            }

            if (isCommand(args, r, "inv", "import")) {
                final Player player = sender.getServer().getPlayer(sender.getName());

                final Location location = player.getLocation();

                final World world = player.getWorld();

                final Block blockAt = world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ());

                if (blockAt.getType() == Material.CHEST) {
                    Chest chest = (Chest) blockAt.getState();
                } else {
                    sender.sendMessage(blockAt.getType().toString());
                    //blockAt.get
                }

                //org.bukkit.material.Chest chest = (org.bukkit.material.Chest)blockAt.getState().getBlock().;

                //sender.sendMessage(chest.getBlockInventory().getItem(0).toString());

                return true;
            }

            if (isCommand(args, r, "reload", "ignoreEmptyBlockInfo")) {
                boolean wasReloadStarted = plugin.beginReload(sender, true, false);
                if (!wasReloadStarted) {
                    sender.sendMessage("Reloaded could not be started. Perhaps try again.");
                } else {
                    sender.sendMessage("Reloaded started. Once reload is done, you will receive a message.");
                }

                return true;
            }

            if (isCommand(args, r, "reload")) {
                boolean wasReloadStarted = plugin.beginReload(sender, false, false);
                if (!wasReloadStarted) {
                    sender.sendMessage("Reloaded could not be started. Perhaps try again.");
                } else {
                    sender.sendMessage("Reloaded started. Once reload is done, you will receive a message.");
                }

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

            if (isCommand(args, r, "sync", "enable")) {
                plugin.settings.syncRemovedBlocksPeriodicallyEnabled = true;
                plugin.settings.syncRemovedBlocksOnEventsEnabled = true;
                sender.sendMessage("sync is now enabled (periodic and events)");
                return true;
            }

            if (isCommand(args, r, "sync", "disable")) {
                plugin.settings.syncRemovedBlocksPeriodicallyEnabled = false;
                plugin.settings.syncRemovedBlocksOnEventsEnabled = false;
                sender.sendMessage("sync is now disabled (periodic and events)");
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
                final String limitGroup = r.getString(1);
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
                        String.format("Last processed items %d secs ago. DBE state: %s",
                                (new Date().getTime() - plugin.databaseEngine._lastRun.getTime()) / 1000,
                                plugin.databaseEngine.getState()
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

                final String worldsString = Utils.join(worldNames, ", ").toLowerCase();

                sender.sendMessage(worldsString);

                return true;
            }

            if (isCommand(args, r, "chunk", "resend")) {
                final Location playerLocation = plugin.getServer().getPlayer(sender.getName()).getLocation();
                playerLocation.getWorld().refreshChunk(playerLocation.getChunk().getX(), playerLocation.getChunk().getZ());
                return true;
            }

//            if (isCommand(args, r, "blocks", "list") && r.remainingCommands.size() < 1) {
//                for (String item : r.remainingCommands) {
//                    plugin.logger.logInfo(item);
//                }
//
//                final int blockId = r.getInt(0);
//                final int subValue = r.getInt(1);
//                final String blockIdStr = blockId + ":" + subValue;
//
//                final Map<String, ArrayList<BlockInfo>> map = plugin.playerBlocks.get(sender.getName());
//
//                if (map == null || !map.containsKey(blockIdStr)) {
//                    sender.sendMessage("No blocks of that type for given player.");
//                    return true;
//                }
//
//                final ArrayList<BlockInfo> blockInfos = map.get(blockIdStr);
//
//                if (blockInfos.size() > 0) {
//                    int cnt = 0;
//                    for (BlockInfo blockInfo : blockInfos) {
//                        sender.sendMessage(String.format("%s: %d.%d.%d", blockInfo.world, blockInfo.x, blockInfo.y, blockInfo.z));
//
//                        if (++cnt > 20) {
//                            sender.sendMessage("Reached maximum amount of blocks to list.");
//                            break;
//                        }
//                    }
//                } else {
//                    sender.sendMessage("No blocks of that type for given player.");
//                }
//
//                return true;
//            }
//
//            if (isCommand(args, r, "blocks", "list")) {
//
//                int currentCnt = 0;
//                int maxCnt = 100;
//
//                final Map<String, ArrayList<BlockInfo>> map = plugin.playerBlocks.get(sender.getName());
//                final Set<String> keySet = map.keySet();
//                for (String key : keySet) {
//
//                    final ArrayList<BlockInfo> blockInfos = map.get(key);
//
//                    for (BlockInfo blockInfo : blockInfos) {
//
//                        sender.sendMessage(String.format("%s: %s", key, plugin.getBlockKeyFromInfo(blockInfo)));
//
//                        if (++currentCnt > maxCnt) {
//
//                            sender.sendMessage(String.format("Reached max count of %d", currentCnt));
//                            return true;
//                        }
//                    }
//                }
//
//                sender.sendMessage(String.format("Found a total of %d blocks", currentCnt));
//                return true;
//            }

            if (isCommand(args, r, "unknown", "list")) {

//                int currentCnt = 0;
//                int maxCnt = 100;
//
//
//                    final BlockInfo[] blockInfos = (BlockInfo[])plugin.unknownBlocksFound.toArray();
//
//                    for (BlockInfo blockInfo : blockInfos){
//
//                        sender.sendMessage(String.format("%s: %s", blockInfo.blockId, plugin.getBlockKeyFromInfo(blockInfo)));
//
//                        if (++currentCnt > maxCnt){
//
//                            sender.sendMessage(String.format("Reached max count of %d", currentCnt));
//                            return true;
//                        }
//                    }
//
//                sender.sendMessage(String.format("Found a total of %d blocks", currentCnt));
                return true;
            }

            if (isCommand(args, r, "superSmite")) {
                final Player player = sender.getServer().getPlayer(sender.getName());
                final World world = player.getWorld();
                final Location location = player.getLocation();
                final double x = location.getX();
                final double z = location.getZ();

                double rx = 1;
                double maxRadiusOffset = 50;
                double a = x;
                double b = z;

                for (double radiusOffset = maxRadiusOffset; radiusOffset >= 0; radiusOffset -= 10) {


                    for (double i = 0.0; i < 6.26; i += 0.01) {

                        double cx = x + (rx + radiusOffset) * Math.cos(i);
                        double zx = z + (rx + radiusOffset) * Math.sin(i);

                        world.strikeLightning(new Location(world, cx, location.getY(), zx));
                    }
                }
            }

            if (isCommand(args, r, "forceRun")) {
                plugin.setState(PluginState.Running);
                return true;
            }

        } catch (Throwable throwable) {
            plugin.logger.logSevere(throwable);
            sender.sendMessage(throwable.getMessage());
            return true;
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
