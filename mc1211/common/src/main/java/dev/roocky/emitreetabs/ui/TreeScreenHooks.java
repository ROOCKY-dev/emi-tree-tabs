package dev.roocky.emitreetabs.ui;

/**
 * Implemented on EMI's {@code BoMScreen} by a mixin. Lets the tab manager read and write the
 * screen's private viewport without the rest of the mod having to know about mixin internals.
 */
public interface TreeScreenHooks {
	double emitreetabs$offX();

	void emitreetabs$offX(double value);

	double emitreetabs$offY();

	void emitreetabs$offY(double value);

	int emitreetabs$zoom();

	void emitreetabs$zoom(int value);

	/** Rebuilds the laid out node graph. EMI does this whenever the tree changes shape. */
	void emitreetabs$recalculate();
}
