package me.axicavlo.customname;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NicknameCommand implements CommandExecutor {
    private final Customname plugin;

    public NicknameCommand(Customname plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("사용법: /customname [이름]").color(NamedTextColor.RED));
            return true;
        }

        String newName = args[0];

        // 1. 닉네임 저장
        plugin.saveNickname(player, newName);

        // 2. Tab 리스트 변경
        player.playerListName(Component.text(newName));

        // 3. 실제 이름표 숨기기 (혹시 안 되어 있다면)
        plugin.setupHideNameTeam(player);

        // 4. ⭐ 기존 가짜 엔티티 제거하고 새로 생성
        Bukkit.getScheduler().runTask(plugin, () -> {
            PacketManager pm = plugin.getPacketManager();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    pm.removeFakeNameTag(online, player); // 확실하게 먼저 지우기
                }
            }

            // 5틱 정도의 여유를 두어 클라이언트 엔티티 리스트가 정리될 시간을 줍니다.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(player)) {
                        pm.spawnFakeNameTag(online, player, newName);
                    }
                }
            }, 5L);
        });

        player.sendMessage(Component.text("닉네임이 '")
                .color(NamedTextColor.GREEN)
                .append(Component.text(newName).color(NamedTextColor.GOLD))
                .append(Component.text("'으로 변경되었습니다.").color(NamedTextColor.GREEN)));

        return true;
    }
}