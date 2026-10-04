package com.aiplay.config;

public class ModConfig {
	// --- DeepSeek (stored locally only, never in the jar) ---
	public String apiKey = "";
	public String apiUrl = "https://api.deepseek.com/chat/completions";
	public String model = "deepseek-chat";
	public double temperature = 0.5;
	public int decisionIntervalMs = 1500;
	public int maxTokens = 700;

	// --- AI modules ---
	public boolean aiCombat = true, aiMovement = true, aiInventory = true,
			aiItemUsage = true, aiHealing = true, aiTargeting = true;

	// --- Combat ---
	public boolean targetPlayers = true, targetMobs = true, critHits = true, strafe = true;
	public double attackRange = 3.0;
	public int healThreshold = 40;      // user-rule level (percent HP)
	public int criticalThreshold = 20;  // critical survival level (percent HP)
	public int searchRadius = 24;

	// --- Movement ---
	public boolean manualOverride = true, autoJump = true, avoidHazards = true;
	public int manualOverrideMs = 1500;

	// --- Inventory ---
	public boolean autoWeapon = true;

	// --- Chat / UI ---
	public boolean echoToChat = false, showReasons = false, showHud = true, debug = false;
}
