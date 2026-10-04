package com.aiplay.ai;

import com.aiplay.ai.AIDecision.Strategy;
import com.aiplay.ai.actions.ActionExecutor;
import com.aiplay.ai.actions.Aim;
import com.aiplay.ai.actions.Action;
import com.aiplay.ai.state.WorldState;
import com.aiplay.config.ConfigManager;
import com.aiplay.config.ModConfig;
import com.aiplay.input.Inputs;
import com.aiplay.items.ItemKnowledgeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Fast local controller: runs EVERY tick, executes the model's strategy without waiting for the API.
 * Priority: (emergency stop / manual override handled in AIManager) -> critical survival -> user rules (heal threshold)
 * -> explicit model actions -> combat strategy.
 */
public final class Controller {
	private static final Random RNG = new Random();
	private static int critWait, healCd, tick, strafeDir = 1, strafeTimer, weaponCd, exploreTurn;
	private static LivingEntity cached;
	private static int cachedAge;
	public static LivingEntity currentTarget;

	private Controller() {}

	public static void reset() { healCd = 0; cached = null; currentTarget = null; ActionExecutor.clearAll(); }

	public static boolean startHeal(MinecraftClient mc, String reason, boolean critical) {
		ClientPlayerEntity p = mc.player;
		int slot = ItemKnowledgeManager.findHealSlot(p, critical);
		if (slot < 0) { healCd = 60; return false; }
		ActionExecutor.clearAll();
		ActionExecutor.enqueue(Action.internal("use_item", "slot", slot));
		ActionExecutor.healBusy = 12;
		healCd = 70;
		AIManager.logAction("HEAL: " + reason);
		return true;
	}

	public static void tick(MinecraftClient mc) {
		ClientPlayerEntity p = mc.player;
		ModConfig cfg = ConfigManager.cfg;
		Inputs.Intent in = new Inputs.Intent();
		tick++;
		if (healCd > 0) healCd--;
		float hp = p.getHealth() / p.getMaxHealth() * 100f;
		LivingEntity t = resolveTarget(mc);
		currentTarget = t;

		// 1) Critical survival, 2) user heal threshold (both beat the model's strategy)
		if (cfg.aiHealing && healCd == 0 && ActionExecutor.healBusy == 0) {
			if (hp <= cfg.criticalThreshold) startHeal(mc, "critical HP " + Math.round(hp) + "%", true);
			else if (hp <= cfg.healThreshold) startHeal(mc, "HP below " + cfg.healThreshold + "% (user rule)", false);
		}

		// 3) combat strategy (movement/aim/attacks)
		strategyTick(mc, p, t, in, cfg);

		// 4) explicit model actions (can override movement for a short time)
		ActionExecutor.tick(mc, in);

		// safety: never walk into lava/void
		if (cfg.avoidHazards && in.fwd && WorldState.unsafeAhead(mc.world, p)) { in.fwd = false; in.sprint = false; }
		if (cfg.autoJump && cfg.aiMovement && in.fwd && p.isOnGround() && WorldState.obstacleAhead(mc.world, p) == 1) in.jump = true;
		if (in.fwd && p.getHungerManager().getFoodLevel() <= 6) in.sprint = false;
		Inputs.apply(mc, in);
	}

	private static boolean valid(ClientPlayerEntity p, Entity e, ModConfig c) {
		if (!(e instanceof LivingEntity le) || e == p || e instanceof ArmorStandEntity || !le.isAlive() || e.isSpectator()) return false;
		if (p.distanceTo(e) > c.searchRadius) return false;
		return (c.targetPlayers && e instanceof PlayerEntity) || (c.targetMobs && e instanceof HostileEntity);
	}

	private static LivingEntity resolveTarget(MinecraftClient mc) {
		ClientPlayerEntity p = mc.player;
		ModConfig cfg = ConfigManager.cfg;
		if (AIManager.noTarget) return null;
		if (AIManager.targetId >= 0) {
			Entity e = mc.world.getEntityById(AIManager.targetId);
			if (e instanceof LivingEntity le && le.isAlive() && le != p && p.distanceTo(e) <= 48) return le;
			if (!cfg.aiTargeting) return null;
		} else if (!cfg.aiTargeting) return null;
		if (cached != null && cached.isAlive() && cachedAge++ < 10 && p.distanceTo(cached) <= cfg.searchRadius) return cached;
		cachedAge = 0;
		LivingEntity best = null;
		double bd = Double.MAX_VALUE;
		for (Entity e : mc.world.getEntities()) {
			if (!valid(p, e, cfg)) continue;
			double d = p.squaredDistanceTo(e);
			if (d < bd) { bd = d; best = (LivingEntity) e; }
		}
		cached = best;
		return best;
	}

	private static double eyeDist(ClientPlayerEntity p, LivingEntity t) {
		Vec3d eye = p.getEyePos();
		var b = t.getBoundingBox();
		return eye.distanceTo(new Vec3d(MathHelper.clamp(eye.x, b.minX, b.maxX), MathHelper.clamp(eye.y, b.minY, b.maxY), MathHelper.clamp(eye.z, b.minZ, b.maxZ)));
	}

