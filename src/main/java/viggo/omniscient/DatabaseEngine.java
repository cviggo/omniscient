package viggo.omniscient;

import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by viggo on 19-04-2014.
 */
public class DatabaseEngine implements Runnable {
    private final Plugin plugin;
    private final String dbHost;
    private final int dbPort;
    private final String dbCatalog;
    private final String dbUser;
    private final String dbPassword;
    private final Object engineThreadLock = new Object();
    private final Object connectionLock = new Object();
    Thread engineThread;
    Date _lastRun;
    private Connection conn = null;
    private ConcurrentLinkedQueue<UpdateTask<BlockInfo>> blockInfosToUpdate;
    private ConcurrentLinkedQueue<UpdateTask<BlockStat>> blockStatsToUpdate;
    private ConcurrentLinkedQueue<UpdateTask<BlockLimit>> blockLimitsToUpdate;
    private boolean doExitEngine;
    private volatile DataBaseEngineState state;

    DatabaseEngine(Plugin plugin, String dbHost, int dbPort, String dbCatalog, String dbUser, String dbPassword) {

        this.plugin = plugin;
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbCatalog = dbCatalog;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.blockInfosToUpdate = new ConcurrentLinkedQueue<UpdateTask<BlockInfo>>();
        this.blockStatsToUpdate = new ConcurrentLinkedQueue<UpdateTask<BlockStat>>();
        this.blockLimitsToUpdate = new ConcurrentLinkedQueue<UpdateTask<BlockLimit>>();
        this.doExitEngine = false;
        this.setState(DataBaseEngineState.Unknown);
    }

    public DataBaseEngineState getState() {
        return state;
    }

