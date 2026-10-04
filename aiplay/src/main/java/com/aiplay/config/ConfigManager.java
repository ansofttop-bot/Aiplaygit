package com.aiplay.config;

import com.aiplay.AIMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
	public static ModConfig cfg = new ModConfig();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ConfigManager() {}

	public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("aiplay"); }

	public static void load() {
		try {
			Path f = dir().resolve("config.json");
			if (Files.exists(f)) {
				ModConfig c = GSON.fromJson(Files.readString(f), ModConfig.class);
				if (c != null) cfg = c;
			} else save();
		} catch (Exception e) {
			AIMod.LOG.warn("Config load failed, using defaults: {}", e.toString());
		}
	}

	public static void save() {
		try {
			Files.createDirectories(dir());
			Files.writeString(dir().resolve("config.json"), GSON.toJson(cfg));
		} catch (Exception e) {
			AIMod.LOG.warn("Config save failed: {}", e.toString());
		}
	}

	/** API key from config, falling back to env var DEEPSEEK_API_KEY. Never logged. */
	public static String apiKey() {
		String k = cfg.apiKey == null ? "" : cfg.apiKey.trim();
		if (k.isEmpty()) {
			String env = System.getenv("DEEPSEEK_API_KEY");
			if (env != null) k = env.trim();
		}
		return k;
	}
}
