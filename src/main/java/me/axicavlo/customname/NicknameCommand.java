package me.axicavlo.customname;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


// /customname 명령어를 처리하는 클래스입니다.

public class NicknameCommand implements CommandExecutor {
    private final Customname plugin;

    public NicknameCommand(Customname plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 명령어를 실행한 주체가 플레이어인지 확인
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        // 인자(닉네임)가 입력되었는지 확인
        if (args.length == 0) {
            player.sendMessage(Component.text("사용법: /customname [이름]").color(NamedTextColor.RED));
            return true;
        }

        String newName = args[0];

        // 1. 데이터 파일(userdata.yml)에 닉네임 저장
        plugin.saveNickname(player, newName);

        // 2. 탭(Tab) 리스트에 표시되는 이름 변경
        player.playerListName(Component.text(newName));

        // 3. 마인크래프트 기본 이름표 숨기기 설정
        plugin.setupHideNameTeam(player);

        // 4. 패킷을 이용한 가짜 이름표 갱신
        Bukkit.getScheduler().runTask(plugin, () -> {
            PacketManager pm = plugin.getPacketManager();

            // 기존에 생성된 모든 가짜 이름표 제거
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    pm.removeFakeNameTag(online, player);
                }
            }

            // 클라이언트 엔티티 리스트가 정리될 시간을 준 뒤 새로운 이름표 생성 (5틱 지연)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(player)) {
                        pm.spawnFakeNameTag(online, player, newName);
                    }
                }
            }, 5L);
        });

        // 결과 메시지 전송
        player.sendMessage(Component.text("닉네임이 '")
                .color(NamedTextColor.GREEN)
                .append(Component.text(newName).color(NamedTextColor.GOLD))
                .append(Component.text("'으로 변경되었습니다.").color(NamedTextColor.GREEN)));

        return true;
    }
}