    private void setState(DataBaseEngineState state) {
        plugin.logger.logInfo(String.format("Database state transition %s to %s", this.state, state));
        this.state = state;
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

    @Override
    public void run() {

        while (!doExitEngine) {
            try {

                synchronized (connectionLock) {

                    //plugin.logger.logInfo(String.format("DB Queues: Infos: %d, Stats: %d, Stats: %d", blockInfosToUpdate.size(), blockStatsToUpdate.size(), blockLimitsToUpdate.size()));

                    if (persistConnection()) {

                        updateBlockInfos();
                        updateBlockStats();
                        updateBlockLimits();


                        if (this.state != DataBaseEngineState.Running) {
                            this.setState(DataBaseEngineState.Running);
                        }

                        _lastRun = new Date();
                    }
                }

            } catch (SQLException ex) {
                this.setState(DataBaseEngineState.Error);
                plugin.logger.logSevere("SQLException: " + ex.getMessage());
                plugin.logger.logSevere("SQLState: " + ex.getSQLState());
                plugin.logger.logSevere("VendorError: " + ex.getErrorCode());
                plugin.logger.logSevere(ex);
            }

            try {
                Thread.sleep(2000);
            } catch (Throwable t) {
                plugin.logger.logSevere(t);
            }
        }

        try {
            synchronized (connectionLock) {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            }
        } catch (Throwable t) {
            plugin.logger.logSevere(t);
        }
    }

    private boolean persistConnection() {
        try {

            if (conn == null || conn.isClosed()) {

                this.setState(DataBaseEngineState.Connecting);

                plugin.logger.logInfo(String.format("Connecting to database: %s:%d", dbHost, dbPort));

                conn =
                        DriverManager.getConnection(
                                String.format("jdbc:mysql://%s:%d?user=%s&password=%s",
                                        dbHost, dbPort, dbUser, dbPassword
                                )
                        );

                conn.setCatalog(dbCatalog);

                plugin.logger.logInfo("Connection to database ready for use.");
            }

            return true;

        } catch (Throwable t) {

            this.setState(DataBaseEngineState.ConnectionError);
            plugin.logger.logSevere(t);
            return false;
        }
    }

    private boolean waitForConnection(int milliseconds) {
        try {

            final Date begin = new Date();

            while (true) {
                boolean isConnectionValid;
                synchronized (connectionLock) {
                    isConnectionValid = !(conn == null || conn.isClosed());
                }

                if (isConnectionValid) {
                    return true;
                }

                if (new Date().getTime() - begin.getTime() > milliseconds) {
                    return false;
                }

                Thread.sleep(1);
            }

        } catch (Throwable t) {
            plugin.logger.logSevere(t);
            return false;
        }
    }

    private void updateBlockLimits() throws SQLException {
        if (blockLimitsToUpdate.size() > 0) {
            Statement statement = conn.createStatement();

            while (true) {
                final UpdateTask<BlockLimit> updateTask = blockLimitsToUpdate.poll();

                if (updateTask == null || updateTask.t == null) {
                    break;
                }

                final BlockLimit blockLimit = updateTask.t;
                String sql = null;

                switch (updateTask.type) {

                    case Save:

                        sql = String.format(
                                "INSERT INTO BlockLimit (`limit`, `limitGroup`, `blockId`, `subValue`, `blockDisplayName`) VALUES (%d, %d, %d, %d, '%s')",
                                blockLimit.limit,
                                blockLimit.limitGroup,
                                blockLimit.blockId,
                                blockLimit.subValue,
                                blockLimit.blockDisplayName
                        );

                        statement.addBatch(sql);
                        break;

                    case Delete:
                        sql = String.format(
                                "DELETE FROM BlockLimit WHERE (`blockId` = '%s' AND `subValue` = %d)",
                                blockLimit.blockId,
                                blockLimit.subValue
                        );

                        statement.addBatch(sql);
                        break;
                }
            }

            int[] results = statement.executeBatch();

            plugin.logger.logInfo(String.format("processed %d block limits", results.length));
        }
    }

    private void updateBlockInfos() throws SQLException {
        if (blockInfosToUpdate.size() > 0) {
            Statement statement = conn.createStatement();

            while (true) {
                UpdateTask<BlockInfo> updateTask = blockInfosToUpdate.poll();

                if (updateTask == null || updateTask.t == null) {
                    break;
                }

                BlockInfo blockInfo = updateTask.t;
                String sql;

                int currentTailedValues;

                switch (updateTask.type) {

                    case Save:
                        java.text.SimpleDateFormat sdf =
                                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


                        String time = sdf.format(blockInfo.placedWhen);

                        sql = "INSERT INTO BlockInfo (`blockId`, `world`, `x`, `y`, `z`, `placedBy`, `placedWhen`) VALUES ";

                        sql += String.format(
                                "('%s', '%s', %d, %d, %d, '%s', '%s')",
                                blockInfo.blockId,
                                blockInfo.world,
                                blockInfo.x,
                                blockInfo.y,
                                blockInfo.z,
                                blockInfo.placedBy,
                                time
                        );

                        currentTailedValues = 0;

                        while (blockInfosToUpdate.size() > 0) {

                            updateTask = blockInfosToUpdate.peek(); // just peek to see if the next item is also a save
                            if (updateTask == null || updateTask.t == null && updateTask.type == UpdateType.Save) {
                                break;
                            }

                            blockInfosToUpdate.poll(); // remove from queue

                            blockInfo = updateTask.t;

                            sql += String.format(
                                    ", ('%s', '%s', %d, %d, %d, '%s', '%s')",
                                    blockInfo.blockId,
                                    blockInfo.world,
                                    blockInfo.x,
                                    blockInfo.y,
                                    blockInfo.z,
                                    blockInfo.placedBy,
                                    time
                            );

                            if (currentTailedValues++ > 500) {
                                break;
                            }
                        }


                        statement.addBatch(sql);
                        break;

                    case Delete:
                        sql = String.format(
                                "DELETE FROM BlockInfo WHERE (`world`, `x`, `y`, `z`) IN ('%s', %d, %d, %d)",
                                blockInfo.world,
                                blockInfo.x,
                                blockInfo.y,
                                blockInfo.z
                        );

                        currentTailedValues = 0;

                        while (blockInfosToUpdate.size() > 0) {

                            updateTask = blockInfosToUpdate.peek(); // just peek to see if the next item is also a delete
                            if (updateTask == null || updateTask.t == null && updateTask.type == UpdateType.Delete) {
                                break;
                            }

                            blockInfosToUpdate.poll(); // remove from queue

                            blockInfo = updateTask.t;

                            sql += String.format(
                                    ", (%s, %d, %d, %d)",
                                    blockInfo.world,
                                    blockInfo.x,
                                    blockInfo.y,
                                    blockInfo.z
                            );

                            if (currentTailedValues++ > 500) {
                                break;
                            }
                        }

                        sql += ")";
                        statement.addBatch(sql);
                        break;
                }
            }

            plugin.logger.logInfo("Begin info batch");
            int[] results = statement.executeBatch();
            plugin.logger.logInfo("End info batch");

            plugin.logger.logInfo(String.format("processed %d block infos", results.length));
        }
    }

    private void updateBlockStats() throws SQLException {
        if (blockStatsToUpdate.size() > 0) {
            Statement statement = conn.createStatement();

            while (true) {
                final UpdateTask<BlockStat> updateTask = blockStatsToUpdate.poll();

                if (updateTask == null || updateTask.t == null) {
                    break;
                }

                final BlockStat blockStat = updateTask.t;
                String sql;

                switch (updateTask.type) {

                    case Save:
                        sql = String.format("CALL blockStatUpdate(%d, %d, %d, %d, %d, %d)",
                                blockStat.id,
                                blockStat.current,
                                blockStat.placed,
                                blockStat.breaked,
                                blockStat.blockId,
                                blockStat.subValue);

                        statement.addBatch(sql);
                        break;
                }
            }

            int[] results = statement.executeBatch();

            plugin.logger.logInfo(String.format("processed %d block stats", results.length));

        }
    }

    public void setBlockInfo(BlockInfo blockInfo) {
        blockInfosToUpdate.add(new UpdateTask<BlockInfo>(blockInfo, UpdateType.Save));
    }

//    public boolean hasUnsavedItems() {
//        return blockInfosToUpdate.size() > 0;
//    }

    public void deleteBlockInfo(BlockInfo blockInfo) {
        blockInfosToUpdate.add(new UpdateTask<BlockInfo>(blockInfo, UpdateType.Delete));
    }

    public void setBlockStat(BlockStat blockStat) {
        blockStatsToUpdate.add(new UpdateTask<BlockStat>(blockStat, UpdateType.Save));
    }

    public void setBlockLimit(BlockLimit blockLimit) {
        blockLimitsToUpdate.add(new UpdateTask<BlockLimit>(blockLimit, UpdateType.Save));
    }

    public void deleteBlockLimit(BlockLimit blockLimit) {
        blockLimitsToUpdate.add(new UpdateTask<BlockLimit>(blockLimit, UpdateType.Delete));
    }

    public Map<String, BlockLimit> getBlockLimits() {

        if (!waitForConnection(5000)) {
            return null;
        }

        Map<String, BlockLimit> blockLimits = new HashMap<String, BlockLimit>();

        synchronized (connectionLock) {
            try {
                Statement statement = conn.createStatement();

                final ResultSet resultSet = statement.executeQuery("SELECT * FROM BlockLimit");

                while (resultSet.next()) {
                    final BlockLimit blockLimit = new BlockLimit(
                            resultSet.getInt("id"),
                            resultSet.getInt("limit"),
                            resultSet.getInt("limitGroup"),
                            resultSet.getInt("blockId"),
                            resultSet.getInt("subValue"),
                            resultSet.getString("blockDisplayName"),
                            resultSet.getString("rank"),
                            resultSet.getString("world")
                    );

                    blockLimits.put(String.format("%d:%d", blockLimit.blockId, blockLimit.subValue), blockLimit);
                }
            } catch (SQLException e) {
                plugin.logger.logSevere(e.getMessage());
            }
        }

        return blockLimits;
    }

    public Map<String, Map<String, ArrayList<BlockInfo>>> getPlayerBlocks() {

        if (!waitForConnection(5000)) {
            return null;
        }

        Map<String, Map<String, ArrayList<BlockInfo>>> playerBlocks = new HashMap<String, Map<String, ArrayList<BlockInfo>>>();

        synchronized (connectionLock) {
            try {
                Statement statement = conn.createStatement();

                final ResultSet resultSet = statement.executeQuery("SELECT * FROM BlockInfo");

                while (resultSet.next()) {
                    final BlockInfo blockInfo = new BlockInfo(
                            resultSet.getInt("id"),
                            resultSet.getString("blockId"),
                            resultSet.getString("world"),
                            resultSet.getInt("x"),
                            resultSet.getInt("y"),
                            resultSet.getInt("z"),
                            resultSet.getString("placedBy"),
                            resultSet.getDate("placedWhen")
                    );

                    String playerName = blockInfo.placedBy;

                    // make sure there is a map for the player
                    if (!playerBlocks.containsKey(playerName)) {
                        playerBlocks.put(playerName, new HashMap<String, ArrayList<BlockInfo>>());
                    }

                    Map<String, ArrayList<BlockInfo>> map = playerBlocks.get(playerName);

                    // make sure there is a list available for the type of block
                    if (!map.containsKey(blockInfo.blockId)) {
                        ArrayList<BlockInfo> list = new ArrayList<BlockInfo>();
                        map.put(blockInfo.blockId, list);
                    }

                    ArrayList<BlockInfo> blockList = map.get(blockInfo.blockId);

                    blockList.add(blockInfo);

                }
            } catch (SQLException e) {
                plugin.logger.logSevere(e.getMessage());
            }
        }

        return playerBlocks;
    }

    public Map<String, BlockStat> getBlockStats() {

        if (!waitForConnection(5000)) {
            return null;
        }

        Map<String, BlockStat> blockStats = new HashMap<String, BlockStat>();

        synchronized (connectionLock) {

            try {
                Statement statement = conn.createStatement();

                final ResultSet resultSet = statement.executeQuery("SELECT * FROM BlockStat");

                while (resultSet.next()) {
                    final BlockStat blockStat = new BlockStat(
                            resultSet.getInt("id"),
                            resultSet.getInt("current"),
                            resultSet.getInt("placed"),
                            resultSet.getInt("breaked"),
                            resultSet.getInt("blockId"),
                            resultSet.getInt("subValue"),
                            false
                    );

                    String blockId = String.format("%d:%d", blockStat.blockId, blockStat.subValue);

                    blockStats.put(blockId, blockStat);
                }
            } catch (SQLException e) {
                plugin.logger.logSevere(e.getMessage());
            }
        }

        return blockStats;
    }

    enum DataBaseEngineState {
        Unknown,
        Connecting,
        ConnectionError,
        Error,
        Running
    }


    enum UpdateType {
        Save,
        Delete
    }

    class UpdateTask<T> {
        T t;
        UpdateType type;

        UpdateTask(T t, UpdateType type) {
            this.t = t;
            this.type = type;
        }
    }
}
