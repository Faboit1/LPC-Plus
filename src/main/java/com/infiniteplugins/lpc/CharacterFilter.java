package com.infiniteplugins.lpc;

import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

/**
 * An optional whitelist of the characters a chat message may contain.
 *
 * <p>Servers usually want this to stop players pasting symbols the vanilla font cannot
 * draw — box-drawing characters, combining marks stacked into "zalgo" text, right-to-left
 * overrides and so on. Rather than trying to enumerate those, the filter is expressed the
 * other way round: {@code allowed-characters} is a regular-expression character class
 * describing what <em>is</em> allowed, and anything outside it is refused. The default is
 * printable ASCII, which is exactly the range the default Minecraft font renders.</p>
 */
final class CharacterFilter {

	/** Permission that exempts a player from the filter entirely. */
	static final String BYPASS_PERMISSION = "lpc.bypass.characters";

	/** Space through tilde: printable ASCII, and nothing else. */
	private static final String DEFAULT_ALLOWED = "[\\x20-\\x7E]";
	private static final String DEFAULT_WARNING =
		"&cYour message was not sent: &7'&f{character}&7' &cis not allowed in chat here.";

	private final boolean enabled;
	private final Pattern allowed;
	private final String warning;

	CharacterFilter(final ConfigurationSection section, final Logger logger) {
		boolean on = false;
		String expression = DEFAULT_ALLOWED;
		String message = DEFAULT_WARNING;
		if (section != null) {
			on = section.getBoolean("block-disallowed-characters", false);
			expression = section.getString("allowed-characters", DEFAULT_ALLOWED);
			message = section.getString("warning", DEFAULT_WARNING);
		}
		Pattern compiled;
		try {
			compiled = Pattern.compile(expression == null ? DEFAULT_ALLOWED : expression);
		} catch (final PatternSyntaxException invalid) {
			logger.warning("chat-filter.allowed-characters is not a valid regular expression ("
				+ invalid.getDescription() + ") — falling back to printable ASCII.");
			compiled = Pattern.compile(DEFAULT_ALLOWED);
		}
		this.enabled = on;
		this.allowed = compiled;
		this.warning = ChatColor.translateAlternateColorCodes('&', message == null ? DEFAULT_WARNING : message);
	}

	boolean enabled() {
		return this.enabled;
	}

	/**
	 * The first code point of the message that the whitelist refuses.
	 *
	 * @return the offending code point, or {@code -1} when the whole message is allowed
	 */
	int firstRejected(final String message) {
		if (!this.enabled || message == null) {
			return -1;
		}
		final int length = message.length();
		int index = 0;
		while (index < length) {
			final int codePoint = message.codePointAt(index);
			if (!this.allowed.matcher(new String(Character.toChars(codePoint))).matches()) {
				return codePoint;
			}
			index += Character.charCount(codePoint);
		}
		return -1;
	}

	/** The warning to show a player, with {@code {character}} filled in. */
	String warningFor(final int codePoint) {
		return this.warning.replace("{character}", new String(Character.toChars(codePoint)));
	}
}
