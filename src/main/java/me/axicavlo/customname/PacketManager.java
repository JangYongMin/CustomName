package me.axicavlo.customname;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketManager {

    private final Customname plugin;
    private final ProtocolManager protocolManager;
    private final int ID_OFFSET = 1_000_000;

    private final Map<UUID, Map<UUID, Integer>> fakeEntities = new ConcurrentHashMap<>();

    private WrappedDataWatcher.Serializer getVector3Serializer() {
        try {
            Class<?> serializersClass = Class.forName("net.minecraft.network.syncher.EntityDataSerializers");
            java.lang.reflect.Field vector3Field = serializersClass.getField("VECTOR3");
            Object nmsSerializer = vector3Field.get(null);
            return new WrappedDataWatcher.Serializer(
                    (java.lang.reflect.Type) org.joml.Vector3f.class,
                    nmsSerializer,
                    false
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public PacketManager(Customname plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void registerPacketListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.PLAYER_INFO
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                modifyPlayerInfo(event);
            }
        });
    }

    private void modifyPlayerInfo(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        Set<EnumWrappers.PlayerInfoAction> actions = packet.getPlayerInfoActions().readSafely(0);
        if (actions != null && !actions.contains(EnumWrappers.PlayerInfoAction.ADD_PLAYER)) return;

        List<PlayerInfoData> infoDataList = packet.getPlayerInfoDataLists().readSafely(1);
        if (infoDataList == null || infoDataList.isEmpty()) return;

        List<PlayerInfoData> newList = new ArrayList<>();
        boolean modified = false;

        for (PlayerInfoData data : infoDataList) {
            if (data == null || data.getProfile() == null) {
                newList.add(data);
                continue;
            }
            UUID uuid = data.getProfileId();
            Player target = plugin.getServer().getPlayer(uuid);
            if (target != null) {
                String customName = plugin.getNickname(target);
                if (customName != null && !customName.isEmpty()) {
                    WrappedGameProfile newProfile = new WrappedGameProfile(uuid, customName);
                    WrappedGameProfile realProfile = WrappedGameProfile.fromPlayer(target);
                    newProfile.getProperties().putAll(realProfile.getProperties());
                    newList.add(new PlayerInfoData(uuid, data.getLatency(), data.isListed(), data.getGameMode(), newProfile, data.getDisplayName(), data.getRemoteChatSessionData()));
                    modified = true;
                    continue;
                }
            }
            newList.add(data);
        }
        if (modified) packet.getPlayerInfoDataLists().write(1, newList);
    }

    public void spawnFakeNameTag(Player viewer, Player target, String text) {
        try {
            int fakeEntityId = ID_OFFSET + target.getEntityId();

            PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            spawn.getIntegers().write(0, fakeEntityId);
            spawn.getUUIDs().write(0, UUID.randomUUID());
            spawn.getEntityTypeModifier().write(0, EntityType.TEXT_DISPLAY);
            spawn.getDoubles()
                    .write(0, target.getLocation().getX())
                    .write(1, target.getLocation().getY())
                    .write(2, target.getLocation().getZ());
            protocolManager.sendServerPacket(viewer, spawn);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try {
                    PacketContainer meta = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
                    meta.getIntegers().write(0, fakeEntityId);
                    List<WrappedDataValue> metadata = new ArrayList<>();

                    metadata.add(new WrappedDataValue(23, WrappedDataWatcher.Registry.getChatComponentSerializer(), WrappedChatComponent.fromText(text).getHandle()));
                    metadata.add(new WrappedDataValue(25, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Integer.class), 1073741824));
                    metadata.add(new WrappedDataValue(15, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class), (byte) 3));
                    metadata.add(new WrappedDataValue(12, getVector3Serializer(), new org.joml.Vector3f(1.0f, 1.0f, 1.0f)));
                    metadata.add(new WrappedDataValue(8, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Integer.class), 0));
                    metadata.add(new WrappedDataValue(9, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Integer.class), 0));
                    metadata.add(new WrappedDataValue(10, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Integer.class), 0));

                    // ⭐ [핵심 변경] 높이를 0.22f로 고정합니다.
                    // 0.28(서있음)과 0.15(앉음)의 중간값 정도이며,
                    // 탑승(Mount) 상태이므로 플레이어가 웅크리면 클라이언트가 알아서 이름표를 내립니다.
                    metadata.add(new WrappedDataValue(11, getVector3Serializer(), new org.joml.Vector3f(0.0f, 0.22f, 0.0f)));

                    boolean isSneaking = target.isSneaking();
                    byte opacity = isSneaking ? (byte) 120 : (byte) -1;
                    metadata.add(new WrappedDataValue(26, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class), opacity));
                    byte flags = isSneaking ? (byte) 0 : (byte) 2;
                    metadata.add(new WrappedDataValue(27, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class), flags));

                    meta.getDataValueCollectionModifier().write(0, metadata);
                    protocolManager.sendServerPacket(viewer, meta);

                } catch (Exception e) { e.printStackTrace(); }
            }, 2L);

            try {
                PacketContainer mount = protocolManager.createPacket(PacketType.Play.Server.MOUNT);
                mount.getIntegers().write(0, target.getEntityId());
                mount.getIntegerArrays().write(0, new int[]{fakeEntityId});
                protocolManager.sendServerPacket(viewer, mount);
            } catch (Exception e) { e.printStackTrace(); }

            fakeEntities.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>()).put(target.getUniqueId(), fakeEntityId);

        } catch (Exception e) { e.printStackTrace(); }
    }

    // ⭐ 웅크리기 상태 업데이트 (높이 변경 코드 삭제 -> 지연 삭제)
    public void updateSneakState(Player viewer, Player target, boolean isSneaking) {
        try {
            Map<UUID, Integer> watcherMap = fakeEntities.get(viewer.getUniqueId());
            if (watcherMap == null) return;
            Integer fakeId = watcherMap.get(target.getUniqueId());
            if (fakeId == null) return;

            PacketContainer meta = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            meta.getIntegers().write(0, fakeId);

            List<WrappedDataValue> metadata = new ArrayList<>();

            // 🚨 [중요] 여기서 Index 11 (Translation/높이) 패킷을 아예 보내지 않습니다.
            // 높이 패킷을 보내지 않으면, 클라이언트는 "탑승 상태" 물리 엔진에 따라
            // 머리가 내려가는 애니메이션과 100% 동일한 속도로 이름표를 내립니다. (지연 0ms)

            // 1. Opacity (불투명도만 변경)
            byte opacity = isSneaking ? (byte) 120 : (byte) -1;
            metadata.add(new WrappedDataValue(
                    26,
                    WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class),
                    opacity
            ));

            // 2. Flags (벽 투시 설정만 변경)
            byte flags = isSneaking ? (byte) 0 : (byte) 2;
            metadata.add(new WrappedDataValue(
                    27,
                    WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class),
                    flags
            ));

            meta.getDataValueCollectionModifier().write(0, metadata);
            protocolManager.sendServerPacket(viewer, meta);

        } catch (Exception e) { e.printStackTrace(); }
    }

    public void removeAllFakeEntities(Player watcher) {
        Map<UUID, Integer> watcherMap = fakeEntities.remove(watcher.getUniqueId());
        if (watcherMap == null || watcherMap.isEmpty()) return;
        try {
            List<Integer> idsToRemove = new ArrayList<>(watcherMap.values());
            PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroy.getIntLists().write(0, idsToRemove);
            protocolManager.sendServerPacket(watcher, destroy);
        } catch (Exception e) { plugin.getLogger().warning("전체 제거 실패: " + e.getMessage()); }
    }

    public void removeFakeNameTag(Player watcher, Player target) {
        try {
            Map<UUID, Integer> watcherMap = fakeEntities.get(watcher.getUniqueId());
            if (watcherMap == null) return;
            Integer fakeId = watcherMap.remove(target.getUniqueId());
            if (fakeId == null) return;
            PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroy.getIntLists().write(0, List.of(fakeId));
            protocolManager.sendServerPacket(watcher, destroy);
        } catch (Exception e) { plugin.getLogger().warning("개별 제거 실패: " + e.getMessage()); }
    }
}