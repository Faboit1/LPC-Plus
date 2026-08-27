package com.infiniteplugins.lpc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Turns {@code :shortcode:} tokens in a chat message into the characters configured
 * for them, so typing {@code :heart:} sends a heart.
 *
 * <p>Shortcodes are deliberately restricted to ASCII, which means they survive
 * {@link CharacterFilter} untouched: a player can type {@code :heart:} on a server that
 * blocks non-ASCII input, and the emoji is substituted afterwards by the plugin.</p>
 */
final class EmojiService {

	/** Permission a player needs for their shortcodes to be replaced. */
	static final String PERMISSION = "lpc.emoji";

	private static final Pattern SHORTCODE = Pattern.compile(":([A-Za-z0-9_+-]{1,32}):");
	private static final String DEFAULT_HOVER = "&7{code}";

	private final boolean enabled;
	private final String hoverFormat;
	private final Map<String, String> emojis;

	EmojiService(final ConfigurationSection section) {
		final Map<String, String> found = new LinkedHashMap<String, String>();
		boolean on = false;
		String hover = DEFAULT_HOVER;
		if (section != null) {
			on = section.getBoolean("enabled", true);
			hover = section.getString("hover", DEFAULT_HOVER);
			final ConfigurationSection list = section.getConfigurationSection("list");
			if (list != null) {
				for (final String name : list.getKeys(false)) {
					final String replacement = list.getString(name);
					if (replacement != null && !replacement.isEmpty()) {
						found.put(name.toLowerCase(Locale.ROOT), replacement);
					}
				}
			}
		}
		this.enabled = on;
		this.hoverFormat = ChatColor.translateAlternateColorCodes('&', hover == null ? DEFAULT_HOVER : hover);
		this.emojis = Collections.unmodifiableMap(found);
	}

	/** Whether there is anything to substitute at all. */
	boolean enabled() {
		return this.enabled && !this.emojis.isEmpty();
	}

	int size() {
		return this.emojis.size();
	}

	/** A fresh matcher for the shortcodes in a message. */
	static Matcher shortcodes(final String message) {
		return SHORTCODE.matcher(message);
	}

	/** The emoji configured for a shortcode name, or {@code null} when there is none. */
	String replacement(final String name) {
		return this.emojis.get(name.toLowerCase(Locale.ROOT));
	}

	/**
	 * The hover text for a shortcode, already translated to legacy colour codes.
	 *
	 * @param shortcode the whole token including its colons, e.g. {@code :heart:}
	 */
	String hoverFor(final String shortcode) {
		return this.hoverFormat.replace("{code}", shortcode);
	}

	/**
	 * Replaces every known shortcode with its emoji, as plain text.
	 *
	 * <p>Used on servers without Paper's chat events, where the message is a string and
	 * there is nothing to attach a hover to.</p>
	 */
	String applyPlain(final String message) {
		if (!this.enabled() || message == null || message.indexOf(':') < 0) {
			return message;
		}
		final Matcher matcher = SHORTCODE.matcher(message);
		final StringBuffer out = new StringBuffer(message.length());
		while (matcher.find()) {
			final String replacement = this.replacement(matcher.group(1));
			matcher.appendReplacement(out, Matcher.quoteReplacement(
				replacement == null ? matcher.group() : replacement));
		}
		return matcher.appendTail(out).toString();
	}
}
