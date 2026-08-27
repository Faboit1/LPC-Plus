package com.infiniteplugins.lpc;

import java.util.Locale;

/**
 * Who is allowed to mention a player, chosen with {@code /allowmentions}.
 *
 * <p>The setting belongs to the player being mentioned: it decides who can make their
 * client ping them by typing their name.</p>
 */
public enum MentionPolicy {

	/** Anyone who can talk to them — the default. */
	ALL("all", "everyone"),
	/** Only players they are friends with in FriendSystem. */
	FRIENDS("friends"),
	/** Nobody; their name never pings them. */
	NOBODY("nobody", "none");

	private final String key;
	private final String[] aliases;

	MentionPolicy(final String key, final String... aliases) {
		this.key = key;
		this.aliases = aliases;
	}

	/** The value as it is typed on the command line and stored in {@code chat-settings.yml}. */
	public String key() {
		return this.key;
	}

	/**
	 * Parses a typed or stored value, returning {@code null} when it names no policy.
	 *
	 * <p>The wording {@code /showchatfrom} uses is accepted too, so a player who types
	 * {@code everyone} or {@code none} out of habit is not told they got it wrong.</p>
	 */
	public static MentionPolicy fromKey(final String key) {
		if (key != null) {
			final String normalised = key.trim().toLowerCase(Locale.ROOT);
			for (final MentionPolicy policy : values()) {
				if (policy.key.equals(normalised)) {
					return policy;
				}
				for (final String alias : policy.aliases) {
					if (alias.equals(normalised)) {
						return policy;
					}
				}
			}
		}
		return null;
	}
}