	private static void strategyTick(MinecraftClient mc, ClientPlayerEntity p, LivingEntity t, Inputs.Intent in, ModConfig cfg) {
		Strategy s = AIManager.strategy;
		boolean healing = ActionExecutor.healBusy > 0;
		boolean canAim = cfg.aiMovement || cfg.aiCombat;

		if (s == Strategy.EXPLORE && cfg.aiMovement && !healing) { explore(mc, p, in); return; }
		if (t == null || s == Strategy.IDLE) return;

		double dist = eyeDist(p, t);
		Vec3d aimPoint = t.getPos().add(0, t.getHeight() * 0.65, 0);

		if (healing) { // never fight while drinking/eating: back away from the enemy
			if (cfg.aiMovement && dist < 7) {
				if (canAim) Aim.rotate(p, Aim.yawTo(p.getEyePos(), aimPoint) + 180f, p.getPitch(), 30f, 20f);
				in.fwd = true; in.sprint = true;
			}
			return;
		}

		switch (s) {
			case RETREAT, HEAL -> {
				if (!cfg.aiMovement) return;
				boolean turned = Aim.rotate(p, Aim.yawTo(p.getEyePos(), aimPoint) + 180f, 0f, 40f, 30f);
				in.fwd = true; in.sprint = true;
				if (!turned) in.sprint = false;
			}
			case DEFEND -> {
				if (canAim) Aim.face(p, aimPoint);
				if (ItemKnowledgeManager.hasShield(p) && mc.currentScreen == null) in.use = true;
				if (cfg.aiMovement && dist < 3.0) in.back = true;
			}
			case USE_ABILITY -> { if (canAim) Aim.face(p, aimPoint); }
			case ATTACK, CHASE -> {
				boolean aligned = canAim ? Aim.face(p, aimPoint) : true;
				boolean fight = s == Strategy.ATTACK && cfg.aiCombat;
				if (fight) ensureWeapon(p, cfg);
				double stop = Math.max(1.6, cfg.attackRange - 0.7);
				if (cfg.aiMovement) {
					if (dist > stop) in.fwd = true;
					else if (dist < 1.0) in.back = true;
					in.sprint = dist > 3.5 || (in.fwd && !cfg.critHits);
					if (cfg.strafe && dist < 4.5 && dist > 1.3) {
						if (--strafeTimer <= 0) { strafeDir = RNG.nextBoolean() ? 1 : -1; strafeTimer = 10 + RNG.nextInt(25); }
						if (strafeDir > 0) in.right = true; else in.left = true;
					}
				}
				if (fight && !p.isUsingItem() && !ActionExecutor.usingItemNow()) attack(mc, p, t, dist, aligned, in, cfg);
			}
			default -> {}
		}
	}

	private static void attack(MinecraftClient mc, ClientPlayerEntity p, LivingEntity t, double dist, boolean aligned, Inputs.Intent in, ModConfig cfg) {
		if (dist > cfg.attackRange || !aligned || !p.canSee(t)) return;
		boolean ready = p.getAttackCooldownProgress(0.5f) >= 0.92f;
		if (!ready) return;
		boolean crit = cfg.critHits && cfg.aiMovement && !p.isTouchingWater() && !p.isClimbing() && !p.hasVehicle();
		if (crit && critWait++ < 14) {                                   // after 14 ticks fall back to a normal hit
			in.sprint = false;
			if (p.isOnGround()) { in.jump = true; return; }              // jump first, hit while falling
			if (p.getVelocity().y > -0.02 || p.fallDistance <= 0.0f) return;
		}
		critWait = 0;
		mc.interactionManager.attackEntity(p, t);
		p.swingHand(Hand.MAIN_HAND);
	}

	private static void ensureWeapon(ClientPlayerEntity p, ModConfig cfg) {
		if (!cfg.aiInventory || !cfg.autoWeapon || ActionExecutor.busy()) return;
		if (weaponCd > 0) { weaponCd--; return; }
		if (ItemKnowledgeManager.isWeapon(p.getMainHandStack())) return;
		int best = ItemKnowledgeManager.bestWeaponHotbarSlot(p);
		if (best >= 0 && best != p.getInventory().selectedSlot) {
			p.getInventory().selectedSlot = best;
			AIManager.logAction("auto weapon -> slot " + best);
		}
		weaponCd = 20;
	}

	private static void explore(MinecraftClient mc, ClientPlayerEntity p, Inputs.Intent in) {
		int ob = WorldState.obstacleAhead(mc.world, p);
		if (exploreTurn > 0) { p.setYaw(p.getYaw() + 12f * Math.signum(strafeDir)); exploreTurn--; return; }
		if (ob == 2 || WorldState.unsafeAhead(mc.world, p)) { strafeDir = RNG.nextBoolean() ? 1 : -1; exploreTurn = 8 + RNG.nextInt(8); return; }
		in.fwd = true;
		in.sprint = true;
		if (tick % 80 == 0) p.setYaw(p.getYaw() + (RNG.nextFloat() - 0.5f) * 40f);
	}
}
