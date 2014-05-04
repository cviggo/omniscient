package viggo.omniscient;


import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;

public class ProtocolHook {

    private final Plugin omniPlugin;
    private ProtocolManager protocolManager;

    public ProtocolHook(Plugin plugin) {

        this.omniPlugin = plugin;
    }

    public void load() {
        this.protocolManager = ProtocolLibrary.getProtocolManager();

        final PacketAdapter packetAdapter = new PacketAdapter(omniPlugin, ListenerPriority.HIGHEST, PacketType.Play.Server.WORLD_EVENT) {

            // http://aadnk.github.io/ProtocolLib/Javadoc/com/comphenix/protocol/events/PacketAdapter.html
            @Override
            public void onPacketSending(PacketEvent event) {

                final StructureModifier<Integer> integers = event.getPacket().getIntegers();

                if (integers.size() < 1) {
                    return;
                }

                int id = integers.read(0);

                if (omniPlugin.settings == null) {
                    return;
                }

                switch (id) {
                    case 1013: // wither
                        if (omniPlugin.settings.disableWitherSound) {
                            event.setCancelled(true);

                            if (omniPlugin.settings.broadcastOnSoundDisablingEnabled) {
                                omniPlugin.broadcastQueue.add("Wither spawned");
                            }
                        }
                        break;

                    case 1018: // ender dragon
                        if (omniPlugin.settings.disableEnderDragonSound) {
                            event.setCancelled(true);

                            if (omniPlugin.settings.broadcastOnSoundDisablingEnabled) {
                                omniPlugin.broadcastQueue.add("Ender dragon killed");
                            }
                        }
                        break;
                }
            }
        };


        protocolManager.addPacketListener(packetAdapter);
    }
}
