package me.axicavlo.customname;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

import java.util.ArrayList;
import java.util.List;

public class PacketManager {

    private final Customname plugin;

    public PacketManager(Customname plugin){
        this.plugin = plugin;
    }

    public void registerAllPacketDebugger() {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();

        plugin.getLogger().info("=== 패킷 리스너 등록 ===");

        // 가능한 모든 PLAYER 관련 패킷 시도
        List<PacketType> playerPackets = new ArrayList<>();

        // 일반적으로 사용되는 패킷들
        try { playerPackets.add(PacketType.Play.Server.PLAYER_INFO); } catch (Exception e) {}
        try { playerPackets.add(PacketType.Play.Server.NAMED_ENTITY_SPAWN); } catch (Exception e) {}
        try { playerPackets.add(PacketType.Play.Server.ENTITY_METADATA); } catch (Exception e) {}
        try { playerPackets.add(PacketType.Play.Server.SPAWN_ENTITY); } catch (Exception e) {}

        plugin.getLogger().info("등록된 패킷 수: " + playerPackets.size());

        if (!playerPackets.isEmpty()) {
            manager.addPacketListener(new PacketAdapter(
                    plugin,
                    ListenerPriority.MONITOR,
                    playerPackets
            ) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    plugin.getLogger().info("[패킷 감지] " + event.getPacketType().name()
                            + " -> " + event.getPlayer().getName());
                }
            });
        }
    }
}
