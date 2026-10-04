package com.aiplay.items;

import com.aiplay.ai.AIMemory;
import com.aiplay.config.ConfigManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;

import java.util.*;

/** Item detection / knowledge lookup / healing + weapon selection helpers. */
public final class ItemKnowledgeManager {
	private ItemKnowledgeManager() {}

	public static String displayName(ItemStack s) { return s.getName().getString().replaceAll("§.", "").trim(); }
	public static String idPath(ItemStack s) { return Registries.ITEM.getId(s.getItem()).getPath(); }

	/** Server-style custom item: custom name, lore or custom model data. */
	public static boolean isCustom(ItemStack s) {
		if (s.isEmpty()) return false;
		if (s.contains(DataComponentTypes.CUSTOM_NAME) || s.contains(DataComponentTypes.CUSTOM_MODEL_DATA)) return true;
		LoreComponent l = s.get(DataComponentTypes.LORE);
		return l != null && !l.lines().isEmpty();
	}

	public static List<String> lore(ItemStack s, int max) {
		List<String> out = new ArrayList<>();
		LoreComponent l = s.get(DataComponentTypes.LORE);
		if (l != null) for (var t : l.lines()) { if (out.size() >= max) break; out.add(t.getString().replaceAll("§.", "")); }
		return out;
	}

	public static ItemKnowledge knowledgeFor(ItemStack s) {
		if (s.isEmpty()) return null;
		ItemKnowledge k = AIMemory.item(ItemKnowledge.norm(displayName(s)));
		if (k == null) k = AIMemory.item(ItemKnowledge.norm(idPath(s)));
		return k;
	}

	/** known = learned from the user OR an ordinary vanilla item. */
	public static boolean isKnown(ItemStack s) { return knowledgeFor(s) != null || !isCustom(s); }

	public static String customItemsSummary(ClientPlayerEntity p) {
		StringBuilder sb = new StringBuilder();
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getStack(i);
			if (isCustom(s) && seen.add(ItemKnowledge.norm(displayName(s)))) {
				if (sb.length() > 0) sb.append("; ");
				sb.append('"').append(displayName(s)).append('"').append(knowledgeFor(s) == null ? " (unknown)" : " (known)");
				if (sb.length() > 600) break;
			}
		}
		return sb.toString();
	}

	public static boolean onCooldown(ClientPlayerEntity p, ItemStack s) {
		if (s.isEmpty()) return false;
		if (p.getItemCooldownManager().isCoolingDown(s)) return true;
		ItemKnowledge k = knowledgeFor(s);
		return k != null && k.cooldownLeftMs() > 0;
	}

	public static boolean isSplash(ItemStack s) { return s.isOf(Items.SPLASH_POTION) || s.isOf(Items.LINGERING_POTION); }

	private static boolean potionHas(ItemStack s, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> eff) {
		PotionContentsComponent pc = s.get(DataComponentTypes.POTION_CONTENTS);
		if (pc == null) return false;
		for (StatusEffectInstance e : pc.getEffects()) if (e.getEffectType().equals(eff)) return true;
		return false;
	}

	/** Heal score: 0 = not a healing item. Golden apples only count when critical (don't waste them). */
	public static int healScore(ItemStack s, boolean critical) {
		if (s.isEmpty()) return 0;
		ItemKnowledge k = knowledgeFor(s);
		if (k != null && k.type.equalsIgnoreCase("heal")) return 100;
		if (s.isOf(Items.POTION) || isSplash(s)) {
			if (potionHas(s, StatusEffects.INSTANT_HEALTH)) return isSplash(s) ? 95 : 90;
			if (potionHas(s, StatusEffects.REGENERATION)) return 40;
			if (displayName(s).toLowerCase(Locale.ROOT).contains("heal")) return 85;
		}
		if (critical && s.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return 60;
		if (critical && s.isOf(Items.GOLDEN_APPLE)) return 55;
		return 0;
	}

	public static int findHealSlot(ClientPlayerEntity p, boolean critical) {
		int best = -1, bs = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getStack(i);
			int sc = healScore(s, critical);
			if (sc <= 0 || onCooldown(p, s)) continue;
			if (i < 9) sc += 2;
			if (sc > bs) { bs = sc; best = i; }
		}
		return best;
	}

	/** Find a slot by a fuzzy name / id query. */
	public static int find(ClientPlayerEntity p, String query, boolean critical) {
		String q = ItemKnowledge.norm(query);
		if (q.isEmpty()) return -1;
		if (q.contains("heal")) return findHealSlot(p, critical);
		int best = -1, bs = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getStack(i);
			if (s.isEmpty()) continue;
			String name = ItemKnowledge.norm(displayName(s)), id = ItemKnowledge.norm(idPath(s));
			int sc = 0;
			if (name.equals(q) || id.equals(q)) sc = 100;
			else if (name.contains(q)) sc = 80;
			else if (name.length() > 3 && q.contains(name)) sc = 60;
			else if (id.contains(q)) sc = 50;
			if (sc == 0) continue;
			if (i < 9) sc += 5;
			if (onCooldown(p, s)) sc -= 30;
			if (sc > bs) { bs = sc; best = i; }
		}
		return best;
	}

	public static boolean isWeapon(ItemStack s) { return s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES); }

	private static int weaponScore(ItemStack s) {
		if (!isWeapon(s)) return 0;
		String id = idPath(s);
		int base = s.isIn(ItemTags.SWORDS) ? 100 : 80;
		if (id.startsWith("netherite")) base += 50;
		else if (id.startsWith("diamond")) base += 40;
		else if (id.startsWith("iron")) base += 30;
		else if (id.startsWith("stone")) base += 20;
		else if (id.startsWith("golden")) base += 10;
		return base;
	}

	public static int bestWeaponHotbarSlot(ClientPlayerEntity p) {
		int best = -1, bs = 0;
		for (int i = 0; i < 9; i++) {
			int sc = weaponScore(p.getInventory().getStack(i));
			if (sc > bs) { bs = sc; best = i; }
		}
		return best;
	}

	public static boolean hasShield(ClientPlayerEntity p) {
		return p.getOffHandStack().isOf(Items.SHIELD) || p.getMainHandStack().isOf(Items.SHIELD);
	}

	/** For the Items tab. */
	public static List<ItemStack> inventoryStacks(ClientPlayerEntity p) {
		List<ItemStack> l = new ArrayList<>();
		for (int i = 0; i < 36; i++) if (!p.getInventory().getStack(i).isEmpty()) l.add(p.getInventory().getStack(i));
		return l;
	}

	public static boolean criticalNow(ClientPlayerEntity p) {
		return p.getHealth() / p.getMaxHealth() * 100f <= ConfigManager.cfg.criticalThreshold;
	}
}
