# Minecraft 1.21.11 CustomName Plugin
### by Axicavlo
### [0.0.1 Release Note](https://github.com/JangYongMin/CustomName/releases/tag/0.0.1)


# ⚙️ CustomName 작동 원리 (Technical Mechanism)

본 플러그인은 마인크래프트의 기본 이름표 시스템을 우회하여, **패킷 기반 가짜 엔티티(Packet-based Fake Entity)**와 **프로토콜 변조**를 통해 커스텀 닉네임을 구현합니다.

### 1. 패킷 기반 가짜 엔티티 (Fake Entity via Packets)
* **Text Display 활용**: 실제 서버 데이터베이스에 저장되는 엔티티가 아닌, 클라이언트에게만 보이는 `TEXT_DISPLAY` 엔티티를 패킷으로 생성합니다.
* **ProtocolLib 제어**: `SPAWN_ENTITY` 및 `ENTITY_METADATA` 패킷을 가로채고 수정하여 클라이언트가 가짜 이름표를 렌더링하도록 유도합니다.

### 2. 프로필 정보 및 스킨 동기화 (Profile & Skin Synchronization)
* **PLAYER_INFO 패킷 변조**: 서버가 플레이어 정보를 전송할 때 `ADD_PLAYER` 액션을 가로채서 닉네임 정보를 커스텀 이름으로 교체합니다.
* **스킨 속성 복제**: 닉네임이 바뀌어도 기존 플레이어의 스킨(Texture) 프로퍼티를 그대로 복제하여 적용하므로, 캐릭터 스킨이 사라지지 않고 유지됩니다.

### 3. 엔티티 탑승을 통한 위치 동기화 (Synchronization via Mounting)
* **Mount 시스템**: 가짜 이름표 엔티티를 플레이어 엔티티에 `MOUNT` 시킵니다.
* **물리 엔진 동기화**: 서버가 위치 패킷을 계속 보내지 않아도, 클라이언트의 물리 엔진이 플레이어의 움직임에 맞춰 이름표를 즉각적으로 이동시킵니다.

### 4. 웅크리기 및 가시성 최적화 (Sneak & Visibility Optimization).
* **메타데이터 실시간 업데이트**: 웅크리기 상태에 따라 불투명도(Opacity)와 벽 투시(See-through) 플래그를 실시간으로 변경하여 바닐라와 동일한 시각적 효과를 제공합니다.

### 5. 데이터 관리 및 영속성 (Data Persistence)
* **YAML 스토리지**: 사용자가 설정한 닉네임은 UUID 기반으로 `userdata.yml` 파일에 저장되어 서버 재시작 후에도 유지됩니다.
* **이벤트 핸들링**: 플레이어의 접속(`Join`) 및 퇴장(`Quit`) 시점에 맞춰 가짜 엔티티를 생성하거나 제거하여 리소스를 관리합니다.
