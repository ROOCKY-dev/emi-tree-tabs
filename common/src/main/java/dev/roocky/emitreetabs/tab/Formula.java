package dev.roocky.emitreetabs.tab;

/**
 * Evaluates the arithmetic behind a quantity, so a batch count can be typed as the sum that
 * produced it.
 *
 * <p>Wanting 256 of something because it is 32 machines needing 4 each needing 2 should not mean
 * leaving the tree, finding a calculator, and coming back with a number you can no longer check.
 * {@code 32 * 4 * 2} is as valid an answer as {@code 256}, and it still says <em>why</em>.
 *
 * <p>A hand-written recursive descent parser rather than a scripting engine: the grammar is four
 * operators and brackets, and pulling in an expression library to evaluate {@code 32*4} would be a
 * dependency, an attack surface and a startup cost for no gain.
 *
 * <h2>Deliberate limits</h2>
 *
 * <ul>
 *   <li>Integers only. Batch counts are whole; {@code 2.5} is a typo, not a request.</li>
 *   <li>Division truncates, and {@code /0} is rejected rather than throwing.</li>
 *   <li>Overflow is caught and reported, not wrapped — a silently negative batch count would be a
 *       much worse outcome than a refusal.</li>
 * </ul>
 */
public final class Formula {

	/** What a string parsed to: either a value, or the reason it did not. */
	public record Result(boolean ok, long value, String error) {

		public static Result of(long value) {
			return new Result(true, value, null);
		}

		public static Result fail(String error) {
			return new Result(false, 0, error);
		}
	}

	/** Batch counts must stay positive, and stay small enough that costing a tree terminates. */
	public static final long MAX = 1_000_000L;

	private final String src;
	private int pos;

	private Formula(String src) {
		this.src = src;
	}

	/**
	 * Evaluates an expression.
	 *
	 * @return the value, or a failure carrying a short reason fit to show under an input box
	 */
	public static Result evaluate(String input) {
		if (input == null || input.isBlank()) {
			return Result.fail("empty");
		}
		Formula f = new Formula(input);
		long value;
		try {
			value = f.expression();
		} catch (ArithmeticException e) {
			return Result.fail(e.getMessage());
		}
		f.skipSpace();
		if (f.pos < f.src.length()) {
			return Result.fail("unexpected '" + f.src.charAt(f.pos) + "'");
		}
		if (value < 1) {
			return Result.fail("must be at least 1");
		}
		if (value > MAX) {
			return Result.fail("at most " + MAX);
		}
		return Result.of(value);
	}

	/** True when the string is a plain number, so the caller can skip showing a preview. */
	public static boolean isPlainNumber(String input) {
		if (input == null || input.isBlank()) {
			return false;
		}
		String t = input.trim();
		for (int i = 0; i < t.length(); i++) {
			if (!Character.isDigit(t.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	// --------------------------------------------------------------- parsing

	private long expression() {
		long value = term();
		while (true) {
			skipSpace();
			char c = peek();
			if (c == '+') {
				pos++;
				value = add(value, term());
			} else if (c == '-') {
				pos++;
				value = add(value, -term());
			} else {
				return value;
			}
		}
	}

	private long term() {
		long value = factor();
		while (true) {
			skipSpace();
			char c = peek();
			if (c == '*' || c == 'x' || c == 'X') {
				// 'x' as well as '*', because that is how people write it by hand.
				pos++;
				value = multiply(value, factor());
			} else if (c == '/') {
				pos++;
				long divisor = factor();
				if (divisor == 0) {
					throw new ArithmeticException("cannot divide by zero");
				}
				value = value / divisor;
			} else {
				return value;
			}
		}
	}

	private long factor() {
		skipSpace();
		char c = peek();
		if (c == '-') {
			pos++;
			return -factor();
		}
		if (c == '+') {
			pos++;
			return factor();
		}
		if (c == '(') {
			pos++;
			long value = expression();
			skipSpace();
			if (peek() != ')') {
				throw new ArithmeticException("missing )");
			}
			pos++;
			return value;
		}
		if (!Character.isDigit(c)) {
			throw new ArithmeticException(c == '\0' ? "unfinished" : "unexpected '" + c + "'");
		}
		long value = 0;
		while (Character.isDigit(peek())) {
			value = add(multiply(value, 10), src.charAt(pos) - '0');
			pos++;
		}
		return value;
	}

	private char peek() {
		return pos < src.length() ? src.charAt(pos) : '\0';
	}

	private void skipSpace() {
		while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
			pos++;
		}
	}

	// Overflow is a refusal, never a wrap. A batch count that silently went negative would cost
	// far more to notice than a rejected expression.
	private static long add(long a, long b) {
		long r = a + b;
		if (((a ^ r) & (b ^ r)) < 0) {
			throw new ArithmeticException("number too large");
		}
		return r;
	}

	private static long multiply(long a, long b) {
		long r = a * b;
		if (a != 0 && (r / a != b || (a == -1 && b == Long.MIN_VALUE))) {
			throw new ArithmeticException("number too large");
		}
		return r;
	}
}
