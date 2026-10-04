package com.aiplay.ai;

import com.aiplay.AIMod;
import com.aiplay.ai.AIDecision.Strategy;
import com.aiplay.ai.actions.ActionExecutor;
import com.aiplay.ai.state.GameStateCollector;
import com.aiplay.config.ConfigManager;
import com.aiplay.config.ModConfig;
import com.aiplay.input.Inputs;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates everything: high-level decisions (async DeepSeek, every N ms) + local per-tick Controller.
 * All fields are touched on the client thread only (HTTP callbacks are bounced with client.execute).
 */
public final class AIManager {
	public static volatile boolean enabled = false;
	public static Strategy strategy = Strategy.IDLE;
	public static int targetId = -1;
	public static boolean noTarget = false;

	public static String statusLine = "Idle", lastError = "", lastReason = "-", lastAction = "-";
	public static String lastRequest = "", lastResponse = "", lastState = "";
	public static long latencyMs = -1;
	public static boolean overriding = false;

	private static boolean inFlight = false;
	private static long lastRequestAt = 0, overrideUntil = 0;
	private static int epoch = 0, badResponses = 0;
	private static final ArrayDeque<String> recent = new ArrayDeque<>();

	private AIManager() {}

	public static List<String> recentActions() { return new ArrayList<>(recent); }

	public static void logAction(String s) {
		lastAction = s;
		recent.addLast(s);
		while (recent.size() > 6) recent.removeFirst();
	}

	public static String modelLabel() { return "DeepSeek (" + ConfigManager.cfg.model + ")"; }

	/** GUI toggle entry point. */
	public static void setEnabled(boolean on) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!on) { stop("Turned off by user", false); return; }
		if (mc.player == null || mc.world == null) { AIChat.add("system", "Join a world/server first."); return; }
		if (ConfigManager.apiKey().isEmpty()) {
			fail("DeepSeek API key is not set. Open Settings and paste your key.");
			return;
		}
		epoch++;
		enabled = true;
		lastError = "";
		statusLine = "FULL AI PLAY";
		strategy = Strategy.IDLE; targetId = -1; noTarget = false;
		badResponses = 0; inFlight = false; lastRequestAt = 0; overrideUntil = 0;
		Controller.reset();
		logAction("AI enabled");
	}

	private static void fail(String msg) {
		lastError = msg;
		statusLine = "ERROR";
		AIChat.add("system", msg);
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) mc.player.sendMessage(Text.literal("[AI] " + msg).formatted(Formatting.RED), false);
	}

	/** Turns the AI off immediately, releases all keys and drops every pending action. Safe to call anytime. */
	public static void stop(String reason, boolean error) {
		boolean was = enabled;
		enabled = false;
		epoch++;
		inFlight = false;
		overriding = false;
		strategy = Strategy.IDLE;
		try {
			Controller.reset();
			MinecraftClient mc = MinecraftClient.getInstance();
			Inputs.release(mc);
		} catch (Throwable ignored) {}
		if (error) fail(reason);
		else {
			statusLine = "Idle";
			if (was) {
				AIChat.add("system", "AI OFF: " + reason);
				MinecraftClient mc = MinecraftClient.getInstance();
				if (mc.player != null) mc.player.sendMessage(Text.literal("[AI] OFF - " + reason).formatted(Formatting.YELLOW), true);
			}
		}
	}

	public static void tick(MinecraftClient mc) {
		if (mc.player == null || mc.world == null) { if (enabled) stop("No world", false); return; }
		if (!enabled) { Inputs.release(mc); return; }
		ModConfig cfg = ConfigManager.cfg;
		if (!mc.player.isAlive() || mc.player.getHealth() <= 0) { stop("Player died", false); return; }
		long now = System.currentTimeMillis();

		// Manual override: the human touching WASD/Space/Shift temporarily takes priority
		if (cfg.manualOverride && Inputs.physicalMovePressed(mc)) overrideUntil = now + cfg.manualOverrideMs;
		overriding = now < overrideUntil;

		if (!inFlight && now - lastRequestAt >= cfg.decisionIntervalMs) requestDecision(mc, now);

		if (overriding) {
			Inputs.release(mc);
			ActionExecutor.clearAll();
			statusLine = "MANUAL OVERRIDE";
			return;
		}
		statusLine = "FULL AI PLAY";
		Controller.tick(mc);
	}

	private static void requestDecision(MinecraftClient mc, long now) {
		ModConfig cfg = ConfigManager.cfg;
		JsonObject state = GameStateCollector.collect(mc);
		lastState = new GsonBuilder().setPrettyPrinting().create().toJson(state);
		String user = "STATE:\n" + state + "\nDecide now. Respond with the JSON object only.";
		lastRequest = user;
		lastRequestAt = now;
		inFlight = true;
		final int myEpoch = epoch;
		AIClient.chat(AIPrompt.messages(AIPrompt.decisionSystem(), user), cfg.temperature, cfg.maxTokens)
				.whenComplete((res, err) -> mc.execute(() -> onResult(myEpoch, res, err)));
	}

	private static void onResult(int myEpoch, AIClient.Result res, Throwable err) {
		if (myEpoch != epoch || !enabled) return; // stale (AI was stopped meanwhile)
		inFlight = false;
		if (err != null) { stop("DeepSeek API error: " + AIClient.errorText(err) + " - AI turned off", true); return; }
		latencyMs = res.latencyMs();
		lastResponse = res.content();
		try {
			AIDecision d = AIDecision.parse(res.content());
			badResponses = 0;
			apply(d);
		} catch (Exception e) {
			badResponses++;
			lastError = "Bad AI response (" + badResponses + "/3): " + e.getMessage();
			AIMod.LOG.warn(lastError);
			if (badResponses >= 3) stop("AI returned invalid answers 3 times in a row (" + e.getMessage() + ") - AI turned off", true);
		}
	}

	private static void apply(AIDecision d) {
		ModConfig cfg = ConfigManager.cfg;
		MinecraftClient mc = MinecraftClient.getInstance();
		strategy = d.strategy;
		targetId = d.targetId;
		noTarget = d.noTarget;
		lastReason = d.reason.isBlank() ? "-" : d.reason;
		lastAction = d.strategy.name() + (d.actions.isEmpty() ? "" : " + " + d.actions.get(0).type);
		ActionExecutor.replaceModelActions(d.actions);
		if (d.strategy == Strategy.HEAL && cfg.aiHealing && mc.player != null && !ActionExecutor.usingItemNow() && ActionExecutor.healBusy == 0)
			Controller.startHeal(mc, "AI decision", mc.player.getHealth() / mc.player.getMaxHealth() * 100f <= cfg.criticalThreshold);
		if (cfg.showReasons || cfg.echoToChat) {
			String msg = (cfg.echoToChat && !d.chat.isBlank() ? d.chat + " " : "") + (cfg.showReasons ? "[" + d.strategy + "] " + d.reason : "");
			if (!msg.isBlank() && mc.player != null) mc.player.sendMessage(Text.literal("[AI] " + msg.trim()).formatted(Formatting.GRAY), false);
		}
	}
}
