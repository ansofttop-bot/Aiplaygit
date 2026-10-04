package com.aiplay.gui;

import com.aiplay.ai.AIChat;
import com.aiplay.ai.AIManager;
import com.aiplay.ai.AIMemory;
import com.aiplay.config.ConfigManager;
import com.aiplay.config.ModConfig;
import com.aiplay.gui.Components.*;
import com.aiplay.input.Keybinds;
import com.aiplay.items.ItemKnowledge;
import com.aiplay.items.ItemKnowledgeManager;
import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

/** Dark ClickGUI: sidebar categories + scrollable settings + AI chat. Opened with RShift. */
public class ClickGuiScreen extends Screen {
	public enum Cat {
		AI("AI"), COMBAT("Combat"), MOVEMENT("Movement"), INVENTORY("Inventory"), ITEMS("Items"),
		CHAT("Chat"), SETTINGS("Settings"), DEBUG("Debug");
		final String title;
		Cat(String t) { title = t; }
	}

	private static Cat current = Cat.AI;
	private static final int SB = 108;

	private int px, py, pw, ph, cx, cy, cw, ch;
	private float openAnim = 0;
	private final float[] hover = new float[Cat.values().length];
	private final List<Row> rows = new ArrayList<>();
	private double scroll;
	private int contentH, chatScroll, rebuildTimer;
	private Row dragging;
	private long clearArmedUntil = 0;
	private TextFieldWidget chatInput, keyField, modelField;
	private final List<TextFieldWidget> fields = new ArrayList<>();

	public ClickGuiScreen() { super(Text.literal("AI Play")); }

	private static ModConfig cfg() { return ConfigManager.cfg; }

	@Override
	protected void init() {
		pw = Math.min(width - 12, 650);
		ph = Math.min(height - 12, 390);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		cx = px + SB + 10; cy = py + 34; cw = pw - SB - 20; ch = ph - 44;
		fields.clear();

		chatInput = new TextFieldWidget(textRenderer, 0, 0, 100, 16, Text.literal("chat"));
		chatInput.setMaxLength(600);
		chatInput.setPlaceholder(Text.literal("Tell the AI how to play..."));
		chatInput.setText(AIChat.draft);
		chatInput.setChangedListener(s -> AIChat.draft = s);

		keyField = new TextFieldWidget(textRenderer, 0, 0, 100, 16, Text.literal("key"));
		keyField.setMaxLength(300);
		keyField.setPlaceholder(Text.literal("sk-****************"));
		keyField.setText(cfg().apiKey == null ? "" : cfg().apiKey);
		keyField.setRenderTextProvider((s, i) -> Text.literal("*".repeat(s.length())).asOrderedText()); // always masked
		keyField.setChangedListener(s -> cfg().apiKey = s.trim());

		modelField = new TextFieldWidget(textRenderer, 0, 0, 100, 16, Text.literal("model"));
		modelField.setMaxLength(60);
		modelField.setText(cfg().model);
		modelField.setChangedListener(s -> { if (!s.isBlank()) cfg().model = s.trim(); });

		for (TextFieldWidget f : List.of(chatInput, keyField, modelField)) {
			f.visible = false;
			fields.add(f);
			addSelectableChild(f);
		}
		rebuild();
	}

	// ---------------------------------------------------------------- rows per category

