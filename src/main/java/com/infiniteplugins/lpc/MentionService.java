package com.infiniteplugins.lpc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Finds the players named in a chat message, highlights their names and pings them.
 *
 * <p>Who a ping actually reaches is decided by the receiver's {@code /allowmentions}
 * setting, which {@link LPC#pingMentions} applies; this class only does the text work
 * and the notification itself.</p>
 *
 * <p>Detection runs over the message with colour codes stripped, so a mention is found
 * however the message happens to be coloured. Highlighting is applied to the coloured
 * message and is therefore best-effort: a name written flush against a colour code is
 * still pinged, it just is not repainted.</p>
 */
final class MentionService {

	/** Permission a player needs for their message to ping anybody. */
	static final String PERMISSION = "lpc.mention";

	/** A Minecraft name, optionally written with a leading {@code @}. */
	private static final Pattern CANDIDATE =
		Pattern.compile("(?<![A-Za-z0-9_])(@?)([A-Za-z0-9_]{3,16})(?![A-Za-z0-9_])");

	private static final String DEFAULT_HIGHLIGHT = "&e";
	private static final String DEFAULT_SOUND = "entity.experience_orb.pickup";
	private static final String DEFAULT_ACTION_BAR = "&e{player} &7mentioned you in chat.";

	private final boolean enabled;
	private final boolean requireAt;
	private final String highlight;
	private final String sound;
	private final float volume;
	private final float pitch;
	private final String actionBar;

	MentionService(final ConfigurationSection section) {
		boolean on = true;
		boolean at = false;
		String colour = DEFAULT_HIGHLIGHT;
		String noise = DEFAULT_SOUND;
		double loudness = 1.0d;
		double tone = 1.2d;
		String bar = DEFAULT_ACTION_BAR;
		if (section != null) {
			on = section.getBoolean("enabled", true);
			at = section.getBoolean("require-at", false);
			colour = section.getString("highlight", DEFAULT_HIGHLIGHT);
			noise = section.getString("sound", DEFAULT_SOUND);
			loudness = section.getDouble("volume", 1.0d);
			tone = section.getDouble("pitch", 1.2d);
			bar = section.getString("action-bar", DEFAULT_ACTION_BAR);
		}
		this.enabled = on;
		this.requireAt = at;
		this.highlight = ChatColor.translateAlternateColorCodes('&', colour == null ? "" : colour);
		this.sound = noise == null ? "" : noise.trim();
		this.volume = (float) loudness;
		this.pitch = (float) tone;
		this.actionBar = ChatColor.translateAlternateColorCodes('&', bar == null ? DEFAULT_ACTION_BAR : bar);
	}

	boolean enabled() {
		return this.enabled;
	}

	/**
	 * The online players named in a message, in the order they first appear.
	 *
	 * <p>The speaker is never included: mentioning yourself does not ping you.</p>
	 */
	List<Player> find(final String message, final Collection<? extends Player> online, final Player speaker) {
		if (!this.enabled || message == null || message.isEmpty()) {
			return Collections.emptyList();
		}
		final String plain = ChatColor.stripColor(message);
		if (plain == null || plain.isEmpty()) {
			return Collections.emptyList();
		}
		final Map<String, Player> byName = new HashMap<String, Player>();
		for (final Player candidate : online) {
			if (!candidate.getUniqueId().equals(speaker.getUniqueId())) {
				byName.put(candidate.getName().toLowerCase(Locale.ROOT), candidate);
			}
		}
		if (byName.isEmpty()) {
			return Collections.emptyList();
		}
		final List<Player> found = new ArrayList<Player>();
		final Matcher matcher = CANDIDATE.matcher(plain);
		while (matcher.find()) {
			if (this.requireAt && matcher.group(1).isEmpty()) {
				continue;
			}
			final Player mentioned = byName.get(matcher.group(2).toLowerCase(Locale.ROOT));
			if (mentioned != null && !found.contains(mentioned)) {
				found.add(mentioned);
			}
		}
		return found;
	}

	/**
	 * Repaints every mentioned name in the message.
	 *
	 * @param colourContext the already-coloured chat format up to {@code {message}}, so the
	 *                      colour in force before the mention can be put back after it
	 */
	String highlight(final String message, final List<Player> mentioned, final String colourContext) {
		if (!this.enabled || this.highlight.isEmpty() || mentioned.isEmpty()) {
			return message;
		}
		String result = message;
		for (final Player player : mentioned) {
			result = this.highlightName(result, player.getName(), colourContext);
		}
		return result;
	}

	private String highlightName(final String message, final String name, final String colourContext) {
		final Matcher matcher = Pattern.compile(
			"(?<![A-Za-z0-9_])@?" + Pattern.quote(name) + "(?![A-Za-z0-9_])",
			Pattern.CASE_INSENSITIVE).matcher(message);
		final StringBuffer out = new StringBuffer(message.length() + 16);
		while (matcher.find()) {
			final String restore = ChatColor.getLastColors(colourContext + message.substring(0, matcher.start()));
			matcher.appendReplacement(out, Matcher.quoteReplacement(
				this.highlight + matcher.group() + (restore.isEmpty() ? ChatColor.RESET.toString() : restore)));
		}
		return matcher.appendTail(out).toString();
	}

	/** Plays the ping. Called on a server thread. */
	void playPing(final Player mentioned) {
		if (this.sound.isEmpty()) {
			return;
		}
		try {
			mentioned.playSound(mentioned.getLocation(), this.sound, this.volume, this.pitch);
		} catch (final IllegalArgumentException unknownSound) {
			// A misconfigured sound name should not cost the player their mention.
		}
	}

	/** The action bar shown to a mentioned player. */
	String actionBarFor(final Player speaker) {
		return this.actionBar.replace("{player}", speaker.getName());
	}
}
