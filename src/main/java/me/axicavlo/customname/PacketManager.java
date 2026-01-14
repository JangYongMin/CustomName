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

    // ⭐ [수정 1] Serializer 생성자 에러 해결: (java.lang.reflect.Type) 캐스팅 추가
    private WrappedDataWatcher.Serializer getVector3Serializer() {
        try {
            Class<?> serializersClass = Class.forName("net.minecraft.network.syncher.EntityDataSerializers");
            java.lang.reflect.Field vector3Field = serializersClass.getField("VECTOR3");
            Object nmsSerializer = vector3Field.get(null);

            // Class를 Type으로 캐스팅하여 올바른 생성자를 호출하도록 유도합니다.
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

        // ⭐ [수정 2] EnumSet -> Set으로 변경 (타입 불일치 해결)
        Set<EnumWrappers.PlayerInfoAction> actions = packet.getPlayerInfoActions().readSafely(0);

        // actions가 null이거나 ADD_PLAYER가 없으면 리턴
        if (actions != null && !actions.contains(EnumWrappers.PlayerInfoAction.ADD_PLAYER)) {
            return;
        }

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

                    newList.add(new PlayerInfoData(
                            uuid,
                            data.getLatency(),
                            data.isListed(),
                            data.getGameMode(),
                            newProfile,
                            data.getDisplayName(),
                            data.getRemoteChatSessionData()
                    ));

                    modified = true;
                    continue;
                }
            }
            newList.add(data);
        }

        if (modified) {
            packet.getPlayerInfoDataLists().write(1, newList);
        }
    }

    public void spawnFakeNameTag(Player viewer, Player target, String text) {
        try {
            int fakeEntityId = ID_OFFSET + target.getEntityId();

            // 1. SPAWN_ENTITY
            PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            spawn.getIntegers().write(0, fakeEntityId);
            spawn.getUUIDs().write(0, UUID.randomUUID());
            spawn.getEntityTypeModifier().write(0, EntityType.TEXT_DISPLAY);
            spawn.getDoubles()
                    .write(0, target.getLocation().getX())
                    .write(1, target.getLocation().getY())
                    .write(2, target.getLocation().getZ());
            protocolManager.sendServerPacket(viewer, spawn);

            // 2. ENTITY_METADATA
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try {
                    PacketContainer meta = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
                    meta.getIntegers().write(0, fakeEntityId);
                    List<WrappedDataValue> metadata = new ArrayList<>();

                    // Text (23)
                    metadata.add(new WrappedDataValue(
                            23,
                            WrappedDataWatcher.Registry.getChatComponentSerializer(),
                            WrappedChatComponent.fromText(text).getHandle()
                    ));

                    // Background (25)
                    metadata.add(new WrappedDataValue(
                            25,
                            WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Integer.class),
                            1073741824
                    ));

                    // Billboard (15)
                    metadata.add(new WrappedDataValue(
                            15,
                            WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class),
                            (byte) 3
                    ));

                    // Translation (11) - 높이 조절
                    metadata.add(new WrappedDataValue(
                            11,
                            getVector3Serializer(),
                            new org.joml.Vector3f(0.0f, 0.3f, 0.0f) // 머리 위 높이
                    ));

                    // Scale (12)
                    metadata.add(new WrappedDataValue(
                            12,
                            getVector3Serializer(),
                            new org.joml.Vector3f(1.0f, 1.0f, 1.0f)
                    ));

                    meta.getDataValueCollectionModifier().write(0, metadata);
                    protocolManager.sendServerPacket(viewer, meta);

                } catch (Exception e) { e.printStackTrace(); }
            }, 2L);

            // 3. MOUNT
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try {
                    PacketContainer mount = protocolManager.createPacket(PacketType.Play.Server.MOUNT);
                    mount.getIntegers().write(0, target.getEntityId());
                    mount.getIntegerArrays().write(0, new int[]{fakeEntityId});
                    protocolManager.sendServerPacket(viewer, mount);
                } catch (Exception e) { e.printStackTrace(); }
            }, 4L);

            fakeEntities
                    .computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                    .put(target.getUniqueId(), fakeEntityId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeAllFakeEntities(Player watcher) {
        Map<UUID, Integer> watcherMap = fakeEntities.remove(watcher.getUniqueId());
        if (watcherMap == null || watcherMap.isEmpty()) return;

        try {
            List<Integer> idsToRemove = new ArrayList<>(watcherMap.values());
            PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroy.getIntLists().write(0, idsToRemove);
            protocolManager.sendServerPacket(watcher, destroy);
        } catch (Exception e) {
            plugin.getLogger().warning("전체 가짜 엔티티 제거 실패: " + e.getMessage());
        }
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
        } catch (Exception e) {
            plugin.getLogger().warning("가짜 엔티티 제거 실패: " + e.getMessage());
        }
    }
}