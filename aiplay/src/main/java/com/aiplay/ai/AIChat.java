package com.aiplay.ai;

import com.aiplay.config.ConfigManager;
import com.aiplay.items.ItemKnowledge;
import com.aiplay.items.ItemKnowledgeManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/** Chat with the AI: user messages are compiled into persistent structured rules / item knowledge. */
public final class AIChat {
	public record Msg(String role, String text) {}

	public static final List<Msg> log = new ArrayList<>();
	public static boolean pending = false;
	public static String draft = "";

	private AIChat() {}

	public static void add(String role, String text) {
		log.add(new Msg(role, text));
		if (log.size() > 200) log.remove(0);
	}

	public static void send(String text) {
		if (pending) { add("system", "Wait for the previous answer..."); return; }
		MinecraftClient mc = MinecraftClient.getInstance();
		add("user", text);
		pending = true;
		JsonArray msgs = new JsonArray();
		msgs.add(AIPrompt.msg("system", AIPrompt.chatSystem()));
		int from = Math.max(0, log.size() - 11);
		for (int i = from; i < log.size() - 1; i++) {
			Msg m = log.get(i);
			if (m.role().equals("user")) msgs.add(AIPrompt.msg("user", m.text()));
			else if (m.role().equals("ai")) msgs.add(AIPrompt.msg("assistant", m.text()));
		}
		String custom = mc.player == null ? "" : ItemKnowledgeManager.customItemsSummary(mc.player);
		msgs.add(AIPrompt.msg("user", (custom.isEmpty() ? "" : "[Custom items in my inventory: " + custom + "]\n") + text));
		AIClient.chat(msgs, 0.2, 800).whenComplete((res, err) -> mc.execute(() -> {
			pending = false;
			if (err != null) { add("system", "Error: " + AIClient.errorText(err)); return; }
			try { apply(Json.parseObject(res.content())); }
			catch (Exception e) { add("system", "AI answered in an unexpected format: " + e.getMessage()); }
		}));
	}

	private static void apply(JsonObject o) {
		int nr = 0, ni = 0;
		for (JsonElement e : Json.arr(o, "rules")) {
			if (!e.isJsonObject()) continue;
			JsonObject r = e.getAsJsonObject();
			String t = Json.str(r, "text", "");
			if (!t.isBlank()) { AIMemory.addRule(Json.str(r, "category", "User Rules"), t); nr++; }
		}
		for (JsonElement e : Json.arr(o, "items")) {
			if (!e.isJsonObject()) continue;
			JsonObject it = e.getAsJsonObject();
			ItemKnowledge k = new ItemKnowledge();
			k.name = Json.str(it, "name", "").trim();
			if (k.name.isEmpty()) continue;
			k.description = Json.str(it, "description", "");
			k.type = Json.str(it, "type", "other").toLowerCase();
			k.trigger = Json.str(it, "trigger", "right_click");
			k.cooldownSeconds = Math.max(0, Math.min(600, Json.dbl(it, "cooldown_seconds", 0)));
			AIMemory.putItem(k);
			ni++;
		}
		for (JsonElement e : Json.arr(o, "forget")) if (e.isJsonPrimitive()) AIMemory.forget(e.getAsString());
		if (o.has("settings") && o.get("settings").isJsonObject()) {
			JsonObject s = o.getAsJsonObject("settings");
			var cfg = ConfigManager.cfg;
			if (s.has("heal_below_percent")) cfg.healThreshold = Math.max(5, Math.min(95, Json.num(s, "heal_below_percent", cfg.healThreshold)));
			if (s.has("critical_hp_percent")) cfg.criticalThreshold = Math.max(3, Math.min(60, Json.num(s, "critical_hp_percent", cfg.criticalThreshold)));
			if (s.has("attack_range")) cfg.attackRange = Math.max(2.0, Math.min(3.0, Json.dbl(s, "attack_range", cfg.attackRange)));
			if (cfg.criticalThreshold > cfg.healThreshold) cfg.criticalThreshold = cfg.healThreshold;
			ConfigManager.save();
		}
		AIMemory.save();
		String reply = Json.str(o, "reply", "OK");
		if (nr + ni > 0) reply += "  [saved: " + nr + " rule(s), " + ni + " item(s)]";
		add("ai", reply);
	}
}
