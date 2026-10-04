package com.aiplay.items;

import java.util.Locale;

/** What the user taught the AI about one item (e.g. a custom server ability item). */
public class ItemKnowledge {
	public String name = "";
	public String description = "";
	public String type = "other";        // heal / attack / movement / defense / buff / utility / other
	public String trigger = "right_click";
	public double cooldownSeconds = 0;
	public transient long lastUsedMs = 0;

	public static String norm(String s) {
		if (s == null) return "";
		return s.replaceAll("§.", "").toLowerCase(Locale.ROOT).replace('_', ' ').replaceAll("\\s+", " ").trim();
	}

	public long cooldownLeftMs() {
		if (cooldownSeconds <= 0 || lastUsedMs == 0) return 0;
		return Math.max(0, (long) (cooldownSeconds * 1000) - (System.currentTimeMillis() - lastUsedMs));
	}

	public void markUsed() { lastUsedMs = System.currentTimeMillis(); }

	public String summary() {
		return name + " [" + type + "]: " + description + " (trigger: " + trigger
				+ (cooldownSeconds > 0 ? ", cooldown " + cooldownSeconds + "s" : "") + ")";
	}
}
