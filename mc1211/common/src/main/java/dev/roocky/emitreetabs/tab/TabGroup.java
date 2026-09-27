package dev.roocky.emitreetabs.tab;

import com.google.gson.JsonObject;

/**
 * A named set of tabs, and whether it is currently being worked on.
 *
 * <p>Not a folder. The problem groups exist to solve is that a large build has <em>phases</em>: you
 * need a whole set of materials before a later set of machinery, and until the machinery's turn
 * comes the crafting list should not be telling you to gather its parts.
 *
 * <p>So the flag that matters is {@link #parked}. Parking keeps the tabs exactly where they are and
 * takes their trees out of the aggregated crafting list — the group is still open in front of you,
 * it has simply stopped asking for anything. {@link #collapsed} is only about screen space.
 */
public final class TabGroup {

	/** Stable across a session and across a restart; tabs refer to their group by this. */
	public final int id;

	/** Shown on the header. Never null; an unnamed group gets a default when it is created. */
	public String name;

	/** ARGB. Drawn on the header and the enclosing border, so phases are told apart at a glance. */
	public int colour;

	/** Folded to just its header. Purely visual — a collapsed group still feeds the crafting list. */
	public boolean collapsed;

	/**
	 * Set aside. Its trees stop feeding the crafting list, while staying open and keeping their own
	 * crafting-mode flags, so unparking restores exactly what you had.
	 */
	public boolean parked;

	public TabGroup(int id, String name, int colour) {
		this.id = id;
		this.name = name;
		this.colour = colour;
	}

	public JsonObject save() {
		JsonObject obj = new JsonObject();
		obj.addProperty("id", id);
		obj.addProperty("name", name);
		obj.addProperty("colour", colour);
		if (collapsed) {
			obj.addProperty("collapsed", true);
		}
		if (parked) {
			obj.addProperty("parked", true);
		}
		return obj;
	}

	/** @return the group, or null when the json is missing the parts that identify one. */
	public static TabGroup load(JsonObject obj) {
		if (obj == null || !obj.has("id")) {
			return null;
		}
		int id;
		try {
			id = obj.get("id").getAsInt();
		} catch (RuntimeException e) {
			return null;
		}
		String name = obj.has("name") ? obj.get("name").getAsString() : "";
		int colour = obj.has("colour") ? obj.get("colour").getAsInt() : DEFAULT_COLOURS[0];
		TabGroup group = new TabGroup(id, name, colour);
		group.collapsed = obj.has("collapsed") && obj.get("collapsed").getAsBoolean();
		group.parked = obj.has("parked") && obj.get("parked").getAsBoolean();
		return group;
	}

	/**
	 * Colours new groups cycle through.
	 *
	 * <p>Deliberately not the progress colours — grey, amber and green already mean "nothing
	 * gathered", "partly stocked" and "ready" on a tab's border, and a group tinted green would
	 * read as a finished group rather than a named one.
	 */
	public static final int[] DEFAULT_COLOURS = {
			0xFF6E8CC8, // slate blue
			0xFF9C6EC8, // violet
			0xFFC86E9C, // rose
			0xFF6EC8C0, // teal
			0xFFC8A06E, // sand
	};

	public static int colourFor(int index) {
		return DEFAULT_COLOURS[Math.floorMod(index, DEFAULT_COLOURS.length)];
	}
}