	private void rebuild() {
		rows.clear();
		ModConfig c = cfg();
		switch (current) {
			case AI -> {
				rows.add(new Row() {
					{ h = 50; }
					@Override public void render(DrawContext g, net.minecraft.client.font.TextRenderer tr, int mx, int my) {
						Components.rrect(g, x, y, w, h - 3, 4, Components.CARD);
						boolean err = !AIManager.lastError.isEmpty() && !AIManager.enabled;
						int col = AIManager.enabled ? (AIManager.overriding ? Components.WARN : Components.GOOD) : err ? Components.BAD : Components.DIM;
						g.drawText(tr, "\u25CF", x + 8, y + 6, col, false);
						g.drawText(tr, AIManager.enabled ? "AI ONLINE" : err ? "AI ERROR" : "AI OFFLINE", x + 20, y + 6, col, false);
						g.drawText(tr, "Model: DeepSeek", x + 8, y + 18, Components.DIM, false);
						g.drawText(tr, "Mode: " + (AIManager.enabled ? (AIManager.overriding ? "MANUAL OVERRIDE" : "FULL AI") : "OFF"), x + 8, y + 30, Components.DIM, false);
					}
				});
				rows.add(new Toggle("FULL AI PLAY", () -> AIManager.enabled, AIManager::setEnabled));
				rows.add(new Toggle("AI Combat", () -> c.aiCombat, v -> c.aiCombat = v));
				rows.add(new Toggle("AI Movement", () -> c.aiMovement, v -> c.aiMovement = v));
				rows.add(new Toggle("AI Inventory", () -> c.aiInventory, v -> c.aiInventory = v));
				rows.add(new Toggle("AI Item Usage", () -> c.aiItemUsage, v -> c.aiItemUsage = v));
				rows.add(new Toggle("AI Healing", () -> c.aiHealing, v -> c.aiHealing = v));
				rows.add(new Toggle("AI Targeting", () -> c.aiTargeting, v -> c.aiTargeting = v));
				if (!AIManager.lastError.isEmpty() && !AIManager.enabled) {
					for (OrderedText t : textRenderer.wrapLines(Text.literal(AIManager.lastError), 168)) rows.add(new Label(t, Components.BAD));
				}
			}
			case COMBAT -> {
				rows.add(new Header("Targets"));
				rows.add(new Toggle("Target Players", () -> c.targetPlayers, v -> c.targetPlayers = v));
				rows.add(new Toggle("Target Hostile Mobs", () -> c.targetMobs, v -> c.targetMobs = v));
				rows.add(new Header("Fighting"));
				rows.add(new Toggle("Critical Hits (jump attack)", () -> c.critHits, v -> c.critHits = v));
				rows.add(new Toggle("Strafing", () -> c.strafe, v -> c.strafe = v));
				rows.add(new Slider("Attack Range", 2.0, 3.0, 0.1, " blocks", () -> c.attackRange, v -> c.attackRange = v));
				rows.add(new Slider("Target Search Radius", 8, 48, 2, " blocks", () -> c.searchRadius, v -> c.searchRadius = (int) v));
				rows.add(new Header("Survival"));
				rows.add(new Slider("Heal Below (user rule)", 5, 95, 5, " %", () -> c.healThreshold, v -> { c.healThreshold = (int) v; if (c.criticalThreshold > c.healThreshold) c.criticalThreshold = c.healThreshold; }));
				rows.add(new Slider("Critical HP (always heals)", 3, 60, 1, " %", () -> c.criticalThreshold, v -> { c.criticalThreshold = (int) v; if (c.healThreshold < c.criticalThreshold) c.healThreshold = c.criticalThreshold; }));
			}
			case MOVEMENT -> {
				rows.add(new Header("Priority"));
				rows.add(new Toggle("Manual Override", () -> c.manualOverride, v -> c.manualOverride = v));
				rows.add(new Slider("Override Duration", 500, 5000, 100, " ms", () -> c.manualOverrideMs, v -> c.manualOverrideMs = (int) v));
				rows.add(new Header("Safety"));
				rows.add(new Toggle("Auto Jump over obstacles", () -> c.autoJump, v -> c.autoJump = v));
				rows.add(new Toggle("Avoid lava / fire / void", () -> c.avoidHazards, v -> c.avoidHazards = v));
				rows.add(new Label("Manual override reacts to W A S D / Space / Shift.", Components.DIM));
			}
			case INVENTORY -> {
				rows.add(new Toggle("AI Inventory", () -> c.aiInventory, v -> c.aiInventory = v));
				rows.add(new Toggle("Auto-equip best weapon", () -> c.autoWeapon, v -> c.autoWeapon = v));
				rows.add(new Header("Current inventory"));
				if (client != null && client.player != null) {
					var p = client.player;
					rows.add(new Label("Selected slot: " + p.getInventory().selectedSlot + "   Offhand: " + (p.getOffHandStack().isEmpty() ? "-" : ItemKnowledgeManager.displayName(p.getOffHandStack())), Components.DIM));
					for (int i = 0; i < 36; i++) {
						ItemStack s = p.getInventory().getStack(i);
						if (s.isEmpty()) continue;
						rows.add(new Label((i < 9 ? "[H" + i + "] " : "[" + i + "] ") + ItemKnowledgeManager.displayName(s) + " x" + s.getCount(), i == p.getInventory().selectedSlot ? Components.GOOD : Components.TEXT));
					}
				} else rows.add(new Label("Join a world to see your inventory.", Components.DIM));
			}
			case ITEMS -> buildItems();
			case CHAT -> {
				rows.add(new Toggle("Echo AI chat lines in game chat", () -> c.echoToChat, v -> c.echoToChat = v));
				rows.add(new Toggle("Show decision reasons in game chat", () -> c.showReasons, v -> c.showReasons = v));
				rows.add(new Button("Clear chat log", Components.WARN, () -> { AIChat.log.clear(); rebuild(); }));
				for (String cat : AIMemory.CATS) {
					if (cat.equals("Item Knowledge")) continue;
					rows.add(new Header(cat));
					List<String> l = AIMemory.rules(cat);
					if (l.isEmpty()) rows.add(new Label("(none)", Components.DIM));
					for (String r : l) rows.add(new ItemRow(r, null, 0, "Del", () -> { AIMemory.removeRule(cat, r); rebuild(); }));
				}
				rows.add(new Header("Item Knowledge"));
				List<ItemKnowledge> its = AIMemory.items();
				if (its.isEmpty()) rows.add(new Label("(none)", Components.DIM));
				for (ItemKnowledge k : its) rows.add(new ItemRow(k.name + " - " + k.description, null, 0, "Del", () -> { AIMemory.removeItem(k.name); rebuild(); }));
			}
			case SETTINGS -> {
				rows.add(new Header("DeepSeek"));
				rows.add(new FieldRow("DeepSeek API Key (stored locally, masked)", keyField));
				rows.add(new FieldRow("Model", modelField));
				rows.add(new Slider("Temperature", 0.0, 1.5, 0.05, "", () -> c.temperature, v -> c.temperature = v));
				rows.add(new Slider("Decision interval", 500, 5000, 100, " ms", () -> c.decisionIntervalMs, v -> c.decisionIntervalMs = (int) v));
				rows.add(new Slider("Max tokens", 200, 2000, 50, "", () -> c.maxTokens, v -> c.maxTokens = (int) v));
				rows.add(new Header("Interface"));
				rows.add(new Toggle("AI Status HUD", () -> c.showHud, v -> c.showHud = v));
				rows.add(new Toggle("Debug Mode", () -> c.debug, v -> c.debug = v));
				rows.add(new Header("Memory"));
				boolean armed = System.currentTimeMillis() < clearArmedUntil;
				rows.add(new Button(armed ? "Click again to CONFIRM clearing memory" : "Clear AI Memory (rules + item knowledge)", Components.BAD, () -> {
					if (System.currentTimeMillis() < clearArmedUntil) { AIMemory.clear(); clearArmedUntil = 0; AIChat.add("system", "AI memory cleared."); }
					else clearArmedUntil = System.currentTimeMillis() + 4000;
					rebuild();
				}));
				rows.add(new Label("Files: .minecraft/config/aiplay/ (config.json, memory.json)", Components.DIM));
			}
			case DEBUG -> {
				rows.add(new Toggle("Debug Mode", () -> c.debug, v -> c.debug = v));
				if (!c.debug) { rows.add(new Label("Enable Debug Mode to record details.", Components.DIM)); break; }
				addBlock("Current action", AIManager.lastAction + "  | strategy: " + AIManager.strategy);
				addBlock("Decision reason", AIManager.lastReason);
				addBlock("API latency", AIManager.latencyMs < 0 ? "-" : AIManager.latencyMs + " ms");
				addBlock("Last error", AIManager.lastError.isEmpty() ? "-" : AIManager.lastError);
				addBlock("Last AI request (state)", cut(AIManager.lastRequest, 1800));
				addBlock("Last AI response", cut(AIManager.lastResponse, 1500));
				addBlock("Game state", cut(AIManager.lastState, 2500));
			}
		}
		for (Row r : rows) if (r instanceof FieldRow f) f.field.visible = false;
	}

