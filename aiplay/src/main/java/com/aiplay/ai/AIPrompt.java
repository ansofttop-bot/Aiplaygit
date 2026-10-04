package com.aiplay.ai;

import com.aiplay.config.ConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class AIPrompt {
	private AIPrompt() {}

	public static JsonObject msg(String role, String content) {
		JsonObject o = new JsonObject();
		o.addProperty("role", role);
		o.addProperty("content", content);
		return o;
	}

	public static String decisionSystem() {
		var c = ConfigManager.cfg;
		return """
You are the high-level tactical brain of a client-side autopilot that controls the user's OWN Minecraft Java 1.21.4 character on the user's private PvP test server.
A fast local controller executes your decision every tick (aiming, walking, hitting, eating). You are only consulted every ~%d ms, so give a strategy that stays valid for a couple of seconds.
Reply with EXACTLY ONE JSON object and nothing else (no markdown, no code, no commands).

JSON schema:
{
 "strategy": "attack|chase|retreat|heal|defend|use_ability|explore|idle",
 "target": "<entity id from nearby_entities> | \\"nearest\\" | \\"none\\"",
 "actions": [ {"action": "...", "reason": "..."} ],   // optional, max 3, executed in order before/with the strategy
 "reason": "<one short sentence>"
}
Allowed actions (anything else is rejected):
- {"action":"switch_slot","slot":N}            N = 0-8 hotbar (0 = leftmost); 9-35 swaps that inventory slot into the selected hotbar slot
- {"action":"use_item","item":"<name or id fragment>"}  or {"action":"use_item","slot":N}   right-click an item (abilities, potions, food). "healing_potion" / "heal" picks the best healing item
- {"action":"interact"}                         right-click whatever is under the crosshair
- {"action":"move","direction":"forward|back|left|right","duration":MS}   duration 100-3000 ms
- {"action":"look","yaw":DEG,"pitch":DEG}  or {"action":"look","at":ENTITY_ID}
- {"action":"jump"}  {"action":"sprint","enabled":true|false}  {"action":"sneak","enabled":true|false}
- {"action":"attack","target":ENTITY_ID}  {"action":"drop_item","slot":N}  {"action":"swap_to_hotbar","slot":9-35,"hotbar":0-8}
- {"action":"stop"}  {"action":"wait","duration":MS}
Strategies: attack = approach + melee the target; chase = approach without hitting; retreat = run away; heal = use a healing item now; defend = face target, block with shield, back off; use_ability = face target and use abilities from "actions"; explore = walk forward avoiding obstacles; idle = do nothing.

Hard rules:
1. Obey the USER MEMORY below. User rules have priority over your own tactics. Survival comes first: the local controller already heals automatically at HP <= %d%% (user threshold) and <= %d%% (critical), so do not wait for it, but you may heal earlier if the rules say so.
2. Use only items that are known (known=true) or ordinary vanilla items. Never use a custom item that is flagged known=false.
3. Respect item cooldowns (on_cooldown / cooldown_left_s). Do not waste rare items (e.g. golden apples) unless necessary.
4. Entity ids and slot numbers come only from the state. Slots are 0-based inventory indices.
5. Only the modules in "enabled_modules" are active; actions of disabled modules are ignored.
6. Think about the opponent: distance, relative_yaw (0 = ahead, positive = right), moving_away (retreating), hurt, holding. If the enemy retreats, chase or use a gap-closer ability if the rules allow. Keep reasons short.

USER MEMORY (persistent rules and item knowledge):
%s""".formatted(c.decisionIntervalMs, c.healThreshold, c.criticalThreshold, AIMemory.dumpForPrompt());
	}

	public static String chatSystem() {
		return """
You are the instruction compiler of a Minecraft PvP autopilot mod. The user chats with you to teach the autopilot how to play. Your job is to turn their message into STRUCTURED, persistent behaviour rules and item knowledge, and to confirm what you understood.
Reply with EXACTLY ONE JSON object:
{
 "reply": "1-3 sentences in the user's language confirming what you understood (or answering a question)",
 "rules": [ {"category": "Combat Rules|User Rules|Server Rules|Preferences", "text": "short imperative rule, e.g. 'Heal below 40% HP'"} ],
 "items": [ {"name": "exact item name", "description": "what it does", "type": "heal|attack|movement|defense|buff|utility|other", "trigger": "right_click|left_click|auto|other", "cooldown_seconds": 0} ],
 "settings": { "heal_below_percent": 40, "critical_hp_percent": 20, "attack_range": 3.0 },
 "forget": [ "fragment of a rule or item name the user asks to forget" ]
}
Rules: include only what the user actually said. "settings" keys only when the user gives such a number. Empty arrays/objects when nothing applies. If the user only asks a question or chats, answer in "reply" and leave the rest empty. Never output code or commands.

CURRENT MEMORY:
""" + AIMemory.dumpForPrompt();
	}

	public static JsonArray messages(String system, String user) {
		JsonArray a = new JsonArray();
		a.add(msg("system", system));
		a.add(msg("user", user));
		return a;
	}
}
