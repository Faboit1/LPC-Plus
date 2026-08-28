package com.infiniteplugins.lpc;

import me.clip.placeholderapi.PlaceholderAPI;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LPC extends JavaPlugin implements Listener {

	private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
	private static final Pattern BUKKIT_HEX_PATTERN = Pattern.compile("&x(&[A-Fa-f0-9]){6}");

	private LuckPerms luckPerms;
	private ChatPreferences chatPreferences;
	private FriendSystemHook friendSystem;
	private EmojiService emoji;
	private CharacterFilter characterFilter;
	private MentionService mentions;


	@Override
	public void onEnable() {
		// Load an instance of 'LuckPerms' using the services manager.
		this.luckPerms = getServer().getServicesManager().load(LuckPerms.class);
		if (this.luckPerms == null) {
			getLogger().severe("LuckPerms not found! LPC requires LuckPerms to function.");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		saveDefaultConfig();

		this.chatPreferences = new ChatPreferences(this);
		this.chatPreferences.load();
		this.friendSystem = new FriendSystemHook(this);
		reloadChatSettings();
		bind("showchatfrom", new ShowChatFromCommand(this));
		bind("allowmentions", new AllowMentionsCommand(this));

		try {
			Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
			getServer().getPluginManager().registerEvents(new PaperChatListener(this), this);
		} catch (ClassNotFoundException ignored) {
			getServer().getPluginManager().registerEvents(this, this);
		}

		if (this.friendSystem.isAvailable()) {
			getLogger().info("Hooked into " + FriendSystemHook.PLUGIN_NAME
				+ " — the 'friends' option of /showchatfrom and /allowmentions is active.");
		}

		final String[] chatPlugins = {"EssentialsChat", "VentureChat", "HeroChat", "DeluxeChat", "ChatManager", "ChatEx", "UltraChat", "TownyChat"};
		for (final String pluginName : chatPlugins) {
			if (getServer().getPluginManager().isPluginEnabled(pluginName)) {
				getLogger().warning("Detected " + pluginName + " which may also format chat. To avoid message duplication, disable chat formatting in " + pluginName + ".");
			}
		}
	}

	@Override
	public void onDisable() {
		if (this.chatPreferences != null) {
			this.chatPreferences.save();
		}
	}

	/** Attaches one of the per-player chat setting commands, if plugin.yml declares it. */
	private <T extends CommandExecutor & TabCompleter> void bind(final String name, final T executor) {
		final PluginCommand command = getCommand(name);
		if (command == null) {
			getLogger().warning("Command /" + name + " is missing from plugin.yml.");
			return;
		}
		command.setExecutor(executor);
		command.setTabCompleter(executor);
	}

	/**
	 * Rebuilds the config-driven chat helpers. Called on enable and on {@code /lpc reload}.
	 *
	 * <p>{@code copyDefaults} makes anything missing from the server's own config.yml fall
	 * back to the copy inside the jar. Without it a server that upgraded the plugin but
	 * kept its existing config.yml would silently get no emoji at all, because the file
	 * saved before this release has no {@code emoji} section and {@code saveDefaultConfig}
	 * will not overwrite it. Settings the admin did set still win, and the character
	 * filter stays off unless they turn it on.</p>
	 */
	private void reloadChatSettings() {
		getConfig().options().copyDefaults(true);
		this.emoji = new EmojiService(getConfig().getConfigurationSection("emoji"));
		this.characterFilter = new CharacterFilter(getConfig().getConfigurationSection("chat-filter"), getLogger());
		this.mentions = new MentionService(getConfig().getConfigurationSection("mentions"));
	}

	@Override
	public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
		if (args.length == 1 && "reload".equals(args[0]) && sender.hasPermission("lpc.reload")) {
			reloadConfig();
			reloadChatSettings();
			sender.sendMessage(colorize("&aLPC has been reloaded."));
			return true;
		}

		if (args.length == 1 && "clear".equals(args[0]) && sender.hasPermission("lpc.clearchat")) {
			for (final Player player : getServer().getOnlinePlayers()) {
				for (int i = 0; i < 100; i++) {
					player.sendMessage("");
				}
			}
			final String clearMessage = getConfig().getString("clear-chat-message", "&7Chat has been cleared by a staff member.");
			getServer().broadcastMessage(colorize(clearMessage));
			return true;
		}

		if (args.length == 2 && "debug".equals(args[0]) && sender.hasPermission("lpc.debug")) {
			final Player target = getServer().getPlayer(args[1]);
			if (target == null) {
				sender.sendMessage(colorize("&cPlayer not found."));
				return true;
			}
			final CachedMetaData debugMeta = luckPerms.getPlayerAdapter(Player.class).getMetaData(target);
			sender.sendMessage(colorize("&6&lLPC Debug: &f" + target.getName()));
			sender.sendMessage(colorize("&7Primary Group: &f" + debugMeta.getPrimaryGroup()));
			sender.sendMessage(colorize("&7Prefix: &f" + (debugMeta.getPrefix() != null ? debugMeta.getPrefix() : "&cnone")));
			sender.sendMessage(colorize("&7Suffix: &f" + (debugMeta.getSuffix() != null ? debugMeta.getSuffix() : "&cnone")));
			sender.sendMessage(colorize("&7All Prefixes (by weight):"));
			debugMeta.getPrefixes().forEach((weight, prefix) ->
					sender.sendMessage(colorize("  &7[" + weight + "] &f" + prefix)));
			sender.sendMessage(colorize("&7All Suffixes (by weight):"));
			debugMeta.getSuffixes().forEach((weight, suffix) ->
					sender.sendMessage(colorize("  &7[" + weight + "] &f" + suffix)));
			final String usernameColor = debugMeta.getMetaValue("username-color");
			final String messageColor = debugMeta.getMetaValue("message-color");
			sender.sendMessage(colorize("&7Username-color: &f" + (usernameColor != null ? usernameColor : "&cnone")));
			sender.sendMessage(colorize("&7Message-color: &f" + (messageColor != null ? messageColor : "&cnone")));
			sender.sendMessage(colorize("&7Group format: &f" + (getConfig().getString("group-formats." + debugMeta.getPrimaryGroup()) != null ? "group-formats." + debugMeta.getPrimaryGroup() : "chat-format (default)")));
			sender.sendMessage(colorize("&7PAPI: &f" + (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI") ? "&ahooked" : "&cnot found")));
			sender.sendMessage(colorize("&7Has lpc.colorcodes: &f" + target.hasPermission("lpc.colorcodes")));
			sender.sendMessage(colorize("&7Has lpc.rgbcodes: &f" + target.hasPermission("lpc.rgbcodes")));
			return true;
		}

		return false;
	}

	@Override
	public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
		if (args.length == 1) {
			final String input = args[0].toLowerCase();
			final List<String> completions = new ArrayList<>();
			if (sender.hasPermission("lpc.reload") && "reload".startsWith(input)) completions.add("reload");
			if (sender.hasPermission("lpc.clearchat") && "clear".startsWith(input)) completions.add("clear");
			if (sender.hasPermission("lpc.debug") && "debug".startsWith(input)) completions.add("debug");
			return completions;
		}
		if (args.length == 2 && "debug".equals(args[0]) && sender.hasPermission("lpc.debug")) {
			return getServer().getOnlinePlayers().stream()
					.map(Player::getName)
					.filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
					.collect(Collectors.toList());
		}
		return new ArrayList<>();
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onChat(final AsyncPlayerChatEvent event) {
		final String message = event.getMessage();
		final Player player = event.getPlayer();

		if (rejectsMessage(player, message)) {
			event.setCancelled(true);
			return;
		}

		hideFromUninterestedViewers(player, event.getRecipients());

		final String format = buildFormat(player);
		String processedMessage = processMessage(player, message);

		final List<Player> mentioned = findMentions(player, processedMessage);
		processedMessage = this.mentions.highlight(processedMessage, mentioned, formatPrefix(format));
		processedMessage = applyEmoji(player, processedMessage);
		pingMentions(player, mentioned);

		event.setFormat(format.replace("{message}", processedMessage).replace("%", "%%"));
	}

	String buildFormat(final Player player) {
		final CachedMetaData metaData = this.luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
		final String group = metaData.getPrimaryGroup();

		String format = getConfig().getString(getConfig().getString("group-formats." + group) != null ? "group-formats." + group : "chat-format");
		if (format == null) {
			format = "{prefix}{name}&r: {message}";
		}

		final String prefix = metaData.getPrefix();
		final String suffix = metaData.getSuffix();
		final String usernameColor = metaData.getMetaValue("username-color");
		final String messageColor = metaData.getMetaValue("message-color");

		format = format
				.replace("{prefix}", prefix != null ? prefix : "")
				.replace("{suffix}", suffix != null ? suffix : "")
				.replace("{prefixes}", metaData.getPrefixes().keySet().stream().map(key -> metaData.getPrefixes().get(key)).collect(Collectors.joining()))
				.replace("{suffixes}", metaData.getSuffixes().keySet().stream().map(key -> metaData.getSuffixes().get(key)).collect(Collectors.joining()))
				.replace("{world}", player.getWorld().getName())
				.replace("{name}", player.getName())
				.replace("{displayname}", player.getDisplayName())
				.replace("{username-color}", usernameColor != null ? usernameColor : "")
				.replace("{message-color}", messageColor != null ? messageColor : "");

		format = translateHexColorCodes(format);
		if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			format = PlaceholderAPI.setPlaceholders(player, format);
		}
		format = colorize(translateHexColorCodes(format));

		return format;
	}

	String processMessage(final Player player, final String message) {
		if (player.hasPermission("lpc.colorcodes") && player.hasPermission("lpc.rgbcodes")) {
			return colorize(translateHexColorCodes(message));
		} else if (player.hasPermission("lpc.colorcodes")) {
			return colorize(stripHexCodes(message));
		} else if (player.hasPermission("lpc.rgbcodes")) {
			return stripColorCodes(translateHexColorCodes(message));
		} else {
			return stripColorCodes(stripHexCodes(message));
		}
	}

	String colorize(final String message) {
		return ChatColor.translateAlternateColorCodes('&', message);
	}

	String translateHexColorCodes(final String message) {
		final char colorChar = ChatColor.COLOR_CHAR;

		// Handle &#rrggbb format
		Matcher matcher = HEX_PATTERN.matcher(message);
		StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);
		while (matcher.find()) {
			final String group = matcher.group(1);
			matcher.appendReplacement(buffer, colorChar + "x"
					+ colorChar + group.charAt(0) + colorChar + group.charAt(1)
					+ colorChar + group.charAt(2) + colorChar + group.charAt(3)
					+ colorChar + group.charAt(4) + colorChar + group.charAt(5));
		}
		String result = matcher.appendTail(buffer).toString();

		// Handle &x&r&r&g&g&b&b format (Bukkit-style)
		matcher = BUKKIT_HEX_PATTERN.matcher(result);
		buffer = new StringBuffer(result.length());
		while (matcher.find()) {
			matcher.appendReplacement(buffer, matcher.group().replace('&', colorChar));
		}
		return matcher.appendTail(buffer).toString();
	}

	String stripColorCodes(final String message) {
		return message.replaceAll("&[0-9a-fA-Fk-oK-OrR]", "");
	}

	String stripHexCodes(final String message) {
		String result = message.replaceAll("&#[0-9a-fA-F]{6}", "");
		result = result.replaceAll("&x(&[0-9a-fA-F]){6}", "");
		return result;
	}

	ChatPreferences chatPreferences() {
		return this.chatPreferences;
	}

	MentionService mentions() {
		return this.mentions;
	}

	FriendSystemHook friendSystem() {
		return this.friendSystem;
	}

	EmojiService emoji() {
		return this.emoji;
	}

	/** Replaces {@code :shortcode:} tokens with plain emoji, for players allowed to use them. */
	String applyEmoji(final Player player, final String message) {
		return player.hasPermission(EmojiService.PERMISSION) ? this.emoji.applyPlain(message) : message;
	}

	/**
	 * Whether {@code viewer} wants to see {@code speaker}'s public chat, per their
	 * {@code /showchatfrom} setting. A player always sees their own messages.
	 */
	boolean canSee(final Player speaker, final Player viewer) {
		final UUID watching = viewer.getUniqueId();
		if (watching.equals(speaker.getUniqueId())) {
			return true;
		}
		final ChatVisibility mode = this.chatPreferences.visibility(watching);
		if (mode == ChatVisibility.EVERYONE) {
			return true;
		}
		if (mode == ChatVisibility.NONE) {
			return false;
		}
		// Friends-only, but nothing can answer "are these two friends?" — show the message
		// rather than silently cutting the player off from chat entirely.
		final Boolean friends = this.friendSystem.friendship(watching, speaker.getUniqueId());
		return friends == null || friends;
	}

	/**
	 * Drops every recipient whose {@code /showchatfrom} setting does not want this speaker.
	 *
	 * <p>Another plugin may hand out an unmodifiable recipient set; if it does, the message
	 * stays visible to everyone rather than the chat event blowing up.</p>
	 */
	void hideFromUninterestedViewers(final Player speaker, final Set<Player> recipients) {
		try {
			final Iterator<Player> iterator = recipients.iterator();
			while (iterator.hasNext()) {
				if (!canSee(speaker, iterator.next())) {
					iterator.remove();
				}
			}
		} catch (final UnsupportedOperationException immutable) {
			// The recipient list belongs to another plugin — leave it as it is.
		}
	}

	/**
	 * Applies the character filter, warning the player in chat and on the action bar when
	 * their message contains something the server does not allow.
	 *
	 * @param message the message as the player typed it, before any colour translation
	 * @return {@code true} when the caller must cancel the chat event
	 */
	boolean rejectsMessage(final Player player, final String message) {
		if (!this.characterFilter.enabled() || player.hasPermission(CharacterFilter.BYPASS_PERMISSION)) {
			return false;
		}
		final int rejected = this.characterFilter.firstRejected(message);
		if (rejected < 0) {
			return false;
		}
		final String warning = this.characterFilter.warningFor(rejected);
		Schedulers.forPlayer(this, player, target -> {
			target.sendMessage(warning);
			sendActionBar(target, warning);
		});
		return true;
	}

	/**
	 * The players named in a message who are willing to be pinged by this speaker.
	 *
	 * <p>Only these names are highlighted, so the highlight always matches the ping: a
	 * player who turned mentions off is neither pinged nor repainted.</p>
	 */
	List<Player> findMentions(final Player speaker, final String message) {
		if (!this.mentions.enabled() || !speaker.hasPermission(MentionService.PERMISSION)) {
			return Collections.emptyList();
		}
		final List<Player> named = this.mentions.find(message, getServer().getOnlinePlayers(), speaker);
		if (named.isEmpty()) {
			return named;
		}
		final List<Player> allowed = new ArrayList<Player>(named.size());
		for (final Player mentioned : named) {
			if (allowsMentionFrom(mentioned, speaker) && canSee(speaker, mentioned)) {
				allowed.add(mentioned);
			}
		}
		return allowed;
	}

	/** Whether {@code mentioned}'s {@code /allowmentions} setting lets {@code speaker} ping them. */
	private boolean allowsMentionFrom(final Player mentioned, final Player speaker) {
		final MentionPolicy policy = this.chatPreferences.mentions(mentioned.getUniqueId());
		if (policy == MentionPolicy.ALL) {
			return true;
		}
		if (policy == MentionPolicy.NOBODY) {
			return false;
		}
		// Friends-only, with the same fallback as /showchatfrom: when nothing can answer
		// "are these two friends?", let the mention through rather than dropping it.
		final Boolean friends = this.friendSystem.friendship(mentioned.getUniqueId(), speaker.getUniqueId());
		return friends == null || friends;
	}

	/**
	 * Plays the ping for every player a message mentioned.
	 *
	 * <p>Chat events are asynchronous, so the notification is handed back to a server
	 * thread before it touches anybody.</p>
	 */
	void pingMentions(final Player speaker, final List<Player> mentioned) {
		if (mentioned.isEmpty()) {
			return;
		}
		final MentionService service = this.mentions;
		final String actionBar = service.actionBarFor(speaker);
		for (final Player player : mentioned) {
			Schedulers.forPlayer(this, player, target -> {
				service.playPing(target);
				sendActionBar(target, actionBar);
			});
		}
	}

	/** The part of a chat format before {@code {message}}, which sets the colour it starts in. */
	static String formatPrefix(final String format) {
		final int index = format.indexOf("{message}");
		return index < 0 ? format : format.substring(0, index);
	}

	/** Action bars go through the Spigot API, which plain Spigot and Paper both implement. */
	private static void sendActionBar(final Player player, final String legacyText) {
		player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(legacyText));
	}
}