	private static String cut(String s, int n) { return s == null ? "" : (s.length() > n ? s.substring(0, n) + " ..." : s); }

	private void addBlock(String title, String text) {
		rows.add(new Header(title));
		for (String line : text.split("\n")) {
			for (OrderedText t : textRenderer.wrapLines(Text.literal(line.replace("\t", "  ")), cw - 8)) rows.add(new Label(t, Components.TEXT));
		}
	}

	private void buildItems() {
		rows.add(new Label("Teach unknown items in AI Chat, e.g. \"Shadow Dash teleports 5 blocks, cooldown 8s, right click\".", Components.DIM));
		List<ItemStack> inv = client != null && client.player != null ? ItemKnowledgeManager.inventoryStacks(client.player) : List.of();
		LinkedHashMap<String, String> unknown = new LinkedHashMap<>(), known = new LinkedHashMap<>();
		for (ItemStack s : inv) {
			String n = ItemKnowledgeManager.displayName(s);
			if (ItemKnowledgeManager.isKnown(s)) known.putIfAbsent(n, ItemKnowledgeManager.knowledgeFor(s) != null ? "TAUGHT" : "VANILLA");
			else unknown.putIfAbsent(n, "UNKNOWN");
		}
		rows.add(new Header("Unknown Items"));
		if (unknown.isEmpty()) rows.add(new Label("(none)", Components.DIM));
		for (String n : unknown.keySet()) rows.add(new ItemRow(n, "UNKNOWN", Components.BAD, "Explain to AI", () -> explain(n)));
		rows.add(new Header("Known Items"));
		Set<String> shown = new HashSet<>();
		for (ItemKnowledge k : AIMemory.items()) { shown.add(ItemKnowledge.norm(k.name)); rows.add(new ItemRow(k.name, k.type.toUpperCase(), Components.GOOD, "Edit", () -> explain(k.name))); }
		for (var e : known.entrySet()) if (!shown.contains(ItemKnowledge.norm(e.getKey())) && e.getValue().equals("VANILLA")) rows.add(new ItemRow(e.getKey(), "VANILLA", Components.DIM, null, null));
	}

