package com.aiplay.ai;

import com.aiplay.AIMod;
import com.aiplay.config.ConfigManager;
import com.aiplay.items.ItemKnowledge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Persistent local memory: rule categories + item knowledge. Survives Minecraft restarts. */
public final class AIMemory {
	public static final List<String> CATS = List.of("User Rules", "Combat Rules", "Item Knowledge", "Server Rules", "Preferences");

	private static class Data {
		Map<String, List<String>> rules = new LinkedHashMap<>();
		Map<String, ItemKnowledge> items = new LinkedHashMap<>();
	}

	private static Data data = new Data();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private AIMemory() {}

	private static Path file() { return ConfigManager.dir().resolve("memory.json"); }

	public static synchronized void load() {
		try {
			if (Files.exists(file())) {
				Data d = GSON.fromJson(Files.readString(file()), Data.class);
				if (d != null) data = d;
			}
		} catch (Exception e) { AIMod.LOG.warn("Memory load failed: {}", e.toString()); }
		if (data.rules == null) data.rules = new LinkedHashMap<>();
		if (data.items == null) data.items = new LinkedHashMap<>();
	}

	public static synchronized void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(data));
		} catch (Exception e) { AIMod.LOG.warn("Memory save failed: {}", e.toString()); }
	}

	private static String normCat(String cat) {
		for (String c : CATS) if (c.equalsIgnoreCase(cat == null ? "" : cat.trim())) return c.equals("Item Knowledge") ? "User Rules" : c;
		return "User Rules";
	}

	public static synchronized void addRule(String cat, String text) {
		if (text == null || text.isBlank()) return;
		List<String> l = data.rules.computeIfAbsent(normCat(cat), k -> new ArrayList<>());
		for (String s : l) if (s.equalsIgnoreCase(text.trim())) return;
		if (l.size() >= 60) l.remove(0);
		l.add(text.trim());
	}

	public static synchronized List<String> rules(String cat) {
		return new ArrayList<>(data.rules.getOrDefault(cat, List.of()));
	}

	public static synchronized void removeRule(String cat, String text) {
		List<String> l = data.rules.get(cat);
		if (l != null) l.remove(text);
		save();
	}

	public static synchronized void putItem(ItemKnowledge k) {
		if (k == null || k.name == null || k.name.isBlank()) return;
		ItemKnowledge old = data.items.get(ItemKnowledge.norm(k.name));
		if (old != null) k.lastUsedMs = old.lastUsedMs;
		data.items.put(ItemKnowledge.norm(k.name), k);
	}

	public static synchronized ItemKnowledge item(String normName) { return data.items.get(normName); }
	public static synchronized List<ItemKnowledge> items() { return new ArrayList<>(data.items.values()); }
	public static synchronized void removeItem(String name) { data.items.remove(ItemKnowledge.norm(name)); save(); }

	/** Removes rules/items that contain the fragment (used by "forget ..." chat instructions). */
	public static synchronized void forget(String fragment) {
		String f = fragment == null ? "" : fragment.toLowerCase(Locale.ROOT).trim();
		if (f.isEmpty()) return;
		for (List<String> l : data.rules.values()) l.removeIf(s -> s.toLowerCase(Locale.ROOT).contains(f));
		data.items.values().removeIf(k -> ItemKnowledge.norm(k.name).contains(ItemKnowledge.norm(f)));
	}

	public static synchronized void clear() { data = new Data(); save(); }

	public static synchronized String dumpForPrompt() {
		StringBuilder sb = new StringBuilder();
		for (String cat : CATS) {
			if (cat.equals("Item Knowledge")) {
				sb.append("Item Knowledge:\n");
				if (data.items.isEmpty()) sb.append("- (none)\n");
				for (ItemKnowledge k : data.items.values()) sb.append("- ").append(k.summary()).append('\n');
			} else {
				sb.append(cat).append(":\n");
				List<String> l = data.rules.getOrDefault(cat, List.of());
				if (l.isEmpty()) sb.append("- (none)\n");
				for (String s : l) sb.append("- ").append(s).append('\n');
			}
		}
		return sb.toString();
	}
}
