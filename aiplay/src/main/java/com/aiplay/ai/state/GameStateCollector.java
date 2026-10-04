package com.aiplay.ai.state;

import com.aiplay.ai.AIManager;
import com.aiplay.config.ConfigManager;
import com.aiplay.config.ModConfig;
import com.aiplay.items.ItemKnowledge;
import com.aiplay.items.ItemKnowledgeManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds the compact JSON game state sent to the AI: player, inventory, nearby entities, world. */
public final class GameStateCollector {
	private GameStateCollector() {}

	private static double r1(double v) { return Math.round(v * 10.0) / 10.0; }

	public static float yawTo(Vec3d from, Vec3d to) {
		return (float) (Math.atan2(to.z - from.z, to.x - from.x) * 180.0 / Math.PI) - 90f;
	}

	public static JsonObject collect(MinecraftClient c) {
		ClientPlayerEntity p = c.player;
		ModConfig cfg = ConfigManager.cfg;
		JsonObject o = new JsonObject();
		o.add("player", player(p));
		o.add("inventory", inventory(p));
		o.add("nearby_entities", entities(c));
		o.add("world", WorldState.collect(c));
		JsonObject mods = new JsonObject();
		mods.addProperty("combat", cfg.aiCombat);
		mods.addProperty("movement", cfg.aiMovement);
		mods.addProperty("inventory", cfg.aiInventory);
		mods.addProperty("item_usage", cfg.aiItemUsage);
		mods.addProperty("healing", cfg.aiHealing);
		mods.addProperty("targeting", cfg.aiTargeting);
		o.add("enabled_modules", mods);
		o.addProperty("attack_range", cfg.attackRange);
		o.addProperty("current_strategy", AIManager.strategy.name().toLowerCase());
		JsonArray recent = new JsonArray();
		AIManager.recentActions().forEach(recent::add);
		o.add("recent_actions", recent);
		return o;
	}

	public static JsonObject player(ClientPlayerEntity p) {
		JsonObject o = new JsonObject();
		o.addProperty("hp", r1(p.getHealth()));
		o.addProperty("max_hp", r1(p.getMaxHealth()));
		o.addProperty("hp_percent", Math.round(p.getHealth() / p.getMaxHealth() * 100f));
		o.addProperty("absorption", r1(p.getAbsorptionAmount()));
		o.addProperty("armor", p.getArmor());
		o.addProperty("hunger", p.getHungerManager().getFoodLevel());
		o.addProperty("saturation", r1(p.getHungerManager().getSaturationLevel()));
		JsonArray ef = new JsonArray();
		for (StatusEffectInstance e : p.getStatusEffects())
			ef.add(e.getTranslationKey().replace("effect.minecraft.", "") + " " + (e.getAmplifier() + 1) + " (" + e.getDuration() / 20 + "s)");
		o.add("effects", ef);
		o.add("pos", vec(p.getPos()));
		o.addProperty("yaw", r1(MathHelper.wrapDegrees(p.getYaw())));
		o.addProperty("pitch", r1(p.getPitch()));
		o.addProperty("facing", p.getHorizontalFacing().asString());
		o.addProperty("speed", r1(p.getVelocity().horizontalLength() * 20));
		o.addProperty("on_ground", p.isOnGround());
		o.addProperty("sprinting", p.isSprinting());
		o.addProperty("sneaking", p.isSneaking());
		o.addProperty("using_item", p.isUsingItem());
		o.addProperty("attack_ready", p.getAttackCooldownProgress(0.5f) >= 0.92f);
		return o;
	}

	private static JsonArray vec(Vec3d v) {
		JsonArray a = new JsonArray();
		a.add(r1(v.x)); a.add(r1(v.y)); a.add(r1(v.z));
		return a;
	}

