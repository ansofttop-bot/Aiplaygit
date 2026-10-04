package com.aiplay.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** The controller's desired key state for one tick + helpers to apply/release it. */
public final class Inputs {
	public static class Intent {
		public boolean fwd, back, left, right, jump, sneak, sprint, use;
		public boolean anyMove() { return fwd || back || left || right; }
	}

	private static boolean applied = false;
	private static final int[] MANUAL_KEYS = {GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
			GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT};

	private Inputs() {}

	public static void apply(MinecraftClient c, Intent in) {
		GameOptions o = c.options;
		o.forwardKey.setPressed(in.fwd);
		o.backKey.setPressed(in.back);
		o.leftKey.setPressed(in.left);
		o.rightKey.setPressed(in.right);
		o.jumpKey.setPressed(in.jump);
		o.sneakKey.setPressed(in.sneak);
		o.sprintKey.setPressed(in.sprint);
		o.useKey.setPressed(in.use);
		applied = true;
	}

	/** Give keys back to the real keyboard state. */
	public static void release(MinecraftClient c) {
		if (!applied) return;
		applied = false;
		c.options.useKey.setPressed(false);
		KeyBinding.updatePressedStates();
	}

	/** Physical WASD / Space / Shift pressed by the human (only while no screen is open). */
	public static boolean physicalMovePressed(MinecraftClient c) {
		if (c.getWindow() == null || c.currentScreen != null) return false;
		long h = c.getWindow().getHandle();
		for (int k : MANUAL_KEYS) if (InputUtil.isKeyPressed(h, k)) return true;
		return false;
	}
}
