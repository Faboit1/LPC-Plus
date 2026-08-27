package com.infiniteplugins.lpc;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;

/**
 * Optional bridge to FriendSystem's developer API.
 *
 * <p>FriendSystem is not published to a Maven repository, so LPC reaches it through
 * reflection instead of a compile-time dependency: the plugin still builds, loads and
 * runs on servers that do not have FriendSystem installed. Only its documented public
 * API ({@code com.faboit.friendsystem.api.FriendSystemAPI}) is touched, and only the
 * query half of it — FriendSystem states that queries read from memory and are safe to
 * call from any thread, which is what lets the async chat listeners use this.</p>
 *
 * <p>The API instance is looked up on each call rather than cached, as FriendSystem's own
 * documentation recommends, so a reload that swaps the plugin out cannot leave LPC
 * holding a dead object. Only the reflected {@link Method} handles are kept.</p>
 */
final class FriendSystemHook {

	/** The plugin name as it appears in FriendSystem's {@code plugin.yml}. */
	static final String PLUGIN_NAME = "FriendSystem";

	private static final String API_CLASS = "com.faboit.friendsystem.api.FriendSystemAPI";

	private final Plugin plugin;

	private volatile Class<?> apiType;
	private volatile Method areFriendsMethod;
	/** Set once something goes wrong, so a broken hook cannot spam the console. */
	private volatile boolean broken;

	FriendSystemHook(final Plugin plugin) {
		this.plugin = plugin;
	}

	/** Whether FriendSystem is installed, enabled and answering. */
	boolean isAvailable() {
		return this.api() != null;
	}

	/**
	 * Whether the two players are friends.
	 *
	 * @return {@code false} when FriendSystem cannot answer, so callers should check
	 *         {@link #isAvailable()} first if "unknown" needs to differ from "no"
	 */
	boolean areFriends(final UUID a, final UUID b) {
		final Object api = this.api();
		if (api == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(this.areFriendsMethod.invoke(api, a, b));
		} catch (final ReflectiveOperationException | RuntimeException error) {
			this.giveUp("FriendSystem rejected an areFriends(...) call", error);
			return false;
		}
	}

	/** The running API instance, or {@code null} when FriendSystem is absent or unusable. */
	private Object api() {
		if (this.broken || !this.plugin.getServer().getPluginManager().isPluginEnabled(PLUGIN_NAME)) {
			return null;
		}
		try {
			if (this.apiType == null) {
				final Class<?> type = Class.forName(API_CLASS, true, this.getClass().getClassLoader());
				this.areFriendsMethod = type.getMethod("areFriends", UUID.class, UUID.class);
				this.apiType = type;
			}
			return this.plugin.getServer().getServicesManager().load(this.apiType);
		} catch (final ClassNotFoundException | NoSuchMethodException | LinkageError error) {
			this.giveUp("FriendSystem is enabled but its API could not be reached", error);
			return null;
		}
	}

	private void giveUp(final String what, final Throwable error) {
		this.broken = true;
		this.plugin.getLogger().log(Level.WARNING, what
			+ " — '/showchatfrom friends' will keep showing all chat until the server restarts.", error);
	}
}