	private void explain(String name) {
		current = Cat.AI;
		chatInput.setText("Item \"" + name + "\": ");
		rebuild();
		setFocused(chatInput);
		chatInput.setFocused(true);
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void render(DrawContext g, int mx, int my, float delta) {
		openAnim = Math.min(1f, openAnim + 0.1f);
		g.fill(0, 0, width, height, ((int) (openAnim * 0x78)) << 24);
		for (TextFieldWidget f : fields) f.visible = false;

		Components.rrect(g, px, py, pw, ph, 7, Components.BG);
		Components.rrect(g, px, py, SB, ph, 7, Components.SIDE);
		g.fill(px + SB - 7, py, px + SB, py + ph, Components.SIDE);
		g.drawText(textRenderer, Text.literal("AI PLAY").formatted(Formatting.BOLD).asOrderedText(), px + 12, py + 11, Components.ACCENT, false);

		Cat[] cats = Cat.values();
		for (int i = 0; i < cats.length; i++) {
			int by = py + 32 + i * 25;
			boolean over = mx >= px + 6 && mx < px + SB - 6 && my >= by && my < by + 22;
			hover[i] += ((over ? 1 : 0) - hover[i]) * 0.3f;
			boolean sel = cats[i] == current;
			if (sel || hover[i] > 0.02f) Components.rrect(g, px + 6, by, SB - 12, 22, 4, sel ? Components.mix(Components.SIDE, Components.ACCENT, 0.35f) : Components.mix(Components.SIDE, Components.CARD_HOVER, hover[i]));
			if (sel) g.fill(px + 6, by + 5, px + 8, by + 17, Components.ACCENT);
			g.drawText(textRenderer, cats[i].title, px + 16, by + 7, sel ? Components.TEXT : Components.DIM, false);
		}
		int sy = py + ph - 30;
		boolean stopHover = mx >= px + 8 && mx < px + SB - 8 && my >= sy && my < sy + 22;
		Components.rrect(g, px + 8, sy, SB - 16, 22, 4, stopHover ? 0xFFE0334F : 0xFFB02A40);
		String st = "STOP AI";
		g.drawText(textRenderer, st, px + (SB - textRenderer.getWidth(st)) / 2, sy + 7, 0xFFFFFFFF, false);

		g.drawText(textRenderer, Text.literal(current.title).formatted(Formatting.BOLD).asOrderedText(), cx, py + 12, Components.TEXT, false);
		String hint = "RShift: close   RCTRL: emergency stop";
		g.drawText(textRenderer, hint, px + pw - 10 - textRenderer.getWidth(hint), py + 12, Components.DIM, false);

		int rw = current == Cat.AI ? 176 : cw;
		int total = 0;
		for (Row r : rows) total += r.h;
		contentH = total;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - ch)));

		g.enableScissor(cx - 2, cy, cx + cw + 2, cy + ch);
		int yy = cy - (int) scroll;
		for (Row r : rows) {
			r.layout(cx, yy, rw);
			if (yy + r.h > cy && yy < cy + ch) r.render(g, textRenderer, mx, my);
			yy += r.h;
		}
		for (TextFieldWidget f : fields) if (f != chatInput && f.visible) f.render(g, mx, my, delta);
		g.disableScissor();
		if (contentH > ch) {
			int bh = Math.max(16, ch * ch / contentH);
			int bt = cy + (int) ((ch - bh) * (scroll / (contentH - ch)));
			g.fill(cx + rw + (current == Cat.AI ? 0 : 4), bt, cx + rw + (current == Cat.AI ? 0 : 4) + 2, bt + bh, Components.OFF);
		}
		if (current == Cat.AI) renderChat(g, mx, my, delta);
	}

	private int chatX() { return cx + 186; }
	private int chatW() { return cw - 186; }

	private void renderChat(DrawContext g, int mx, int my, float delta) {
		int x0 = chatX(), w = chatW(), y0 = cy, y1 = cy + ch;
		Components.rrect(g, x0, y0, w, ch, 5, Components.CARD);
		g.drawText(textRenderer, "AI Chat - teach the AI how to play", x0 + 8, y0 + 6, Components.DIM, false);
		int ay0 = y0 + 20, ay1 = y1 - 28;
		record L(OrderedText t, int col) {}
		List<L> lines = new ArrayList<>();
		if (AIChat.log.isEmpty()) {
			String ex = "Examples: \"Heal below 40% HP. If the enemy retreats use Shadow Dash. Don't waste Golden Apples.\"  or  \"Shadow Dash teleports me 5 blocks forward, cooldown 8s, right click.\"";
			for (OrderedText t : textRenderer.wrapLines(Text.literal(ex), w - 18)) lines.add(new L(t, Components.DIM));
		}
		int from = Math.max(0, AIChat.log.size() - 40);
		for (int i = from; i < AIChat.log.size(); i++) {
			AIChat.Msg m = AIChat.log.get(i);
			String pre = m.role().equals("user") ? "You: " : m.role().equals("ai") ? "AI: " : "! ";
			int col = m.role().equals("user") ? Components.ACCENT : m.role().equals("ai") ? Components.GOOD : Components.BAD;
			boolean first = true;
			for (OrderedText t : textRenderer.wrapLines(Text.literal(pre + m.text()), w - 18)) { lines.add(new L(t, first ? col : Components.TEXT)); first = false; }
		}
		if (AIChat.pending) lines.add(new L(Text.literal("AI is thinking...").asOrderedText(), Components.DIM));
		int vis = (ay1 - ay0) / 10;
		chatScroll = Math.max(0, Math.min(chatScroll, Math.max(0, lines.size() - vis)));
		int start = Math.max(0, lines.size() - vis - chatScroll);
		g.enableScissor(x0, ay0, x0 + w, ay1);
		for (int i = start; i < Math.min(lines.size(), start + vis + 1); i++)
			g.drawText(textRenderer, lines.get(i).t(), x0 + 8, ay0 + (i - start) * 10, lines.get(i).col(), false);
		g.disableScissor();
		chatInput.setX(x0 + 6); chatInput.setY(y1 - 22); chatInput.setWidth(w - 12 - 44); chatInput.visible = true;
		chatInput.render(g, mx, my, delta);
		int bx = x0 + w - 44, by = y1 - 22;
		boolean hv = mx >= bx && mx < bx + 38 && my >= by && my < by + 16;
		Components.rrect(g, bx, by + 0, 38, 16, 3, hv ? Components.ACCENT : Components.mix(Components.CARD, Components.ACCENT, 0.5f));
		g.drawText(textRenderer, "Send", bx + 7, by + 4, Components.TEXT, false);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double mxD, double myD, int btn) {
		int mx = (int) mxD, my = (int) myD;
		if (btn == 0) {
			Cat[] cats = Cat.values();
			for (int i = 0; i < cats.length; i++) {
				int by = py + 32 + i * 25;
				if (mx >= px + 6 && mx < px + SB - 6 && my >= by && my < by + 22) {
					current = cats[i]; scroll = 0; chatScroll = 0; setFocused(null); rebuild(); return true;
				}
			}
			int sy = py + ph - 30;
			if (mx >= px + 8 && mx < px + SB - 8 && my >= sy && my < sy + 22) { AIManager.stop("STOP AI button", false); return true; }
			if (current == Cat.AI && mx >= chatX() + chatW() - 44 && mx < chatX() + chatW() - 6 && my >= cy + ch - 22 && my < cy + ch - 6) { sendChat(); return true; }
		}
		if (super.mouseClicked(mxD, myD, btn)) return true;
		if (my >= cy && my < cy + ch) {
			for (Row r : rows) {
				if (r.click(mx, my, btn)) { if (r instanceof Slider) dragging = r; ConfigManager.save(); return true; }
			}
		}
		setFocused(null);
		return false;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
		if (dragging != null) { dragging.drag((int) mx); return true; }
		return super.mouseDragged(mx, my, btn, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int btn) {
		if (dragging != null) { dragging = null; ConfigManager.save(); }
		return super.mouseReleased(mx, my, btn);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (current == Cat.AI && mx >= chatX()) chatScroll += (int) Math.signum(v) * 3;
		else scroll -= v * 22;
		return true;
	}

	private boolean typing() {
		for (TextFieldWidget f : fields) if (f.isFocused() && f.visible) return true;
		return false;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (Keybinds.emergency != null && Keybinds.emergency.matchesKey(key, scan)) { AIManager.stop("EMERGENCY STOP (RCTRL)", false); return true; }
		if (chatInput.isFocused() && chatInput.visible && (key == 257 || key == 335)) { sendChat(); return true; }
		if (key == 344 && !typing()) { close(); return true; } // RShift closes the GUI
		return super.keyPressed(key, scan, mods);
	}

	private void sendChat() {
		String t = chatInput.getText().trim();
		if (t.isEmpty()) return;
		chatInput.setText("");
		chatScroll = 0;
		AIChat.send(t);
	}

	@Override
	public void tick() {
		if (++rebuildTimer % 10 == 0 && !typing()) {
			if (current == Cat.INVENTORY || current == Cat.ITEMS || current == Cat.DEBUG || current == Cat.CHAT
					|| (current == Cat.AI) || (clearArmedUntil != 0 && System.currentTimeMillis() > clearArmedUntil)) {
				if (clearArmedUntil != 0 && System.currentTimeMillis() > clearArmedUntil) clearArmedUntil = 0;
				if (dragging == null) rebuild();
			}
		}
	}

	@Override public boolean shouldPause() { return false; }
	@Override public void renderBackground(DrawContext c, int mx, int my, float d) {}

	@Override
	public void removed() {
		ConfigManager.save();
		AIMemory.save();
	}
}
