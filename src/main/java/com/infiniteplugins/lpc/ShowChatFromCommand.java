package com.infiniteplugins.lpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /showchatfrom <everyone|friends|none>} — picks whose public chat the sender sees.
 *
 * <p>{@code friends} is answered by FriendSystem. When FriendSystem is not installed the
 * mode is still stored, but the player is told that it will not take effect yet.</p>
 */
final class ShowChatFromCommand implements CommandExecutor, TabCompleter {

	private final LPC plugin;

	ShowChatFromCommand(final LPC plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("Only players can choose whose chat they see.");
			return true;
		}
		final Player player = (Player) sender;
		final ChatVisibility current = this.plugin.chatPreferences().visibility(player.getUniqueId());

		if (args.length == 0) {
			player.sendMessage(this.plugin.colorize("&7You are showing chat from &f" + current.key() + "&7."));
			player.sendMessage(this.plugin.colorize("&7Change it with &f/" + label + " <everyone|friends|none>&7."));
			return true;
		}

		final ChatVisibility mode = ChatVisibility.fromKey(args[0]);
		if (mode == null) {
			player.sendMessage(this.plugin.colorize("&cUnknown option '&f" + args[0]
				+ "&c'. Use &feveryone&c, &ffriends&c or &fnone&c."));
			return true;
		}

		this.plugin.chatPreferences().visibility(player.getUniqueId(), mode);
		switch (mode) {
			case EVERYONE:
				player.sendMessage(this.plugin.colorize("&aYou now see public chat from &feveryone&a."));
				break;
			case FRIENDS:
				player.sendMessage(this.plugin.colorize("&aYou now see public chat from &fyour friends&a only."));
				if (!this.plugin.friendSystem().isAvailable()) {
					player.sendMessage(this.plugin.colorize("&e" + FriendSystemHook.PLUGIN_NAME
						+ " isn't running, so all chat stays visible until it is installed."));
				}
				break;
			default:
				player.sendMessage(this.plugin.colorize("&aYou no longer see public chat from &fanyone&a."));
				break;
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
		final List<String> completions = new ArrayList<String>();
		if (args.length == 1) {
			final String input = args[0].toLowerCase(Locale.ROOT);
			for (final ChatVisibility mode : ChatVisibility.values()) {
				if (mode.key().startsWith(input)) {
					completions.add(mode.key());
				}
			}
		}
		return completions;
	}
}
