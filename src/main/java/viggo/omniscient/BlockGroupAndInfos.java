package viggo.omniscient;

import java.util.ArrayList;
import java.util.Map;

/**
 * Created by viggo on 01-07-2014.
 */
public class BlockGroupAndInfos {
    // <groupName, count of blocks in group>
    Map<String, GroupCount> groupMap;

    // <blockId, list of block info>
    Map<String, ArrayList<BlockInfo>> blockMap;
}
