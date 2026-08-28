package com.infiniteplugins.lpc;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * The per-player chat settings: {@code /showchatfrom} and {@code /allowmentions}.
 *
 * <p>Kept in memory so the chat listeners never touch the disk on the chat thread, and
 * mirrored into {@code chat-settings.yml} on a background thread whenever somebody
 * changes theirs. Players who left both settings alone are not written out, so the file
 * only ever lists players who opted into something.</p>
 */
final class ChatPreferences {

	private static final String FILE_NAME = "chat-settings.yml";
	private static final String SHOW_CHAT_FROM = "show-chat-from";
	private static final String ALLOW_MENTIONS = "allow-mentions";

	/** One player's settings. Fields are volatile: commands write them, chat threads read them. */
	private static final class Prefs {

		private volatile ChatVisibility visibility = ChatVisibility.EVERYONE;
		private volatile MentionPolicy mentions = MentionPolicy.ALL;

		boolean isDefault() {
			return this.visibility == ChatVisibility.EVERYONE && this.mentions == MentionPolicy.ALL;
		}
	}

	private final Plugin plugin;
	private final File file;
	private final Map<UUID, Prefs> players = new ConcurrentHashMap<UUID, Prefs>();

	ChatPreferences(final Plugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), FILE_NAME);
	}

	/** Reads the file into memory. Unreadable or unknown entries are skipped, not fatal. */
	void load() {
		this.players.clear();
		if (!this.file.isFile()) {
			return;
		}
		final FileConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
		for (final String key : yaml.getKeys(false)) {
			final ConfigurationSection entry = yaml.getConfigurationSection(key);
			if (entry == null) {
				continue;
			}
			final UUID uuid;
			try {
				uuid = UUID.fromString(key);
			} catch (final IllegalArgumentException notAUuid) {
				this.plugin.getLogger().warning("Skipping malformed player id '" + key + "' in " + FILE_NAME + '.');
				continue;
			}
			final Prefs prefs = new Prefs();
			final ChatVisibility visibility = ChatVisibility.fromKey(entry.getString(SHOW_CHAT_FROM));
			final MentionPolicy mentions = MentionPolicy.fromKey(entry.getString(ALLOW_MENTIONS));
			if (visibility != null) {
				prefs.visibility = visibility;
			}
			if (mentions != null) {
				prefs.mentions = mentions;
			}
			if (!prefs.isDefault()) {
				this.players.put(uuid, prefs);
			}
		}
	}

	ChatVisibility visibility(final UUID player) {
		final Prefs prefs = this.players.get(player);
		return prefs == null ? ChatVisibility.EVERYONE : prefs.visibility;
	}

	void visibility(final UUID player, final ChatVisibility mode) {
		this.mutable(player).visibility = mode == null ? ChatVisibility.EVERYONE : mode;
		this.saveLater();
	}

	MentionPolicy mentions(final UUID player) {
		final Prefs prefs = this.players.get(player);
		return prefs == null ? MentionPolicy.ALL : prefs.mentions;
	}

	void mentions(final UUID player, final MentionPolicy policy) {
		this.mutable(player).mentions = policy == null ? MentionPolicy.ALL : policy;
		this.saveLater();
	}

	private Prefs mutable(final UUID player) {
		Prefs prefs = this.players.get(player);
		if (prefs == null) {
			final Prefs created = new Prefs();
			prefs = this.players.putIfAbsent(player, created);
			if (prefs == null) {
				prefs = created;
			}
		}
		return prefs;
	}

	/** Writes the file off the server's threads; see {@link Schedulers#async}. */
	private void saveLater() {
		Schedulers.async(this.plugin, this::save);
	}

	/** Rewrites the whole file; synchronized so two queued saves cannot interleave. */
	synchronized void save() {
		final YamlConfiguration yaml = new YamlConfiguration();
		for (final Map.Entry<UUID, Prefs> entry : this.players.entrySet()) {
			final Prefs prefs = entry.getValue();
			if (prefs.isDefault()) {
				continue;
			}
			final String id = entry.getKey().toString();
			yaml.set(id + '.' + SHOW_CHAT_FROM, prefs.visibility.key());
			yaml.set(id + '.' + ALLOW_MENTIONS, prefs.mentions.key());
		}
		final File folder = this.file.getParentFile();
		if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
			this.plugin.getLogger().warning("Could not create " + folder + " to save " + FILE_NAME + '.');
			return;
		}
		try {
			yaml.save(this.file);
		} catch (final IOException failure) {
			this.plugin.getLogger().log(Level.WARNING, "Could not save " + FILE_NAME + '.', failure);
		}
	}
}
