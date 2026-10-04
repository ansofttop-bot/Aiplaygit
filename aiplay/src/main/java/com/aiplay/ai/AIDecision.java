package com.aiplay.ai;

import com.aiplay.ai.actions.Action;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Validated high-level decision returned by the model. */
public class AIDecision {
	public enum Strategy { ATTACK, CHASE, RETREAT, HEAL, DEFEND, USE_ABILITY, EXPLORE, IDLE }

	public Strategy strategy = Strategy.IDLE;
	public int targetId = -1;            // -1 = nearest valid target
	public boolean noTarget = false;
	public final List<Action> actions = new ArrayList<>();
	public String reason = "", chat = "";

	public static AIDecision parse(String raw) {
		JsonObject o = Json.parseObject(raw);
		AIDecision d = new AIDecision();
		String s = Json.str(o, "strategy", "idle").toUpperCase(Locale.ROOT).trim();
		try { d.strategy = Strategy.valueOf(s); }
		catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown strategy: '" + s + "'"); }
		if (o.has("target")) {
			JsonElement t = o.get("target");
			if (t.isJsonPrimitive()) {
				String ts = t.getAsString().trim();
				if (ts.equalsIgnoreCase("none")) d.noTarget = true;
				else try { d.targetId = Integer.parseInt(ts); } catch (NumberFormatException ignored) { d.targetId = -1; }
			}
		}
		for (JsonElement e : Json.arr(o, "actions")) {
			if (!e.isJsonObject()) throw new IllegalArgumentException("action is not an object");
			if (d.actions.size() < 5) d.actions.add(Action.parse(e.getAsJsonObject()));
		}
		d.reason = Json.str(o, "reason", "");
		d.chat = Json.str(o, "chat", "");
		return d;
	}
}
