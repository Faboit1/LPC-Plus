package com.infiniteplugins.lpc;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The Folia half of {@link Schedulers}.
 *
 * <p>Paper's regionised schedulers are reached reflectively, for two reasons. LPC lists
 * spigot-api ahead of paper-api, so {@code org.bukkit.Server} resolves to Spigot's copy
 * at compile time and does not declare them — an ordering worth keeping, because it stops
 * Paper-only calls slipping into code that has to run on Spigot. And at runtime they
 * genuinely are absent on Spigot, so they have to be optional either way.</p>
 *
 * <p>Nothing here names a Paper type in a signature, so this class is safe to load
 * anywhere; {@link #available()} reports whether the calls can actually be made.</p>
 */
final class FoliaSchedulers {

	private static final Method SERVER_ASYNC_SCHEDULER = method("org.bukkit.Server", "getAsyncScheduler");
	private static final Method ASYNC_RUN_NOW = method(
		"io.papermc.paper.threadedregions.scheduler.AsyncScheduler", "runNow", Plugin.class, Consumer.class);
	private static final Method ENTITY_SCHEDULER = method("org.bukkit.entity.Entity", "getScheduler");
	private static final Method ENTITY_RUN = method(
		"io.papermc.paper.threadedregions.scheduler.EntityScheduler",
		"run", Plugin.class, Consumer.class, Runnable.class);

	private static final boolean AVAILABLE = SERVER_ASYNC_SCHEDULER != null && ASYNC_RUN_NOW != null
		&& ENTITY_SCHEDULER != null && ENTITY_RUN != null;

	private FoliaSchedulers() {
	}

	private static Method method(final String owner, final String name, final Class<?>... parameters) {
		try {
			return Class.forName(owner).getMethod(name, parameters);
		} catch (final ClassNotFoundException | NoSuchMethodException | LinkageError missing) {
			return null;
		}
	}

	/** Whether every scheduler call this class needs was found. */
	static boolean available() {
		return AVAILABLE;
	}

	/** @return {@code false} if the call could not be made, so the caller can fall back */
	static boolean async(final Plugin plugin, final Runnable task) {
		try {
			final Object scheduler = SERVER_ASYNC_SCHEDULER.invoke(plugin.getServer());
			final Consumer<Object> body = handle -> task.run();
			ASYNC_RUN_NOW.invoke(scheduler, plugin, body);
			return true;
		} catch (final ReflectiveOperationException | RuntimeException failed) {
			return false;
		}
	}

	/** @return {@code false} if the call could not be made, so the caller can fall back */
	static boolean forPlayer(final Plugin plugin, final Player player, final Consumer<Player> task) {
		try {
			final Object scheduler = ENTITY_SCHEDULER.invoke(player);
			final Consumer<Object> body = handle -> {
				if (player.isOnline()) {
					task.accept(player);
				}
			};
			// The third argument is the "retired" callback, run if the entity goes away.
			ENTITY_RUN.invoke(scheduler, plugin, body, null);
			return true;
		} catch (final ReflectiveOperationException | RuntimeException failed) {
			return false;
		}
	}
}
