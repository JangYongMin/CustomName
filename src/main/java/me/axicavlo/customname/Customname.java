package me.axicavlo.customname;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 플러그인의 메인 클래스입니다.
 */
public final class Customname extends JavaPlugin implements Listener {
    private File userDataFile;
    private FileConfiguration userDataConfig;
    private PacketManager packetManager;

    private final Map<Player, Integer> fakeEntityIds = new HashMap<>();


    @Override
    public void onEnable() {
        // 데이터 저장소 설정 및 이벤트 리스너 등록
        setupUserData();
        getServer().getPluginManager().registerEvents(this, this);

        // ProtocolLib 설치 여부 확인
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib를 찾을 수 없습니다! 플러그인을 비활성화합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 패킷 매니저 초기화 및 리스너 등록
        packetManager = new PacketManager(this);
        packetManager.registerPacketListener();

        // 명령어 등록
        getCommand("customname").setExecutor(new NicknameCommand(this));

        // 플러그인 활성화 시 이미 접속 중인 유저 중 닉네임 정보가 있는 경우 처리
        for (Player player : getServer().getOnlinePlayers()) {
            String nickname = getNickname(player);
            if (nickname != null) {
                // 이미 설정된 닉네임이 있다면 효과 적용
                applyCustomNameEffects(player, nickname);
            }
        }

        // 구동 확인 로그 출력
        var console = Bukkit.getConsoleSender();
        String RST = "\u001B[0m";
        String C1 = "\u001B[1;31m"; // 빨강
        String C2 = "\u001B[33m";   // 주황 (어두운 노랑)
        String C3 = "\u001B[1;33m"; // 노랑 (밝은 노랑)
        String C4 = "\u001B[1;32m"; // 연두 (밝은 초록)
        String C5 = "\u001B[32m";   // 초록
        String C6 = "\u001B[1;36m"; // 하늘
        String C7 = "\u001B[1;34m"; // 파랑
        String C8 = "\u001B[36m";   // 인디고 (어두운 하늘)
        String C9 = "\u001B[1;35m"; // 보라
        String C10 = "\u001B[35m";  // 핑크 (어두운 보라)
        String title = C1 + "C" + C2 + "u" + C3 + "s" + C4 + "t" + C5 + "o" + C6 + "m" + C7 + "N" + C8 + "a" + C9 + "m" + C10 + "e";

        console.sendMessage(C2 + "---------------------------" + RST);
        console.sendMessage(" " + RST);
        console.sendMessage("       [" + RST + title + RST + "]" + RST);
        console.sendMessage("   플러그인이 구동되었습니다." + RST);
        console.sendMessage(" " + RST);
        console.sendMessage(C2 + "---------------------------" + RST);
        console.sendMessage(C7 + "       version 0.0.3" + RST);
        console.sendMessage(C2 + "---------------------------" + RST);
    }

