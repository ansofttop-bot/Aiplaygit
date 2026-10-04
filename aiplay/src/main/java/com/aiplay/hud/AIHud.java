package com.aiplay.hud;

import com.aiplay.ai.AIManager;
import com.aiplay.ai.Controller;
import com.aiplay.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public final class AIHud {
	private AIHud() {}

	public static void render(DrawContext c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!ConfigManager.cfg.showHud || mc.player == null || mc.options.hudHidden) return;
		TextRenderer tr = mc.textRenderer;
		boolean err = !AIManager.lastError.isEmpty() && !AIManager.enabled;
		int dot = AIManager.enabled ? (AIManager.overriding ? 0xFFFFB84D : 0xFF3DDC84) : err ? 0xFFFF5470 : 0xFF6B7280;
		String state = AIManager.enabled ? "ONLINE" : err ? "ERROR" : "OFFLINE";
		String mode = !AIManager.enabled ? "OFF" : AIManager.overriding ? "MANUAL OVERRIDE" : "FULL PLAY";
		String target = Controller.currentTarget != null && AIManager.enabled ? Controller.currentTarget.getName().getString() : "-";
		String[] lines = {
				"Mode: " + mode,
				"Target: " + target,
				"HP: " + Math.round(mc.player.getHealth()) + "/" + Math.round(mc.player.getMaxHealth()),
				"Action: " + trim(tr, AIManager.enabled ? AIManager.strategy.name() : "-", 110),
				"Model: DeepSeek" + (ConfigManager.cfg.debug && AIManager.latencyMs >= 0 ? "  " + AIManager.latencyMs + "ms" : "")
		};
		int w = 128, x = 6, y = 6, h = 14 + lines.length * 10;
		c.fill(x, y, x + w, y + h, 0xB0101218);
		c.fill(x, y, x + 2, y + h, dot);
		c.drawText(tr, "AI", x + 7, y + 4, 0xFFFFFFFF, true);
		c.drawText(tr, "\u25CF", x + 20, y + 4, dot, false);
		c.drawText(tr, state, x + 30, y + 4, dot, true);
		for (int i = 0; i < lines.length; i++) c.drawText(tr, lines[i], x + 7, y + 15 + i * 10, 0xFFCDD2E0, false);
		if (err) c.drawText(tr, trim(tr, AIManager.lastError, 300), x, y + h + 3, 0xFFFF8899, true);
	}

	private static String trim(TextRenderer tr, String s, int w) { return tr.trimToWidth(s, w); }
}