	public static JsonObject inventory(ClientPlayerEntity p) {
		JsonObject o = new JsonObject();
		o.addProperty("selected_slot", p.getInventory().selectedSlot);
		o.addProperty("note", "slot 0-8 = hotbar (0 leftmost), 9-35 = main inventory");
		JsonArray items = new JsonArray();
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getStack(i);
			if (s.isEmpty()) continue;
			items.add(stack(p, s, i));
		}
		o.add("items", items);
		JsonObject armor = new JsonObject();
		armor.addProperty("head", ItemKnowledgeManager.displayName(p.getEquippedStack(EquipmentSlot.HEAD)));
		armor.addProperty("chest", ItemKnowledgeManager.displayName(p.getEquippedStack(EquipmentSlot.CHEST)));
		armor.addProperty("legs", ItemKnowledgeManager.displayName(p.getEquippedStack(EquipmentSlot.LEGS)));
		armor.addProperty("feet", ItemKnowledgeManager.displayName(p.getEquippedStack(EquipmentSlot.FEET)));
		o.add("armor", armor);
		ItemStack off = p.getOffHandStack();
		o.addProperty("offhand", off.isEmpty() ? "empty" : ItemKnowledgeManager.displayName(off) + " x" + off.getCount());
		return o;
	}

	private static JsonObject stack(ClientPlayerEntity p, ItemStack s, int slot) {
		JsonObject it = new JsonObject();
		it.addProperty("slot", slot);
		it.addProperty("name", ItemKnowledgeManager.displayName(s));
		it.addProperty("id", ItemKnowledgeManager.idPath(s));
		it.addProperty("count", s.getCount());
		if (s.isDamageable()) it.addProperty("durability", s.getMaxDamage() - s.getDamage());
		boolean custom = ItemKnowledgeManager.isCustom(s);
		ItemKnowledge k = ItemKnowledgeManager.knowledgeFor(s);
		it.addProperty("known", k != null || !custom);
		if (custom) {
			it.addProperty("custom", true);
			List<String> lore = ItemKnowledgeManager.lore(s, 3);
			if (!lore.isEmpty()) it.addProperty("lore", String.join(" | ", lore));
		}
		if (k != null) it.addProperty("taught", k.summary());
		boolean cd = ItemKnowledgeManager.onCooldown(p, s);
		if (cd) {
			it.addProperty("on_cooldown", true);
			if (k != null && k.cooldownLeftMs() > 0) it.addProperty("cooldown_left_s", r1(k.cooldownLeftMs() / 1000.0));
		}
		return it;
	}

	public static JsonArray entities(MinecraftClient c) {
		ClientPlayerEntity p = c.player;
		List<LivingEntity> list = new ArrayList<>();
		for (Entity e : c.world.getEntities()) {
			if (e == p || !(e instanceof LivingEntity le) || e instanceof ArmorStandEntity || !le.isAlive()) continue;
			if (p.distanceTo(e) <= 24) list.add(le);
		}
		list.sort(Comparator.comparingDouble(p::distanceTo));
		JsonArray arr = new JsonArray();
		for (LivingEntity e : list) {
			if (arr.size() >= 8) break;
			JsonObject o = new JsonObject();
			o.addProperty("id", e.getId());
			o.addProperty("type", EntityType.getId(e.getType()).getPath());
			o.addProperty("name", e.getName().getString());
			o.addProperty("is_player", e instanceof PlayerEntity);
			o.addProperty("hostile", e instanceof HostileEntity);
			o.addProperty("distance", r1(p.distanceTo(e)));
			o.addProperty("hp", r1(e.getHealth()));
			o.addProperty("max_hp", r1(e.getMaxHealth()));
			Vec3d to = e.getPos();
			o.addProperty("relative_yaw", Math.round(MathHelper.wrapDegrees(yawTo(p.getPos(), to) - p.getYaw())));
			o.add("pos", vec(to));
			o.addProperty("visible", p.canSee(e));
			Vec3d delta = to.subtract(e.prevX, e.prevY, e.prevZ);
			Vec3d dir = to.subtract(p.getPos()).multiply(1, 0, 1);
			boolean away = dir.lengthSquared() > 0.01 && delta.multiply(1, 0, 1).dotProduct(dir.normalize()) > 0.08;
			o.addProperty("moving_away", away);
			o.addProperty("hurt", e.hurtTime > 0);
			ItemStack held = e.getMainHandStack();
			if (!held.isEmpty()) o.addProperty("holding", ItemKnowledgeManager.displayName(held));
			arr.add(o);
		}
		return arr;
	}
}
