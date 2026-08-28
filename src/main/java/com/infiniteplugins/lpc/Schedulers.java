package com.infiniteplugins.lpc;

import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Scheduling that behaves on Spigot, Paper and Folia alike.
 *
 * <p>Folia has no single main thread. {@link org.bukkit.scheduler.BukkitScheduler} is not
 * implemented there and throws, and anything that touches a player has to run on the
 * thread that owns that player. {@link FoliaSchedulers} makes those calls; this class
 * decides when to use them and falls back to the Bukkit scheduler everywhere else.</p>
 */
final class Schedulers {

	/** Folia's own class, absent from Paper and Spigot. */
	private static final boolean FOLIA =
		present("io.papermc.paper.threadedregions.RegionizedServer") && FoliaSchedulers.available();

	private Schedulers() {
	}

	private static boolean present(final String className) {
		try {
			Class.forName(className);
			return true;
		} catch (final ClassNotFoundException | LinkageError absent) {
			return false;
		}
	}

	static boolean folia() {
		return FOLIA;
	}

	/**
	 * Runs blocking work — a file write, say — off the server's threads.
	 *
	 * <p>Falls back to running inline when no scheduler will take the task, which is the
	 * case while the plugin is being disabled.</p>
	 */
	static void async(final Plugin plugin, final Runnable task) {
		if (FOLIA) {
			if (!FoliaSchedulers.async(plugin, task)) {
				task.run();
			}
			return;
		}
		try {
			plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
		} catch (final IllegalArgumentException | IllegalStateException | UnsupportedOperationException disabled) {
			task.run();
		}
	}

	/**
	 * Runs work on the thread that owns a player, skipping it if they log off first.
	 *
	 * <p>On Folia that is the player's own scheduler; elsewhere it is the main thread.
	 * Either way the caller may be the async chat thread, which must not touch players
	 * itself.</p>
	 */
	static void forPlayer(final Plugin plugin, final Player player, final Consumer<Player> task) {
		if (player == null || !player.isOnline()) {
			return;
		}
		if (FOLIA) {
			if (!FoliaSchedulers.forPlayer(plugin, player, task)) {
				task.accept(player);
			}
			return;
		}
		try {
			plugin.getServer().getScheduler().runTask(plugin, () -> {
				if (player.isOnline()) {
					task.accept(player);
				}
			});
		} catch (final IllegalArgumentException | IllegalStateException | UnsupportedOperationException disabled) {
			task.accept(player);
		}
	}
}
