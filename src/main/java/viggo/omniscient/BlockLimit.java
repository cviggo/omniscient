package viggo.omniscient;

import java.io.Serializable;
import java.util.Date;

public class BlockLimit implements Serializable {
    int id;
    int limit;
    String limitGroup;
    int blockId;
    int subValue;
    String world;
    String blockDisplayName;
    String name;
    String class_;
    Date updated;

    public BlockLimit(int id, int limit, String limitGroup, int blockId, int subValue, String world, String blockDisplayName, String name, String class_, Date updated) {
        this.id = id;
        this.limit = limit;
        this.limitGroup = limitGroup;
        this.blockId = blockId;
        this.subValue = subValue;
        this.world = world;
        this.blockDisplayName = blockDisplayName;
        this.name = name;
        this.class_ = class_;
        this.updated = updated;
    }

    public String getBlockKey() {
        return blockId + ":" + subValue;
    }

    public boolean isLimitedInWorld(String worldName) {
        if (world != null) {

            final String[] worldNames = world.split(",");

            boolean foundAny = false;

            for (String worldName_ : worldNames) {
                if (worldName_.equals(worldName)) {
                    foundAny = true;
                    break;
                }
            }

            return foundAny;
        }

        return true;
    }
}
