package dev.roocky.emitreetabs.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.roocky.emitreetabs.tab.Formula.Result;

class FormulaTest {

	private static long ok(String input) {
		Result r = Formula.evaluate(input);
		assertTrue(r.ok(), "expected '" + input + "' to evaluate, got: " + r.error());
		return r.value();
	}

	private static String bad(String input) {
		Result r = Formula.evaluate(input);
		assertFalse(r.ok(), "expected '" + input + "' to be refused, got " + r.value());
		assertTrue(r.error() != null && !r.error().isBlank(), "a refusal must say why");
		return r.error();
	}

	@Nested
	@DisplayName("the case this exists for")
	class TheRealCase {

		@Test
		@DisplayName("32 machines, 4 each, 2 per that")
		void theExampleFromTheNote() {
			assertEquals(256, ok("32 * 4 * 2"));
			assertEquals(256, ok("32*4*2"));
			assertEquals(256, ok("256"));
		}

		@Test
		@DisplayName("written the way people actually write it by hand")
		void xInsteadOfStar() {
			assertEquals(256, ok("32 x 4 x 2"));
			assertEquals(64, ok("8x8"));
		}

		@Test
		@DisplayName("a plain number needs no preview, an expression does")
		void plainNumberDetection() {
			assertTrue(Formula.isPlainNumber("256"));
			assertTrue(Formula.isPlainNumber("  7 "));
			assertFalse(Formula.isPlainNumber("32*4"));
			assertFalse(Formula.isPlainNumber(""));
			assertFalse(Formula.isPlainNumber(null));
		}
	}

	@Nested
	@DisplayName("arithmetic")
	class Arithmetic {

		@Test
		@DisplayName("the four operators")
		void operators() {
			assertEquals(7, ok("3 + 4"));
			assertEquals(6, ok("10 - 4"));
			assertEquals(12, ok("3 * 4"));
			assertEquals(5, ok("20 / 4"));
		}

		@Test
		@DisplayName("multiplication binds tighter than addition")
		void precedence() {
			assertEquals(14, ok("2 + 3 * 4"));
			assertEquals(20, ok("(2 + 3) * 4"));
			assertEquals(10, ok("2 * 3 + 4"));
		}

		@Test
		@DisplayName("brackets nest")
		void brackets() {
			assertEquals(54, ok("((1 + 2) * 3) * (2 * 3)"));
			assertEquals(9, ok("(((9)))"));
		}

		@Test
		@DisplayName("division truncates, because batches are whole")
		void divisionTruncates() {
			assertEquals(3, ok("10 / 3"));
			assertEquals(1, ok("3 / 2"));
		}

		@Test
		@DisplayName("whitespace anywhere is fine")
		void whitespace() {
			assertEquals(256, ok("  32   *4  * 2   "));
			assertEquals(7, ok("3+4"));
		}

		@Test
		@DisplayName("a leading sign is allowed on a term")
		void unarySigns() {
			assertEquals(4, ok("+4"));
			assertEquals(6, ok("10 + -4"));
			assertEquals(14, ok("10 - -4"));
		}
	}

	@Nested
	@DisplayName("refusals")
	class Refusals {

		@Test
		@DisplayName("a batch count below one is refused, not clamped")
		void mustBePositive() {
			assertTrue(bad("0").contains("at least 1"));
			assertTrue(bad("5 - 5").contains("at least 1"));
			assertTrue(bad("-3").contains("at least 1"));
		}

		@Test
		@DisplayName("absurd numbers are refused, so costing a tree still terminates")
		void bounded() {
			assertTrue(bad("999999999").contains("at most"));
			assertEquals(Formula.MAX, ok(String.valueOf(Formula.MAX)));
		}

		@Test
		@DisplayName("overflow is refused, never wrapped into a negative")
		void overflowIsCaught() {
			// The dangerous case: silently wrapping would produce a plausible-looking batch count.
			String e = bad("999999999999 * 999999999999");
			assertTrue(e.contains("too large") || e.contains("at most"), "got: " + e);
			Result r = Formula.evaluate("9223372036854775807 + 1");
			assertFalse(r.ok());
			assertTrue(r.value() >= 0, "a refusal must not leak a wrapped value");
		}

		@Test
		@DisplayName("dividing by zero is refused rather than throwing")
		void divideByZero() {
			assertTrue(bad("4 / 0").contains("divide by zero"));
			assertTrue(bad("4 / (2 - 2)").contains("divide by zero"));
		}

		@Test
		@DisplayName("junk is refused with something readable")
		void junk() {
			bad("");
			bad("   ");
			bad(null);
			bad("hello");
			bad("4 +");
			bad("* 4");
			assertTrue(bad("(4").contains("missing )"));
			assertTrue(bad("4)").contains("unexpected ')'"));
			bad("4 5");
		}

		@Test
		@DisplayName("no input can throw out of evaluate")
		void neverThrows() {
			String[] nasty = { "((((((((((", "))))", "*/+-", "1/0", "9".repeat(400),
					"(1+2", "1..2", "1 2 3", "--", "+", "/", "()", null, "", " " };
			for (String s : nasty) {
				Result r = Formula.evaluate(s);
				// The only requirement: it returns rather than throwing.
				assertTrue(r.ok() || r.error() != null, "no verdict for: " + s);
			}
		}
	}
}
