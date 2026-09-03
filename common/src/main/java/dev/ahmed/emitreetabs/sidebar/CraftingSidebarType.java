package dev.ahmed.emitreetabs.sidebar;

import java.lang.reflect.Field;
import java.util.Arrays;

import dev.ahmed.emitreetabs.EmiTreeTabs;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.config.SidebarSubpanels;
import dev.emi.emi.config.SidebarType;
import sun.misc.Unsafe;

/**
 * Adds a "Crafting" page type to EMI's sidebar picker at runtime.
 *
 * <p>EMI's {@code SidebarType} is a plain enum with no extension point, so the constant is
 * allocated directly and appended to {@code $VALUES}. That is heavy-handed, but it is the only way
 * to appear in EMI's own page and subpanel menus as a first-class option — which is what lets you
 * put the crafting list in a real second panel rather than hijacking favourites.
 *
 * <p>Removal is safe: {@code SidebarType.fromName} falls back to {@code NONE} for a name it does
 * not recognise, so uninstalling this mod leaves an empty panel, not a broken config.
 *
 * <p>Everything is best effort. If any step fails, {@link #TYPE} stays null and the mod carries on
 * with the favourites-panel behaviour instead.
 */
public final class CraftingSidebarType {

	/** EMI derives its lang keys from this as {@code emi.sidebar.type.<name>}. */
	private static final String NAME = "tree_tabs_crafting";

	/** Null when the enum could not be extended. Always null-check before comparing. */
	public static SidebarType TYPE;

	private CraftingSidebarType() {
	}

	public static boolean isOurs(SidebarType type) {
		return TYPE != null && type == TYPE;
	}

	/**
	 * @return true when a Crafting page or subpanel is configured on any sidebar, meaning the
	 *         favourites panel no longer needs to carry the crafting list.
	 */
	public static boolean isPlacedInSidebar() {
		if (TYPE == null) {
			return false;
		}
		try {
			return placed(EmiConfig.leftSidebarPages, EmiConfig.leftSidebarSubpanels)
					|| placed(EmiConfig.rightSidebarPages, EmiConfig.rightSidebarSubpanels)
					|| placed(EmiConfig.topSidebarPages, EmiConfig.topSidebarSubpanels)
					|| placed(EmiConfig.bottomSidebarPages, EmiConfig.bottomSidebarSubpanels);
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean placed(SidebarPages pages, SidebarSubpanels subpanels) {
		if (pages != null && pages.pages != null) {
			for (SidebarPages.SidebarPage page : pages.pages) {
				if (page != null && page.type == TYPE) {
					return true;
				}
			}
		}
		if (subpanels != null && subpanels.subpanels != null) {
			for (SidebarSubpanels.Subpanel subpanel : subpanels.subpanels) {
				if (subpanel != null && subpanel.type == TYPE) {
					return true;
				}
			}
		}
		return false;
	}

	public static void install() {
		if (TYPE != null) {
			return;
		}
		try {
			TYPE = build();
			EmiTreeTabs.LOGGER.info("[emitreetabs] added sidebar page type '{}' at ordinal {}",
					NAME, TYPE.ordinal());
		} catch (Throwable t) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not add a sidebar page type; the crafting "
					+ "list will stay in the favourites panel", t);
			TYPE = null;
		}
	}

	private static SidebarType build() throws Exception {
		SidebarType[] existing = SidebarType.values();
		for (SidebarType type : existing) {
			if (NAME.equals(type.getName())) {
				return type;
			}
		}

		Unsafe unsafe = unsafe();
		// allocateInstance rather than the reflection factory: enum constructors cannot be invoked
		// reflectively, and the JDK's ConstructorAccessor lives in a package that is not exported.
		SidebarType created = (SidebarType) unsafe.allocateInstance(SidebarType.class);

		putObject(unsafe, created, Enum.class.getDeclaredField("name"), NAME.toUpperCase());
		putInt(unsafe, created, Enum.class.getDeclaredField("ordinal"), existing.length);

		// Borrow an existing icon so the picker entry is not blank.
		SidebarType template = SidebarType.CRAFTABLES;
		putObject(unsafe, created, SidebarType.class.getDeclaredField("name"), NAME);
		putInt(unsafe, created, SidebarType.class.getDeclaredField("u"), template.u);
		putInt(unsafe, created, SidebarType.class.getDeclaredField("v"), template.v);

		Field values = SidebarType.class.getDeclaredField("$VALUES");
		Object base = unsafe.staticFieldBase(values);
		long offset = unsafe.staticFieldOffset(values);
		SidebarType[] grown = Arrays.copyOf((SidebarType[]) unsafe.getObject(base, offset), existing.length + 1);
		grown[existing.length] = created;
		unsafe.putObject(base, offset, grown);

		// Class caches the constant list for valueOf; drop it so the new entry is visible there too.
		clearEnumCache(unsafe, "enumConstants");
		clearEnumCache(unsafe, "enumConstantDirectory");
		return created;
	}

	private static void putObject(Unsafe unsafe, Object target, Field field, Object value) {
		unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
	}

	private static void putInt(Unsafe unsafe, Object target, Field field, int value) {
		unsafe.putInt(target, unsafe.objectFieldOffset(field), value);
	}

	private static void clearEnumCache(Unsafe unsafe, String fieldName) {
		try {
			Field field = Class.class.getDeclaredField(fieldName);
			unsafe.putObject(SidebarType.class, unsafe.objectFieldOffset(field), null);
		} catch (Throwable ignored) {
			// EMI resolves config through fromName, not valueOf, so a stale cache is survivable.
		}
	}

	private static Unsafe unsafe() throws Exception {
		Field field = Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return (Unsafe) field.get(null);
	}
}
