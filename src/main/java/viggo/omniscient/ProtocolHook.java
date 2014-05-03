package viggo.omniscient;


import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

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

                final int currentId = event.getPacket().getType().getCurrentId();

                if (currentId == PacketType.UNKNOWN_PACKET || omniPlugin.settings == null) {
                    return;
                }

                switch (currentId) {
                    case 1013: // wither
                        if (omniPlugin.settings.disableWitherSound) {
                            event.setCancelled(true);
                        }
                        break;

                    case 1018: // ender dragon
                        if (omniPlugin.settings.disableEnderDragonSound) {
                            event.setCancelled(true);
                        }
                        break;
                }
            }
        };


        protocolManager.addPacketListener(packetAdapter);
    }
}
