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

/**
 * ProtocolLib을 사용하여 패킷 기반의 가짜 엔티티를 관리하는 클래스입니다.
 */
public class PacketManager {

    private final Customname plugin;
    private final ProtocolManager protocolManager;
    private final int ID_OFFSET = 1_000_000; // 가짜 엔티티 ID 생성을 위한 오프셋

    // 각 플레이어(Viewer)가 보고 있는 대상 플레이어(Target)의 가짜 엔티티 ID 저장
    private final Map<UUID, Map<UUID, Integer>> fakeEntities = new ConcurrentHashMap<>();

    /**
     * NMS 내부에서 사용하는 Vector3f 시리얼라이저를 리플렉션으로 가져옵니다.
     */
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

    /**
     * PLAYER_INFO 패킷을 감지하여 탭 리스트의 이름을 커스텀 이름으로 변조합니다.
     */
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

        // 1. 액션 목록 확인 (ADD_PLAYER 또는 UPDATE_DISPLAY_NAME 등 포함 여부)
        Set<EnumWrappers.PlayerInfoAction> actions = packet.getPlayerInfoActions().readSafely(0);
        if (actions == null) return;

        // 2. 데이터 리스트 읽기 (Index 0 또는 1 확인 필요, 일반적으로 0 사용)
        List<PlayerInfoData> infoDataList = packet.getPlayerInfoDataLists().readSafely(0);
        if (infoDataList == null || infoDataList.isEmpty()) {
            infoDataList = packet.getPlayerInfoDataLists().readSafely(1); // 0이 비었을 경우 1 확인
        }
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
                    // 기존 프로필(스킨 포함)은 그대로 유지하고 DisplayName만 교체
                    // WrappedChatComponent.fromAdventureComponent가 안될 경우 아래 JSON 방식 사용

                    String jsonName = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()
                            .serialize(net.kyori.adventure.text.Component.text(customName));

