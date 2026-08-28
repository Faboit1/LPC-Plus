package com.infiniteplugins.lpc;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.regex.Matcher;

final class PaperChatListener implements Listener {

	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

	private final LPC plugin;

	PaperChatListener(final LPC plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onChat(final AsyncChatEvent event) {
		final Player player = event.getPlayer();

		// The filter looks at what the player actually typed, before any colour code has
		// been turned into a section sign — those are not characters the player sent.
		if (plugin.rejectsMessage(player, PlainTextComponentSerializer.plainText().serialize(event.message()))) {
			event.setCancelled(true);
			return;
		}

		hideFromUninterestedViewers(player, event);

		final String format = plugin.buildFormat(player);
		String processed = plugin.processMessage(player, LEGACY.serialize(event.message()));

		final List<Player> mentioned = plugin.findMentions(player, processed);
		processed = plugin.mentions().highlight(processed, mentioned, LPC.formatPrefix(format));
		plugin.pingMentions(player, mentioned);

		event.message(plugin.emoji().enabled() && player.hasPermission(EmojiService.PERMISSION)
			? renderEmoji(plugin.emoji(), processed)
			: LEGACY.deserialize(processed));
		event.renderer(new ChatRenderer() {
			@Override
			public Component render(final Player source, final Component sourceDisplayName, final Component message, final Audience viewer) {
				return formatMessage(format, message);
			}
		});
	}

	/**
	 * Drops every viewer whose {@code /showchatfrom} setting does not want this speaker.
	 *
	 * <p>Non-player audiences — the console, most notably — are left alone, and so is the
	 * speaker: they always see their own message.</p>
	 */
	private void hideFromUninterestedViewers(final Player speaker, final AsyncChatEvent event) {
		try {
			event.viewers().removeIf(viewer -> viewer instanceof Player
				&& !plugin.canSee(speaker, (Player) viewer));
		} catch (final UnsupportedOperationException immutable) {
			// The viewer set belongs to another plugin — leave it as it is.
		}
	}

	/**
	 * Swaps {@code :shortcode:} tokens for their emoji, giving each one a hover that shows
	 * the shortcode it came from, so players can see how a symbol was typed.
	 *
	 * <p>The message is rebuilt piece by piece rather than string-replaced, because a hover
	 * can only live on a component. Each piece is prefixed with the colour codes in force
	 * where it starts, so splitting the text does not lose the colour running through it.</p>
	 */
	static Component renderEmoji(final EmojiService emoji, final String legacyText) {
		final Matcher matcher = EmojiService.shortcodes(legacyText);
		final StringBuilder seen = new StringBuilder(legacyText.length());
		TextComponent.Builder builder = null;
		String carried = "";
		int cursor = 0;
		while (matcher.find()) {
			final String replacement = emoji.replacement(matcher.group(1));
			if (replacement == null) {
				continue;
			}
			if (builder == null) {
				builder = Component.text();
			}
			final String before = legacyText.substring(cursor, matcher.start());
			builder.append(LEGACY.deserialize(carried + before));
			seen.append(carried).append(before);
			carried = ChatColor.getLastColors(seen.toString());
			builder.append(LEGACY.deserialize(carried + replacement)
				.hoverEvent(HoverEvent.showText(LEGACY.deserialize(emoji.hoverFor(matcher.group())))));
			cursor = matcher.end();
		}
		if (builder == null) {
			return LEGACY.deserialize(legacyText);
		}
		return builder.append(LEGACY.deserialize(carried + legacyText.substring(cursor))).build();
	}

	/**
	 * Joins the format and the message.
	 *
	 * <p>A format carrying MiniMessage tags — which is what a LuckPerms prefix usually
	 * looks like — is rendered by {@link MiniMessageFormat}, so a gradient opened in the
	 * prefix still covers what follows it. Anything else takes the legacy path unchanged,
	 * and so does a format MiniMessage cannot parse.</p>
	 */
	private Component formatMessage(final String format, final Component message) {
		if (MiniMessageFormat.hasTags(format)) {
			final Component rendered = MiniMessageFormat.render(format, message);
			if (rendered != null) {
				return rendered;
			}
		}
		final String[] parts = format.split("\\{message}", -1);
		Component component = LEGACY.deserialize(parts[0]);

		for (int i = 1; i < parts.length; i++) {
			component = component.append(message).append(LEGACY.deserialize(parts[i]));
		}

		return component;
	}
}
