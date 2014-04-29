package viggo.omniscient;

import java.io.Serializable;

public class BlockLimit implements Serializable {
    int id;
    int limit;
    int limitGroup;
    int blockId;
    int subValue;
    String blockDisplayName;
    String rank;
    String world;

    public BlockLimit(int id, int limit, int limitGroup, int blockId, int subValue, String blockDisplayName, String rank, String world) {
        this.id = id;
        this.limit = limit;
        this.limitGroup = limitGroup;
        this.blockId = blockId;
        this.subValue = subValue;
        this.blockDisplayName = blockDisplayName;
        this.rank = rank;
        this.world = world;
    }

    public String getBlockKey() {
        return blockId + ":" + subValue;
    }
}
