package dev.roocky.emitreetabs.api;

/**
 * The only class another mod should touch.
 *
 * <p>Tree Tabs reaches into EMI's internals through mixins, which is the single largest risk this
 * project carries. The whole reason this package exists is so that nothing else has to: a consumer
 * talks to these interfaces and Tree Tabs keeps the internals risk to itself.
 *
 * <h2>Using it without a hard dependency</h2>
 *
 * The failure mode to design against is {@code NoSuchMethodError} on a user's machine — a crash in
 * a build that compiled cleanly, because the installed Tree Tabs is older than the one the consumer
 * was built against. So the entry point is <em>one</em> method whose signature is frozen forever,
 * and everything else hangs off an interface this mod implements:
 *
 * <pre>{@code
 * try {
 *     TreeTabsRegistry registry = TreeTabsApi.registry(TreeTabsApi.VERSION);
 *     if (registry != null) {
 *         registry.registerStockSource("quartermaster", new ChestStock());
 *     }
 * } catch (Throwable ignored) {
 *     // Tree Tabs is not installed, or is too old. Carry on without it.
 * }
 * }</pre>
 *
 * <p>{@code Throwable}, not {@code Exception}: a missing class is a {@link NoClassDefFoundError} and
 * a missing method a {@link NoSuchMethodError}, and both are {@code Error}s.
 *
 * <p>{@link #VERSION} is a compile-time constant, so a consumer that inlines it and passes it back
 * is stating the version it was <em>built</em> against — which is exactly the question being asked.
 *
 * <h2>What the version means</h2>
 *
 * Adding a method to {@link TreeTabsRegistry} does not bump it: an older consumer never calls the
 * new method, and a newer one asking an older Tree Tabs for it would already have been turned away.
 * Changing or removing one does bump it, and the old version then gets {@code null} rather than a
 * registry that would break halfway through.
 */
public final class TreeTabsApi {

	/**
	 * The current API version. A consumer passes this back to {@link #registry(int)}.
	 *
	 * <p>Version 1: stock sources, locate providers, crafting-list listeners.
	 */
	public static final int VERSION = 1;

	/** The oldest version this build still serves. */
	private static final int OLDEST_SUPPORTED = 1;

	private static TreeTabsRegistry registry;

	private TreeTabsApi() {
	}

	/**
	 * @param builtAgainst the value of {@link #VERSION} the caller compiled against
	 * @return the registry, or null when this build cannot serve that version — in which case the
	 *         caller should carry on without Tree Tabs rather than fail
	 */
	public static TreeTabsRegistry registry(int builtAgainst) {
		if (builtAgainst < OLDEST_SUPPORTED || builtAgainst > VERSION) {
			return null;
		}
		return registry;
	}

	/** Called by Tree Tabs itself during client init. Not part of the API. */
	public static void install(TreeTabsRegistry value) {
		registry = value;
	}
}