    @Override
    public void onDisable() {
        // 플러그인 비활성화 시 생성된 모든 가짜 엔티티 제거
        if (packetManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                packetManager.removeAllFakeEntities(player);
            }
        }
    }


    /**
     * 플레이어에게 커스텀 닉네임 효과를 적용하거나 해제합니다.
     */
    public void applyCustomNameEffects(Player player, String nickname) {
        if (nickname != null) {
            // 1. 닉네임이 있는 경우: 팀에 추가하여 기본 이름표 숨김
            setupHideNameTeam(player);
            player.playerListName(Component.text(nickname));

            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    packetManager.spawnFakeNameTag(other, player, nickname);
                }
            }
        } else {
            // 2. 닉네임이 없는 경우: 팀에서 제거하여 바닐라 이름표 노출
            removeHideNameTeam(player);
            player.playerListName(Component.text(player.getName()));
        }
    }

    /**
     * 플레이어를 이름표 숨김 팀에서 제거하는 메서드 추가
     */
    public void removeHideNameTeam(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("hide_real_name");
        if (team != null && team.hasEntry(player.getName())) {
            team.removeEntry(player.getName());
        }
    }


    /**
     * 웅크리기(Shift) 이벤트를 감지하여 이름표의 시각적 효과를 업데이트합니다.
     */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player target = event.getPlayer();
        boolean isSneaking = event.isSneaking();

        if (getNickname(target) == null) return;

        // 다른 모든 플레이어에게 해당 플레이어의 이름표 상태 변경 패킷 전송
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) {
                packetManager.updateSneakState(viewer, target, isSneaking);
            }
        }
    }

    /**
     * userdata.yml 파일 및 설정을 초기화합니다.
     */
    public void setupUserData() {
        if (!getDataFolder().exists()) getDataFolder().mkdir();
        userDataFile = new File(getDataFolder(), "userdata.yml");

        if (!userDataFile.exists()) {
            try {
                userDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        userDataConfig = YamlConfiguration.loadConfiguration(userDataFile);
    }

    /**
     * 플레이어의 닉네임을 파일에 저장합니다.
     */
    public void saveNickname(Player player, String nickname) {
        String uuid = player.getUniqueId().toString();
        userDataConfig.set(uuid + ".nickname", nickname);

        try {
            userDataConfig.save(userDataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNickname(Player player) {
        return userDataConfig.getString(player.getUniqueId().toString() + ".nickname");
    }

    public PacketManager getPacketManager() {
        return packetManager;
    }

    public Map<Player, Integer> getFakeEntityIds() {
        return fakeEntityIds;
    }

    /**
     * 플레이어의 기본 마인크래프트 이름표를 숨기기 위해 팀에 등록합니다.
     */
    public void setupHideNameTeam(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("hide_real_name");

        if (team == null) {
            team = sb.registerNewTeam("hide_real_name");
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        team.addEntry(player.getName());
    }

    /**
     * 채팅 시 플레이어 이름 대신 커스텀 닉네임을 표시하도록 렌더링합니다.
     */
    @EventHandler
    public void onChat(AsyncChatEvent event) {
        String nickname = getNickname(event.getPlayer());
        if (nickname != null) {
            event.renderer((source, sourceDisplayName, message, viewer) -> Component.text("<").append(Component.text(nickname)).append(Component.text("> ")).append(message));
        }
    }

    /**
     * 플레이어 접속 시 닉네임 설정 및 가짜 이름표를 생성합니다.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String nickname = getNickname(player);

        // 1. 본인의 입장 메시지 및 탭 리스트 처리
        if (nickname != null) {
            player.playerListName(Component.text(nickname));
            event.joinMessage(Component.text("[+] ").color(NamedTextColor.GREEN).append(Component.text(nickname).color(NamedTextColor.GOLD)));
        } else {
            event.joinMessage(Component.text("[+] ").color(NamedTextColor.GREEN).append(Component.text(player.getName()).color(NamedTextColor.GOLD)));
            removeHideNameTeam(player); // 닉네임 없으면 바닐라 이름표 노출
        }

        // 2.  재접속한 유저에게 타인의 이름표를 보여주는 로직 (지연 시간 증가)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // 본인이 닉네임이 있다면 본인 이름표를 남들에게 보이게 함
            if (nickname != null) {
                applyCustomNameEffects(player, nickname);
            }

            //  본인 화면에 다른 닉네임 유저들의 이름표를 다시 소환
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player) && other.getWorld().equals(player.getWorld())) {
                    String otherNickname = getNickname(other);

                    // 이미 닉네임을 설정해서 '이름표가 숨겨진 유저'만 다시 소환 대상입니다.
                    if (otherNickname != null) {
                        // 확실한 동기화를 위해 기존 패킷 제거 후 재소환
                        packetManager.removeFakeNameTag(player, other);

                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            packetManager.spawnFakeNameTag(player, other, otherNickname);
                        }, 2L);
                    }
                }
            }
        }, 15L); // 지연 시간을 15틱으로 늘려 클라이언트 로딩 대기
    }

    /**
     * 플레이어 퇴장 시 가짜 엔티티를 정리합니다.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        for (Player online : getServer().getOnlinePlayers()) {
            if (!online.equals(player)) {
                packetManager.removeFakeNameTag(online, player);
            }
        }

        fakeEntityIds.remove(player);
        String nickname = getNickname(player);
        String name = (nickname != null) ? nickname : player.getName();

        event.quitMessage(Component.text("[-] ").color(NamedTextColor.RED).append(Component.text(name).color(NamedTextColor.GOLD)));
    }

    /**
     * 플레이어 사망 시:
     * 1. 타인이 보는 내 이름표 제거
     * 2. 내 화면에 보이는 타인의 이름표 제거 (잔상 방지)
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // 1. 다른 사람들에게 보였던 내 가짜 이름표 제거
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(victim)) {
                packetManager.removeFakeNameTag(viewer, victim);
            }
        }

        // 2. 내 화면에 떠 있던 다른 사람들의 가짜 이름표 패킷 제거
        // 이렇게 해야 죽어있는 동안 다른 사람이 이동해도 허공에 이름표가 남지 않습니다.
        packetManager.removeAllFakeEntities(victim);
    }

    /**
     * 플레이어 부활 시:
     * 1. 내 이름표 다시 생성 (타인에게 보임)
     * 2. 다른 사람들의 이름표 다시 생성 (내게 보임)
     */
    @EventHandler
    public void onRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();

        // 부활 후 지연 시간을 15~20틱(약 1초) 정도로 늘려 클라이언트 로딩 시간을 벌어줍니다.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // 1. 내 이름표 갱신 (남들에게 보임)
            String myNickname = getNickname(player);
            if (myNickname != null) {
                applyCustomNameEffects(player, myNickname);
            }

            // 2. 타인의 이름표 갱신 (내 화면에 보임)
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player) && other.getWorld().equals(player.getWorld())) {
                    String otherNickname = getNickname(other);
                    if (otherNickname != null) {
                        // 중요: 타인의 가짜 이름표를 내 화면에서 완전히 지웠다가 다시 소환
                        packetManager.removeFakeNameTag(player, other);

                        // 2틱 뒤에 소환하여 패킷 충돌 방지
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            packetManager.spawnFakeNameTag(player, other, otherNickname);
                        }, 2L);
                    }
                }
            }
        }, 20L); // 1초 지연
    }
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // 1. [제거] 이전 월드의 플레이어들에게서 내 이름표 삭제
        for (Player other : event.getFrom().getPlayers()) {
            packetManager.removeFakeNameTag(other, player);
        }

        // 2. [생성] 지연 로딩 (월드 로딩 대기)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // 새 월드의 모든 플레이어를 순회
            for (Player other : player.getWorld().getPlayers()) {
                if (other.equals(player)) continue;

                // A. 내 이름표를 새 월드 사람들에게 보여줌
                String myNickname = getNickname(player);
                if (myNickname != null) {
                    packetManager.spawnFakeNameTag(other, player, myNickname);
                }

                // B. 새 월드에 있던 사람들의 이름표를 나(player)에게 보여줌
                String otherNickname = getNickname(other);
                if (otherNickname != null) {
                    packetManager.spawnFakeNameTag(player, other, otherNickname);
                }
            }
        }, 30L); // 1.5초 지연
    }
}