package dev.ahmed.emitreetabs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.ahmed.emitreetabs.tab.TreeTabs;
import dev.ahmed.emitreetabs.ui.TabBar;
import dev.ahmed.emitreetabs.ui.TreeScreenHooks;
import dev.emi.emi.screen.BoMScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Puts the tab strip on EMI's recipe tree screen.
 *
 * <p>Members inherited from {@code Screen} are remapped as usual; EMI's own fields and methods are
 * marked {@code remap = false} because EMI ships unobfuscated.
 */
@Mixin(BoMScreen.class)
public abstract class BoMScreenMixin extends Screen implements TreeScreenHooks {

	@Shadow(remap = false)
	private double offX;

	@Shadow(remap = false)
	private double offY;

	@Shadow(remap = false)
	private static int zoom;

	@Shadow(remap = false)
	public abstract void recalculateTree();

	private BoMScreenMixin(Component title) {
		super(title);
	}

	// ------------------------------------------------------------ view state

	@Override
	public double emitreetabs$offX() {
		return offX;
	}

	@Override
	public void emitreetabs$offX(double value) {
		offX = value;
	}

	@Override
	public double emitreetabs$offY() {
		return offY;
	}

	@Override
	public void emitreetabs$offY(double value) {
		offY = value;
	}

	@Override
	public int emitreetabs$zoom() {
		return zoom;
	}

	@Override
	public void emitreetabs$zoom(int value) {
		zoom = value;
	}

	@Override
	public void emitreetabs$recalculate() {
		recalculateTree();
	}

	// -------------------------------------------------------------- lifecycle

	@Inject(method = "init()V", at = @At("HEAD"))
	private void emitreetabs$initHead(CallbackInfo ci) {
		TabBar.reset();
		// Catches the case where the deferred restore has not run yet.
		TreeTabs.tryRestore();
		// EMI may have set a tree without going through setGoal; make sure a tab owns it.
		TreeTabs.adoptCurrentTree();
	}

	@Inject(method = "init()V", at = @At("TAIL"))
	private void emitreetabs$initTail(CallbackInfo ci) {
		TreeTabs.applyViewport();
		TabBar.ensureVisible(this, TreeTabs.activeIndex());
	}

	@Inject(method = "onClose", at = @At("HEAD"))
	private void emitreetabs$onClose(CallbackInfo ci) {
		TreeTabs.captureViewport();
		TabBar.reset();
		TreeTabs.flush();
	}

	// ---------------------------------------------------------------- drawing

	@Inject(method = "render", at = @At("TAIL"))
	private void emitreetabs$render(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		TabBar.tickDrag(this);
		TabBar.render(this, graphics, mouseX, mouseY, delta);
	}

	/**
	 * Stops the tree underneath the strip from reacting to a pointer that is over the strip. EMI
	 * routes tooltips, clicks and the favourite keybind through this one lookup, so blocking it here
	 * covers all three. Optional: if EMI ever renames it the bar still works, tooltips just bleed.
	 */
	@Inject(method = "getHoveredStack", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void emitreetabs$suppressHoverUnderBar(int mouseX, int mouseY, CallbackInfoReturnable<Object> cir) {
		if (TabBar.isOver(this, mouseX, mouseY)) {
			cir.setReturnValue(null);
		}
	}

	// ------------------------------------------------------------------ input

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void emitreetabs$mouseClicked(double mouseX, double mouseY, int button,
			CallbackInfoReturnable<Boolean> cir) {
		if (TabBar.mouseClicked(this, mouseX, mouseY, button)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void emitreetabs$mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY,
			CallbackInfoReturnable<Boolean> cir) {
		if (TabBar.mouseDragged(this, mouseX, mouseY, button)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void emitreetabs$mouseScrolled(double mouseX, double mouseY, double amount,
			CallbackInfoReturnable<Boolean> cir) {
		if (TabBar.mouseScrolled(this, mouseX, mouseY, amount)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void emitreetabs$keyPressed(int keyCode, int scanCode, int modifiers,
			CallbackInfoReturnable<Boolean> cir) {
		if (TabBar.keyPressed(this, keyCode, scanCode, modifiers)) {
			cir.setReturnValue(true);
		}
	}
}
