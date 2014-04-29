package viggo.omniscient;

import java.io.Serializable;

public class BlockStat implements Serializable {
    int id;
    int current;
    int placed;
    int breaked;
    int blockId;
    int subValue;
    boolean isNewlyGenerated;

    public BlockStat(int id, int current, int placed, int breaked, int blockId, int subValue, boolean isNewlyGenerated) {
        this.id = id;
        this.current = current;
        this.placed = placed;
        this.breaked = breaked;
        this.blockId = blockId;
        this.subValue = subValue;
        this.isNewlyGenerated = isNewlyGenerated;
    }
}
