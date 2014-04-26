package viggo.omniscient;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

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

    private boolean isCommand(String[] args, String baseCommand, String subCommand, int argCountExpected) {
        int argCountActual = args.length - (baseCommand != null
                ? (subCommand != null ? 2 : 1)
                : 0);

        if (argCountExpected == argCountActual) {
            if (baseCommand != null && args.length > 0) {
                if (!baseCommand.equalsIgnoreCase(args[0])) {
                    return false;
                }
            }

            if (subCommand != null && args.length > 1) {
                if (!subCommand.equalsIgnoreCase(args[1])) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd == null) {
            return false;
        }


        try {

            String name = cmd.getName();
            if (!name.equals("omni")) {
                return false;
            }

            if (args == null || args.length < 1) {
                return false;
            }

            if (isCommand(args, "dev", "enable", 0)) {
                plugin.setDebugPlayer(sender.getServer().getPlayer(sender.getName()), true);
                sender.sendMessage("dev enabled");
                return true;
            }

            if (isCommand(args, "dev", "disable", 0)) {
                plugin.setDebugPlayer(sender.getServer().getPlayer(sender.getName()), false);
                sender.sendMessage("dev disabled");
                return true;
            }

            if (isCommand(args, "blocks", "player", 3)) {
                return blocksPlayer(sender, args);
            }

            if (isCommand(args, "blocks", "radius", 1)) {
                return blocksRadius(sender, args);
            }

            if (isCommand(args, "sync", "world", 1)) {
                sender.sendMessage("sync world is not implemented yet!");
                return true;
            }

            if (isCommand(args, "sync", "allWorlds", 0)) {
                sender.sendMessage("sync all worlds is not implemented yet!");
                return true;
            }

            if (isCommand(args, "sync", "data", 0)) {
                return sync(sender, args);
            }

            if (isCommand(args, "reload", null, 0)) {
                reload(sender, args);
                return true;
            }

            if (isCommand(args, "limit", "enable", 0)) {
                plugin.settings.blockLimitsEnabled = true;
                sender.sendMessage("limits are now enabled");
                return true;
            }

            if (isCommand(args, "limit", "disable", 0)) {
                plugin.settings.blockLimitsEnabled = false;
                sender.sendMessage("limits are now disabled");
                return true;
            }

            if (isCommand(args, "stats", "enable", 0)) {
                plugin.settings.blockStatsEnabled = true;
                sender.sendMessage("stats are now enabled");
                return true;
            }

            if (isCommand(args, "stats", "disable", 0)) {
                plugin.settings.blockStatsEnabled = false;
                sender.sendMessage("stats are now disabled");
                return true;
            }

            if (isCommand(args, "scan", "loaded", 0)) {

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

            if (isCommand(args, "scan", "chunk", 0)) {
                final Location location = plugin.getServer().getPlayerExact(sender.getName()).getLocation();
                plugin.worldScannerEngine.queueChunkForScanning(location.getChunk(), false, true);
                sender.sendMessage(String.format("Enqueued current chunk for scanning"));

                return true;
            }

            if (isCommand(args, "scan", "chunks", 1)) {
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

                sender.sendMessage(String.format("Enqueued %d chunk for scanning", enqueuedChunks));
                return true;
            }

            // TODO: refactor - is currently mostly a copy of the above, but with chunk loading
            if (isCommand(args, "scan", "chunks", 2)) {
                final Location location = plugin.getServer().getPlayerExact(sender.getName()).getLocation();
                final Chunk locationChunk = location.getChunk();
                int radius = Integer.parseInt(args[2]);

                final boolean forceChunkLoading = Boolean.parseBoolean(args[3]);

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

                sender.sendMessage(String.format("Enqueued %d chunk for scanning", enqueuedChunks));
                return true;
            }

            if (isCommand(args, "scan", "enable", 0)) {
                plugin.settings.scanChunksOnLoad = true;
                plugin.settings.scanChunksPeriodicallyEnabled = true;
                sender.sendMessage("scan is now enabled (on chunk load and periodically)");
                return true;
            }

            if (isCommand(args, "scan", "disable", 0)) {
                plugin.settings.scanChunksOnLoad = false;
                plugin.settings.scanChunksPeriodicallyEnabled = false;
                sender.sendMessage("scan is now disabled (on chunk load and periodically)");
                return true;
            }

            if (isCommand(args, "state", null, 0)) {
                sender.sendMessage(
                        String.format("Last processed items %d secs ago.",
                                (new Date().getTime() - plugin.databaseEngine._lastRun.getTime()) / 1000
                        )
                );

                return true;
            }

            if (isCommand(args, "listWorlds", null, 0)) {
                final List<World> worlds = plugin.getServer().getWorlds();

                final List<String> worldNames = new ArrayList<String>();

                for (World world : worlds) {
                    worldNames.add(world.getName());
                }

                final String worldsString = join(worldNames, ", ").toLowerCase();

                sender.sendMessage(worldsString);

                return true;
            }

            if (isCommand(args, "chunk", "resend", 0)) {
                final Location playerLocation = plugin.getServer().getPlayer(sender.getName()).getLocation();
                playerLocation.getWorld().refreshChunk(playerLocation.getChunk().getX(), playerLocation.getChunk().getZ());
                return true;
            }

        } catch (Throwable throwable) {
            plugin.logger.logSevere(throwable);
        }

        return false;
    }

    private boolean reload(CommandSender sender, String[] args) {
        boolean wasReloaded = plugin.reload(sender);
        sender.sendMessage(String.format("Reloaded with success: %s", wasReloaded));
        return true;
    }

    private boolean sync(CommandSender sender, String[] args) {
        final boolean wasSynced = plugin.sync(sender);
        sender.sendMessage("Omniscient was synced: " + wasSynced);
        return wasSynced;
    }

    private boolean blocksPlayer(CommandSender sender, String[] args) {
        sender.sendMessage("WIP");
        return true;
    }

    private boolean blocksRadius(CommandSender sender, String[] args) {
        sender.sendMessage("WIP");
        return true;
    }
}