                    newList.add(new PlayerInfoData(
                            data.getProfileId(),
                            data.getLatency(),
                            data.isListed(),
                            data.getGameMode(),
                            data.getProfile(), // 기존 프로필 그대로 사용하여 스킨 보존 및 NPE 방지
                            WrappedChatComponent.fromJson(jsonName), // 닉네임 설정
                            data.getRemoteChatSessionData()
                    ));
                    modified = true;
                    continue;
                }
            }
            newList.add(data);
        }

        // 3. 수정된 리스트 덮어쓰기
        if (modified) {
            // 읽어왔던 인덱스에 맞춰서 기록
            if (packet.getPlayerInfoDataLists().readSafely(0) != null) {
                packet.getPlayerInfoDataLists().write(0, newList);
            } else {
                packet.getPlayerInfoDataLists().write(1, newList);
            }
        }
    }

    /**
     * 가짜 Text Display 엔티티를 생성하고 플레이어에게 탑승(Mount)시킵니다.
     * 부활 시 마운트 해제 문제를 방지하기 위해 패킷 전송 순서를 최적화했습니다.
     */
    public void spawnFakeNameTag(Player viewer, Player target, String text) {
        try {
            int fakeEntityId = ID_OFFSET + target.getEntityId(); // 가짜 엔티티 ID 생성

            org.bukkit.Location eyeLoc = target.getEyeLocation();

            // 1. 가짜 엔티티 소환 패킷 생성 및 전송
            PacketContainer spawn = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            spawn.getIntegers().write(0, fakeEntityId);
            spawn.getUUIDs().write(0, UUID.randomUUID());
            spawn.getEntityTypeModifier().write(0, EntityType.TEXT_DISPLAY);
            spawn.getDoubles()
                    .write(0, eyeLoc.getX())
                    .write(1, eyeLoc.getY() + 0.18)
                    .write(2, eyeLoc.getZ());

            protocolManager.sendServerPacket(viewer, spawn);

            // 2. 엔티티 메타데이터 설정 (2틱 지연 후 전송)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try {
                    PacketContainer meta = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
                    meta.getIntegers().write(0, fakeEntityId);
                    List<WrappedDataValue> metadata = new ArrayList<>();

                    // 텍스트 설정 (Index 23)
                    metadata.add(new WrappedDataValue(23, WrappedDataWatcher.Registry.getChatComponentSerializer(), WrappedChatComponent.fromText(text).getHandle()));

                    // 배경색 설정 (Index 25)
                    metadata.add(new WrappedDataValue(25, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Integer.class), 1073741824));

                    // 빌보드 모드 설정 (Index 15)
                    metadata.add(new WrappedDataValue(15, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class), (byte) 3));

                    // 크기 설정 (Index 12)
                    metadata.add(new WrappedDataValue(12, getVector3Serializer(), new org.joml.Vector3f(1.0f, 1.0f, 1.0f)));

                    // 애니메이션 보간 시간 제거 (Index 8, 9, 10)
                    metadata.add(new WrappedDataValue(8, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Integer.class), 0));
                    metadata.add(new WrappedDataValue(9, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Integer.class), 0));
                    metadata.add(new WrappedDataValue(10, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Integer.class), 0));

                    // 높이 고정 (Index 11) - 0.22f
                    metadata.add(new WrappedDataValue(11, getVector3Serializer(), new org.joml.Vector3f(0.0f, 0.22f, 0.0f)));

                    // 웅크리기 상태 적용 (Index 26, 27)
                    boolean isSneaking = target.isSneaking();
                    byte opacity = isSneaking ? (byte) 120 : (byte) -1;
                    metadata.add(new WrappedDataValue(26, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class), opacity));

                    byte flags = isSneaking ? (byte) 0 : (byte) 2;
                    metadata.add(new WrappedDataValue(27, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class), flags));

                    meta.getDataValueCollectionModifier().write(0, metadata);
                    protocolManager.sendServerPacket(viewer, meta);

                    // 3. 마운트 패킷 전송 (메타데이터 전송 1틱 후)
                    // 클라이언트가 가짜 엔티티의 존재를 인지한 직후에 탑승시켜야 위치가 고정됩니다.
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        try {
                            PacketContainer mount = protocolManager.createPacket(PacketType.Play.Server.MOUNT);
                            mount.getIntegers().write(0, target.getEntityId()); // 말(플레이어)
                            mount.getIntegerArrays().write(0, new int[]{fakeEntityId}); // 기수(가짜 이름표)
                            protocolManager.sendServerPacket(viewer, mount);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }, 1L);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 2L);

            // 엔티티 ID 맵에 저장
            fakeEntities.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>()).put(target.getUniqueId(), fakeEntityId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 플레이어의 웅크리기 상태에 따라 이름표의 메타데이터(불투명도, 가시성)를 업데이트합니다.
     */
    public void updateSneakState(Player viewer, Player target, boolean isSneaking) {
        try {
            Map<UUID, Integer> watcherMap = fakeEntities.get(viewer.getUniqueId());
            if (watcherMap == null) return;

            Integer fakeId = watcherMap.get(target.getUniqueId());
            if (fakeId == null) return;

            PacketContainer meta = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            meta.getIntegers().write(0, fakeId);
            List<WrappedDataValue> metadata = new ArrayList<>();

            // 불투명도 업데이트 (Index 26)
            byte opacity = isSneaking ? (byte) 120 : (byte) -1;
            metadata.add(new WrappedDataValue(26, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class), opacity));

            // 가시성 플래그 업데이트 (Index 27: 벽 투시 여부 등)
            byte flags = isSneaking ? (byte) 0 : (byte) 2;
            metadata.add(new WrappedDataValue(27, WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class), flags));

            meta.getDataValueCollectionModifier().write(0, metadata);
            protocolManager.sendServerPacket(viewer, meta);

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
            plugin.getLogger().warning("전체 제거 실패: " + e.getMessage());
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
            plugin.getLogger().warning("개별 제거 실패: " + e.getMessage());
        }
    }
}