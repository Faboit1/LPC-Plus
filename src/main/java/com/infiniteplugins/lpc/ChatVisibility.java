package com.infiniteplugins.lpc;

import java.util.Locale;

/**
 * Whose public chat a player wants to see, chosen with {@code /showchatfrom}.
 *
 * <p>The setting belongs to the <em>viewer</em>, not to the speaker: it decides which
 * other players' messages reach them, and never hides their own.</p>
 */
public enum ChatVisibility {

	/** Everybody's chat — the default, and what the plugin did before this setting existed. */
	EVERYONE("everyone", "all"),
	/** Only chat from players the viewer is friends with in FriendSystem. */
	FRIENDS("friends"),
	/** No public chat at all. */
	NONE("none", "nobody");

	private final String key;
	private final String[] aliases;

	ChatVisibility(final String key, final String... aliases) {
		this.key = key;
		this.aliases = aliases;
	}

	/** The value as it is typed on the command line and stored in {@code chat-settings.yml}. */
	public String key() {
		return this.key;
	}

	/**
	 * Parses a typed or stored value, returning {@code null} when it names no mode.
	 *
	 * <p>The wording {@code /allowmentions} uses is accepted too, so a player who types
	 * {@code all} or {@code nobody} out of habit is not told they got it wrong.</p>
	 */
	public static ChatVisibility fromKey(final String key) {
		if (key != null) {
			final String normalised = key.trim().toLowerCase(Locale.ROOT);
			for (final ChatVisibility mode : values()) {
				if (mode.key.equals(normalised)) {
					return mode;
				}
				for (final String alias : mode.aliases) {
					if (alias.equals(normalised)) {
						return mode;
					}
				}
			}
		}
		return null;
	}
}
