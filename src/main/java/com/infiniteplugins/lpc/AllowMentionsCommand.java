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
 * {@code /allowmentions <friends|all|nobody>} — picks who may ping the sender by name.
 *
 * <p>{@code friends} is answered by FriendSystem. When FriendSystem is not installed the
 * choice is still stored, but the player is told that it will not take effect yet.</p>
 */
final class AllowMentionsCommand implements CommandExecutor, TabCompleter {

	private final LPC plugin;

	AllowMentionsCommand(final LPC plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("Only players can choose who may mention them.");
			return true;
		}
		final Player player = (Player) sender;
		final MentionPolicy current = this.plugin.chatPreferences().mentions(player.getUniqueId());

		if (args.length == 0) {
			player.sendMessage(this.plugin.colorize("&7Mentions from &f" + current.key() + " &7are allowed."));
			player.sendMessage(this.plugin.colorize("&7Change it with &f/" + label + " <friends|all|nobody>&7."));
			return true;
		}

		final MentionPolicy policy = MentionPolicy.fromKey(args[0]);
		if (policy == null) {
			player.sendMessage(this.plugin.colorize("&cUnknown option '&f" + args[0]
				+ "&c'. Use &ffriends&c, &fall&c or &fnobody&c."));
			return true;
		}

		this.plugin.chatPreferences().mentions(player.getUniqueId(), policy);
		switch (policy) {
			case ALL:
				player.sendMessage(this.plugin.colorize("&fAnyone &acan now mention you."));
				break;
			case FRIENDS:
				player.sendMessage(this.plugin.colorize("&aOnly &fyour friends &acan now mention you."));
				if (!this.plugin.friendSystem().isAvailable()) {
					player.sendMessage(this.plugin.colorize("&e" + FriendSystemHook.PLUGIN_NAME
						+ " isn't running, so anyone can still mention you until it is installed."));
				}
				break;
			default:
				player.sendMessage(this.plugin.colorize("&aNobody can mention you any more."));
				break;
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
		final List<String> completions = new ArrayList<String>();
		if (args.length == 1) {
			final String input = args[0].toLowerCase(Locale.ROOT);
			for (final MentionPolicy policy : MentionPolicy.values()) {
				if (policy.key().startsWith(input)) {
					completions.add(policy.key());
				}
			}
		}
		return completions;
	}
}
