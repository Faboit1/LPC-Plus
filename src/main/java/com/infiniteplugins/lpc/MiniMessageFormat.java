package com.infiniteplugins.lpc;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.ChatColor;

/**
 * Renders a chat format that mixes legacy colour codes with MiniMessage tags.
 *
 * <p>LPC's own formats are written with {@code &} codes, but a prefix or suffix coming
 * from LuckPerms is very often MiniMessage — {@code <gradient>}, {@code <shadow>},
 * {@code <b>} and friends. Deserialising the whole thing as legacy text, which is all the
 * plugin used to do, prints those tags to chat verbatim. This class parses both.</p>
 *
 * <p>The legacy codes are rewritten as the MiniMessage tags that mean the same thing and
 * the result is parsed in one pass, so a gradient opened in the prefix still applies to
 * everything after it. {@code {message}} becomes a component placeholder rather than a
 * string splice, which keeps the player's message inside whatever styling surrounds it —
 * and, because it is inserted as an already-built component, MiniMessage never re-parses
 * it. A player typing {@code <click:run_command:/op me>} sends those characters, not a
 * clickable button.</p>
 *
 * <p>Adventure's MiniMessage ships with Paper and not with Spigot, so this class is only
 * ever reached from {@link PaperChatListener}.</p>
 */
final class MiniMessageFormat {

	/** Tag name standing in for {@code {message}}; deliberately unlikely to collide. */
	private static final String MESSAGE_TAG = "lpc_message";

	private static final MiniMessage MINI = MiniMessage.miniMessage();
	private static final Map<Character, String> LEGACY_TAGS = legacyTags();

	private MiniMessageFormat() {
	}

	private static Map<Character, String> legacyTags() {
		final Map<Character, String> tags = new HashMap<Character, String>();
		tags.put('0', "<black>");
		tags.put('1', "<dark_blue>");
		tags.put('2', "<dark_green>");
		tags.put('3', "<dark_aqua>");
		tags.put('4', "<dark_red>");
		tags.put('5', "<dark_purple>");
		tags.put('6', "<gold>");
		tags.put('7', "<gray>");
		tags.put('8', "<dark_gray>");
		tags.put('9', "<blue>");
		tags.put('a', "<green>");
		tags.put('b', "<aqua>");
		tags.put('c', "<red>");
		tags.put('d', "<light_purple>");
		tags.put('e', "<yellow>");
		tags.put('f', "<white>");
		tags.put('k', "<obf>");
		tags.put('l', "<b>");
		tags.put('m', "<st>");
		tags.put('n', "<u>");
		tags.put('o', "<i>");
		tags.put('r', "<reset>");
		return tags;
	}

	/**
	 * Whether a format is worth sending through MiniMessage at all.
	 *
	 * <p>A format with no {@code <} in it cannot contain a tag, so it takes the legacy
	 * path it always took and behaves exactly as before.</p>
	 */
	static boolean hasTags(final String format) {
		return format != null && format.indexOf('<') >= 0;
	}

	/**
	 * Builds the chat line, substituting {@code message} for every {@code {message}}.
	 *
	 * @return {@code null} if the format could not be parsed, so the caller can fall back
	 *         to the legacy rendering rather than dropping the line
	 */
	static Component render(final String legacyFormat, final Component message) {
		try {
			final String mini = toMiniMessage(legacyFormat).replace("{message}", '<' + MESSAGE_TAG + '>');
			return MINI.deserialize(mini, Placeholder.component(MESSAGE_TAG, message));
		} catch (final RuntimeException unparseable) {
			return null;
		}
	}

	/**
	 * Rewrites legacy section codes as the MiniMessage tags that mean the same thing.
	 *
	 * <p>The tags are left open, exactly as a legacy code applies until something changes
	 * it, so {@code §aHello} and {@code <green>Hello} colour the same run of text.</p>
	 */
	static String toMiniMessage(final String legacy) {
		final StringBuilder out = new StringBuilder(legacy.length() + 32);
		int index = 0;
		while (index < legacy.length()) {
			final char current = legacy.charAt(index);
			if (current != ChatColor.COLOR_CHAR || index + 1 >= legacy.length()) {
				out.append(current);
				index++;
				continue;
			}
			final char code = Character.toLowerCase(legacy.charAt(index + 1));
			final String hex = code == 'x' ? readHex(legacy, index) : null;
			if (hex != null) {
				out.append("<#").append(hex).append('>');
				index += 14;
				continue;
			}
			final String tag = LEGACY_TAGS.get(code);
			if (tag == null) {
				out.append(current);
				index++;
				continue;
			}
			out.append(tag);
			index += 2;
		}
		return out.toString();
	}

	/** Reads Bukkit's {@code §x§r§r§g§g§b§b} spelling of a hex colour, or {@code null}. */
	private static String readHex(final String legacy, final int start) {
		if (start + 13 >= legacy.length()) {
			return null;
		}
		final StringBuilder hex = new StringBuilder(6);
		for (int pair = 0; pair < 6; pair++) {
			final int at = start + 2 + pair * 2;
			if (legacy.charAt(at) != ChatColor.COLOR_CHAR) {
				return null;
			}
			final char digit = legacy.charAt(at + 1);
			if (Character.digit(digit, 16) < 0) {
				return null;
			}
			hex.append(digit);
		}
		return hex.toString();
	}
}
