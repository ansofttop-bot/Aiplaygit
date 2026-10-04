package com.aiplay;

import com.aiplay.ai.AIManager;
import com.aiplay.ai.AIMemory;
import com.aiplay.config.ConfigManager;
import com.aiplay.hud.AIHud;
import com.aiplay.input.Keybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIMod implements ClientModInitializer {
	public static final String ID = "aiplay";
	public static final Logger LOG = LoggerFactory.getLogger("AIPlay");

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		AIMemory.load();
		Keybinds.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			try {
				Keybinds.tick(client);
				AIManager.tick(client);
			} catch (Throwable t) {
				LOG.error("AI tick failed", t);
				AIManager.stop("Internal error: " + t.getClass().getSimpleName() + " (see log)", true);
			}
		});
		HudRenderCallback.EVENT.register((ctx, tick) -> {
			try { AIHud.render(ctx); } catch (Throwable t) { LOG.error("HUD failed", t); }
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AIManager.stop("Disconnected from server", false));
		LOG.info("AI Play loaded. RShift = ClickGUI, RCTRL = emergency stop.");
	}
}
