package com.aiplay.input;

import com.aiplay.ai.AIManager;
import com.aiplay.gui.ClickGuiScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {
	public static KeyBinding openGui, emergency;

	private Keybinds() {}

	public static void register() {
		openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aiplay.open_gui",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.aiplay"));
		emergency = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aiplay.emergency",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, "category.aiplay"));
	}

	public static void tick(MinecraftClient c) {
		while (openGui.wasPressed()) {
			if (c.currentScreen == null && c.player != null) c.setScreen(new ClickGuiScreen());
		}
		// Emergency stop: polls the physical key every tick (works with GUI open/closed, never waits on the API).
		if (c.getWindow() != null && AIManager.enabled) {
			int code = KeyBindingHelper.getBoundKeyOf(emergency).getCode();
			if (InputUtil.isKeyPressed(c.getWindow().getHandle(), code)) AIManager.stop("EMERGENCY STOP (RCTRL)", false);
		}
	}
}
