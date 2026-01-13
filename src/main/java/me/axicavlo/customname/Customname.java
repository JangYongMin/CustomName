package me.axicavlo.customname;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class Customname extends JavaPlugin implements Listener {
    private File userDataFile;
    private FileConfiguration userDataConfig;

    @Override
    public void onEnable() {
        setupUserData();
        getServer().getPluginManager().registerEvents(this,this);

        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib을 찾을 수 없습니다!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("ProtocolLib 발견! 패킷 리스너 등록 중...");

        // 패킷 리스너 등록
        PacketManager packetManager = new PacketManager(this);
        packetManager.registerAllPacketDebugger();

        getCommand("customname").setExecutor(new NicknameCommand(this));

        getLogger().info("-------------------------");
        getLogger().info("");
        getLogger().info("      [CustomName]");
        getLogger().info("  플러그인이 구동되었습니다.");
        getLogger().info("");
        getLogger().info("-------------------------");

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public void setupUserData(){
        if(!getDataFolder().exists()){
            getDataFolder().mkdir();
        }

        userDataFile = new File(getDataFolder(), "userdata.yml");

        if(!userDataFile.exists()){
            try {
                userDataFile.createNewFile();
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        userDataConfig = YamlConfiguration.loadConfiguration(userDataFile);
    }

    public void saveNickname(Player player, String nickname){
        String uuid = player.getUniqueId().toString();
        String accountName = player.getName(); // 실제 마인크래프트 계정 이름

        // 계층형 구조로 저장 (UUID 아래에 실제 이름과 바꾼 닉네임을 각각 저장)
        userDataConfig.set(uuid + ".accountName", accountName);
        userDataConfig.set(uuid + ".nickname", nickname);

        try {
            userDataConfig.save(userDataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNickname(Player player){
        return userDataConfig.getString(player.getUniqueId().toString() + ".nickname");
    }





    @EventHandler
    public void onJoin(PlayerJoinEvent event){
        Player player = event.getPlayer();
        String savedName = getNickname(player);
        String playerName = event.getPlayer().getName();

        if(savedName != null){
            player.displayName(Component.text(savedName));
            player.playerListName(Component.text(savedName));
            playerName = savedName;
        }

        Component message = Component.text("[+]")
                .color(NamedTextColor.GREEN)
                .append(Component.text(playerName).color(NamedTextColor.GOLD))
                .append(Component.text("님이 서버에 접속").color(NamedTextColor.WHITE));

        event.joinMessage(message);
    }

    @EventHandler
    public void  onQuit(PlayerQuitEvent event){
        Player player = event.getPlayer();
        String savedName = getNickname(player);
        String playerName = (savedName != null) ? savedName :event.getPlayer().getName();

        Component message = Component.text("[-]")
                .color(NamedTextColor.RED)
                .append(Component.text(playerName).color(NamedTextColor.GOLD))
                .append(Component.text("님이 서버를 떠남").color(NamedTextColor.WHITE));

        event.quitMessage(message);
    }
}
