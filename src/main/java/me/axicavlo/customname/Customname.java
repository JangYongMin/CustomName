package me.axicavlo.customname;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
// ⭐ 추가됨: 웅크리기 이벤트 임포트
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class Customname extends JavaPlugin implements Listener {
    private File userDataFile;
    private FileConfiguration userDataConfig;
    private PacketManager packetManager;

    private final Map<Player, Integer> fakeEntityIds = new HashMap<>();

    @Override
    public void onEnable() {
        setupUserData();
        getServer().getPluginManager().registerEvents(this, this);

        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib를 찾을 수 없습니다! 플러그인을 비활성화합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        packetManager = new PacketManager(this);
        packetManager.registerPacketListener();

        getCommand("customname").setExecutor(new NicknameCommand(this));

        getServer().getScheduler().runTaskLater(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                String nickname = getNickname(player);
                if (nickname != null) {
                    setupHideNameTeam(player);
                    player.playerListName(Component.text(nickname));
                    for (Player other : getServer().getOnlinePlayers()) {
                        if (!other.equals(player)) {
                            packetManager.spawnFakeNameTag(other, player, nickname);
                        }
                    }
                }
            }
        }, 20L);

        getLogger().info("-------------------------");
        getLogger().info("");
        getLogger().info("      [CustomName]");
        getLogger().info("  플러그인이 구동되었습니다.");
        getLogger().info("");
        getLogger().info("-------------------------");
    }

    @Override
    public void onDisable() {
        if (packetManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                packetManager.removeAllFakeEntities(player);
            }
        }
    }

    // [기능 추가] 웅크리기(Shift) 감지 -> 이름표 흐릿하게
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player target = event.getPlayer();
        boolean isSneaking = event.isSneaking(); // 웅크리기 시작: true, 해제: false

        // 닉네임이 설정된 플레이어만 처리
        if (getNickname(target) == null) return;

        // 다른 모든 플레이어들에게 변경된 투명도 정보 전송
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) {
                packetManager.updateSneakState(viewer, target, isSneaking);
            }
        }
    }

    public void setupUserData() {
        if (!getDataFolder().exists()) getDataFolder().mkdir();
        userDataFile = new File(getDataFolder(), "userdata.yml");
        if (!userDataFile.exists()) {
            try { userDataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        userDataConfig = YamlConfiguration.loadConfiguration(userDataFile);
    }

    public void saveNickname(Player player, String nickname){
        String uuid = player.getUniqueId().toString();
        userDataConfig.set(uuid + ".nickname", nickname);
        try { userDataConfig.save(userDataFile); } catch (IOException e) { e.printStackTrace(); }
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

    public void setupHideNameTeam(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("hide_real_name");
        if (team == null) {
            team = sb.registerNewTeam("hide_real_name");
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        team.addEntry(player.getName());
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        String nickname = getNickname(event.getPlayer());
        if (nickname != null) {
            event.renderer((source, sourceDisplayName, message, viewer) ->
                    Component.text("<")
                            .append(Component.text(nickname).color(NamedTextColor.GOLD))
                            .append(Component.text("> "))
                            .append(message)
            );
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event){
        Player player = event.getPlayer();
        setupHideNameTeam(player);
        String nickname = getNickname(player);
        if (nickname != null) {
            player.playerListName(Component.text(nickname));
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(player)) {
                        packetManager.spawnFakeNameTag(online, player, nickname);
                    }
                }
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(player)) {
                        String otherNickname = getNickname(other);
                        if (otherNickname != null) {
                            packetManager.spawnFakeNameTag(player, other, otherNickname);
                        }
                    }
                }
            }, 20L);
            event.joinMessage(Component.text("[+] ").color(NamedTextColor.GREEN)
                    .append(Component.text(nickname).color(NamedTextColor.GOLD)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event){
        Player player = event.getPlayer();
        for (Player online : getServer().getOnlinePlayers()) {
            if (!online.equals(player)) {
                packetManager.removeFakeNameTag(online, player);
            }
        }
        fakeEntityIds.remove(player);
        String nickname = getNickname(player);
        String name = (nickname != null) ? nickname : player.getName();
        event.quitMessage(Component.text("[-] ").color(NamedTextColor.RED)
                .append(Component.text(name).color(NamedTextColor.GOLD)));
    }
}