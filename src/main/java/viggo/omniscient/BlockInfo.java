package viggo.omniscient;

import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.io.Serializable;
import java.util.Date;

public class BlockInfo implements Serializable {

    int id;
    String blockId;
    String world;
    int x;
    int y;
    int z;
    String placedBy;
    @Temporal(TemporalType.TIMESTAMP)
    Date placedWhen;

    public BlockInfo(int id, String blockId, String world, int x, int y, int z, String placedBy, Date placedWhen) {
        this.id = id;
        this.blockId = blockId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.placedBy = placedBy;
        this.placedWhen = placedWhen;
    }
}
