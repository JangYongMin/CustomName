# Minecraft 1.21.11 CustomName Plugin
### by Axicavlo
### [0.0.3 Release Note](https://github.com/JangYongMin/CustomName/releases/tag/0.0.3)


## ⚙️ Mechanism

본 플러그인은 마인크래프트의 기본 이름표 시스템을 우회하여, **패킷 기반 가짜 엔티티(Packet-based Fake Entity)**와 **프로토콜 변조**를 통해 커스텀 닉네임을 구현합니다.

#### 1. 패킷 기반 가짜 엔티티 (Fake Entity via Packets)
* **Text Display 활용**: 실제 서버 데이터베이스에 저장되는 엔티티가 아닌, 클라이언트에게만 보이는 `TEXT_DISPLAY` 엔티티를 패킷으로 생성합니다.
* **ProtocolLib 제어**: `SPAWN_ENTITY` 및 `ENTITY_METADATA` 패킷을 가로채고 수정하여 클라이언트가 가짜 이름표를 렌더링하도록 유도합니다.

#### 2. 프로필 정보 및 탭 리스트 동기화 (Profile & Tab List Synchronization)
* **PlayerInfo 패킷 전방위 변조 (Improved)**: 단순히 플레이어 추가(`ADD_PLAYER`) 시점뿐만 아니라, `UPDATE_DISPLAY_NAME` 등 모든 정보 업데이트 패킷을 가로채 탭 리스트에서도 실시간으로 커스텀 닉네임이 반영되도록 구현했습니다.
* **안정적인 프로필 유지**: 최신 버전(1.21.1+)의 호환성을 위해 프로필 속성을 직접 복제하는 대신, 기존 `GameProfile` 객체를 보존하며 `DisplayName`만 JSON 형태로 교체하여 스킨 유실과 NullPointerException을 원천 방지합니다.

#### 3. 엔티티 탑승을 통한 위치 동기화 (Synchronization via Mounting)
* **Mount 시스템**: 가짜 이름표 엔티티를 플레이어 엔티티에 `MOUNT` 시킵니다.
* **물리 엔진 동기화**: 서버가 위치 패킷을 계속 보내지 않아도, 클라이언트의 물리 엔진이 플레이어의 움직임에 맞춰 이름표를 즉각적으로 이동시킵니다.
* **패킷 전송 지연 도입**: `SPAWN`과 `MOUNT` 패킷 사이에 미세한 틱 지연을 주어, 클라이언트가 엔티티를 인식하기 전에 탑승 패킷이 먼저 도착해 이름표가 공중에 멈추는 현상을 해결했습니다.

#### 4. 웅크리기 및 가시성 최적화 (Sneak & Visibility Optimization)
* **메타데이터 실시간 업데이트**: 웅크리기 상태에 따라 불투명도(Opacity)와 벽 투시(See-through) 플래그를 실시간으로 변경하여 바닐라와 동일한 시각적 효과를 제공합니다.
* **조건부 렌더링**: 닉네임이 설정되지 않은 유저는 가짜 이름표를 생성하지 않고 바닐라 시스템을 유지하도록 로직을 분리하여 서버 리소스를 최적화했습니다.

#### 5. 데이터 관리 및 예외 처리 (Data Management & Stability)
* **YAML 스토리지**: 사용자가 설정한 닉네임은 UUID 기반으로 `userdata.yml` 파일에 저장되어 서버 재시작 후에도 유지됩니다.
* **지연 로딩(Lazy Loading)**: 플레이어의 **부활(Respawn)** 및 **서버 재접속** 시, 주변 엔티티가 완전히 로딩될 때까지 대기(약 20~30틱) 후 패킷을 재전송하여 이름표 증발 문제를 해결했습니다.
## Commands
/customname [닉네임]: 자신의 닉네임을 설정하거나 변경합니다.
(변경된 닉네임은 chat / List / nameTag 에 적용됩니다.)


## ⚠️필수 요구 사항⚠️
* **Paper / Spigot 1.21.1+ 
* **[ProtocolLib 5.4.0](https://github.com/dmulloy2/ProtocolLib/releases/tag/5.4.0) (최신 개발 빌드 권장)**
