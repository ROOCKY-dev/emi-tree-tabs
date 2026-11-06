package dev.roocky.emitreetabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reading the config: a bad value costs only its own key, and an unreadable file changes nothing.
 *
 * <p>The config lives in static fields, so each test starts by putting the ones it checks back
 * to known values.
 */
class TreeTabsConfigTest {

	@TempDir
	Path dir;

	@BeforeEach
	void knownValues() {
		TreeTabsConfig.maxTabs = 32;
		TreeTabsConfig.showProgress = true;
		TreeTabsConfig.persistTabs = true;
		TreeTabsConfig.closedTabHistory = 16;
		TreeTabsConfig.tabOrientation = "auto";
	}

	private boolean read(String json) throws IOException {
		Path file = dir.resolve("emitreetabs.json");
		Files.writeString(file, json);
		return TreeTabsConfig.read(file);
	}

	@Test
	@DisplayName("valid keys apply even when a key before or after them is malformed")
	void badKeyCostsOnlyItself() throws IOException {
		boolean ok = read("""
				{ "showProgress": false, "maxTabs": "lots", "closedTabHistory": 4,
				  "persistTabs": { "nested": true }, "tabOrientation": "vertical" }""");

		assertTrue(ok);
		assertFalse(TreeTabsConfig.showProgress);
		assertEquals(32, TreeTabsConfig.maxTabs, "malformed key keeps its current value");
		assertEquals(4, TreeTabsConfig.closedTabHistory);
		assertTrue(TreeTabsConfig.persistTabs, "object where a boolean belongs is ignored");
		assertEquals("vertical", TreeTabsConfig.tabOrientation);
	}

	@Test
	@DisplayName("a file that is not json at all changes nothing")
	void unreadableFileChangesNothing() throws IOException {
		assertFalse(read("{ \"maxTabs\": 5, "));
		assertEquals(32, TreeTabsConfig.maxTabs);
	}

	@Test
	@DisplayName("a json array instead of an object changes nothing")
	void wrongRootChangesNothing() throws IOException {
		assertFalse(read("[1, 2, 3]"));
		assertEquals(32, TreeTabsConfig.maxTabs);
	}

	@Test
	@DisplayName("values the old reader accepted still work: quoted booleans, fractional numbers")
	void oldLenientValuesStillWork() throws IOException {
		assertTrue(read("{ \"showProgress\": \"false\", \"maxTabs\": 12.7 }"));
		assertFalse(TreeTabsConfig.showProgress);
		assertEquals(12, TreeTabsConfig.maxTabs);
	}

	@Test
	@DisplayName("numbers are clamped to their range, including ones too big for an int")
	void numbersAreClamped() throws IOException {
		assertTrue(read("{ \"maxTabs\": 99999999999999, \"closedTabHistory\": -3 }"));
		assertEquals(256, TreeTabsConfig.maxTabs);
		assertEquals(0, TreeTabsConfig.closedTabHistory);
	}
}
