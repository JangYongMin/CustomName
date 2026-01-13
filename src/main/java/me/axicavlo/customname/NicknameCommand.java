package me.axicavlo.customname;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

public class NicknameCommand implements CommandExecutor {
    private final Customname plugin;

    public NicknameCommand(Customname plugin){
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args){
        if(label.equalsIgnoreCase("customname")){
            if (!(sender instanceof Player player)) {
                sender.sendMessage("플레이어만 사용할수 있는 명령어입니다.");
                return true;
            }

            if(args.length == 0){
                player.sendMessage(Component.text("사용법: /customname [이름]").color(NamedTextColor.RED));
                return true;
            }

            String newName = args[0];

            player.displayName(Component.text(newName));
            player.playerListName(Component.text(newName));

            plugin.saveNickname(player, newName);

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(player)) continue;
                // 해당 플레이어를 다른 사람들에게 다시 '보여줌'으로써 패킷을 강제 재전송
                online.hidePlayer(plugin, player);
                online.showPlayer(plugin, player);
            }

            Component message = Component.text("플레이어 닉네임이 ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(newName).color(NamedTextColor.GOLD))
                    .append(Component.text("으(로) 설정되었습니다.").color(NamedTextColor.GREEN));

            player.sendMessage(message);

            return true;
        }
        return false;
    }
}
