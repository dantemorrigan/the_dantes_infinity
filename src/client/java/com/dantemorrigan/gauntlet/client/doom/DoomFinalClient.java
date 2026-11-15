package com.dantemorrigan.gauntlet.client.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.client.fx.ClientPower;
import com.dantemorrigan.gauntlet.doom.DoomFinalEncounter;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Client visuals, camera shots, 3D Energy Dome, Stormbreaker Dome-Break HUD button/tooltip, and Flying Gauntlet
 * animation for the Task 4 Doom & Thanos Final Storyline.
 */
public final class DoomFinalClient {
	public static final KeyMapping BREAK_DOME_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.infinity_gauntlet.break_dome", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, ClientPower.CATEGORY));

	private static final Identifier WHITE_TEX = InfinityGauntlet.id("textures/gui/doctor_doom/state_active.png");
	private static final Identifier FROSTED_TEX = InfinityGauntlet.id("textures/fx/doom/dome_frosted.png");
	private static final Identifier GLOW_TEX = InfinityGauntlet.id("textures/fx/doom/glow.png");
	private static final ItemStackRenderState FLYING_GAUNTLET_STATE = new ItemStackRenderState();

	private static int stage = DoomFinalEncounter.STAGE_NONE;
	private static int localTicks = 0;
	private static Vec3 center = Vec3.ZERO;
	private static int doomId = -1;
	private static int thanosId = -1;
	private static int stoneMask = 0x1F;
	private static Vec3 lastDoomPos = Vec3.ZERO;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.DoomFinalSync.TYPE, (pkt, ctx) -> {
			if (stage != pkt.stage()) {
				localTicks = pkt.ticks();
			} else if (Math.abs(localTicks - pkt.ticks()) > 8) {
				localTicks = pkt.ticks();
			}
			stage = pkt.stage();
			center = pkt.center();
			doomId = pkt.doomId();
			thanosId = pkt.thanosId();
			stoneMask = pkt.stoneMask();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(DoomFinalClient::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(DoomFinalClient::renderWorld);
		HudElementRegistry.addLast(InfinityGauntlet.id("doom_final_hud"), (g, delta) -> renderHud(g));
	}

	private static void reset() {
		stage = DoomFinalEncounter.STAGE_NONE;
		localTicks = 0;
		center = Vec3.ZERO;
		doomId = -1;
		thanosId = -1;
		lastDoomPos = Vec3.ZERO;
	}

	/** Overworld coordinates for the QuestCompassHud while the Sandy-Fiery Field encounter is active. */
	public static Vec3 compassSite() {
		if (stage >= DoomFinalEncounter.STAGE_WAITING_OVERWORLD && stage <= DoomFinalEncounter.STAGE_FINAL_BOSS && center.lengthSqr() > 1) {
			return center;
		}
		return null;
	}

	/** The Gauntlet fight on the battlefield is on (after the cutscenes, until Doom falls): its own soundtrack plays. */
	public static boolean gauntletFight() { return stage == DoomFinalEncounter.STAGE_FINAL_BOSS; }

	public static boolean inCutscene() {
		return stage == DoomFinalEncounter.STAGE_THEFT_CUTSCENE
				|| stage == DoomFinalEncounter.STAGE_THANOS_ARRIVAL
				|| stage == DoomFinalEncounter.STAGE_KILL_THANOS_CUTSCENE
				|| stage == DoomFinalEncounter.STAGE_VICTORY_CUTSCENE;
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.isPaused()) return;
		if (stage != DoomFinalEncounter.STAGE_NONE && stage != DoomFinalEncounter.STAGE_DONE) {
			localTicks++;
		}
		Entity d = doomId >= 0 ? mc.level.getEntity(doomId) : null;
		if (d != null) lastDoomPos = d.position();

		while (BREAK_DOME_KEY.consumeClick()) {
			if (stage == DoomFinalEncounter.STAGE_DOME_DUEL
					&& holdsStormbreakerClient(mc)
					&& mc.player.position().distanceTo(center) <= DoomFinalEncounter.DOME_RADIUS + 8.5) {
				if (ClientPlayNetworking.canSend(ModNetwork.DoomBreakDome.TYPE)) {
					ClientPlayNetworking.send(new ModNetwork.DoomBreakDome());
					mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				}
			}
		}

		// Spawn trail particles along the flying Gauntlet during Victory Cutscene (ticks 58..114)
		if (stage == DoomFinalEncounter.STAGE_VICTORY_CUTSCENE && localTicks >= 58 && localTicks <= 114) {
			float k = Mth.clamp((localTicks - 58) / 56f, 0, 1);
			Vec3 gPos = gauntletFlightPos(mc, k, 0f);
			mc.level.addParticle(new DustParticleOptions(0x28E066, 1.3f), gPos.x, gPos.y, gPos.z, 0, 0.02, 0);
			mc.level.addParticle(new DustParticleOptions(0xFFD866, 1.2f), gPos.x, gPos.y, gPos.z, 0, 0.01, 0);
			mc.level.addParticle(ParticleTypes.END_ROD, gPos.x, gPos.y, gPos.z, 0, 0.01, 0);
		}
	}

	private static boolean holdsStormbreakerClient(Minecraft mc) {
		if (mc.player == null) return false;
		return mc.player.getMainHandItem().is(ModItems.STORMBREAKER) || mc.player.getOffhandItem().is(ModItems.STORMBREAKER);
	}

	/** Camera override for the 4 cutscenes: returns {cameraPos, lookAtPos} or null when player view is active. */
	public static Vec3[] shot(float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return null;
		float t = localTicks + partial;

		if (stage == DoomFinalEncounter.STAGE_THEFT_CUTSCENE && mc.level.dimension() == com.dantemorrigan.gauntlet.doom.Latveria.LEVEL) {
			Entity d = doomId >= 0 ? mc.level.getEntity(doomId) : null;
			Vec3 base = d != null ? d.getPosition(partial) : center;
			double angle = -0.5 + (t / 140.0) * 1.1;
			double dist = 5.8;
			Vec3 cam = base.add(Math.sin(angle) * dist, 2.2, Math.cos(angle) * dist);
			Vec3 look = base.add(0, 1.7, 0);
			return new Vec3[]{cam, look};
		}

		if (stage == DoomFinalEncounter.STAGE_THANOS_ARRIVAL && mc.level.dimension() == Level.OVERWORLD) {
			if (mc.player.position().distanceTo(center) > 96) return null;
			if (t < 55) {
				// Looking past Doom up toward Sanctuary II arriving in the sky
				Vec3 cam = center.add(-12, 3.2, 7);
				Vec3 look = center.add(16, 26 - t * 0.25, 0);
				return new Vec3[]{cam, look};
			} else if (t < 112) {
				// Tracking Thanos descending in the blue tractor beam
				float k = (t - 55) / 57f;
				Vec3 cam = center.add(-2, 4.0, 15);
				Vec3 look = center.add(5.5, 20 * (1 - k) + 1.5, 0);
				return new Vec3[]{cam, look};
			} else {
				// Dramatic wide two-shot as the Energy Dome rises over Doom and Thanos
				double a = (t - 112) * 0.012;
				Vec3 cam = center.add(Math.sin(a) * 24, 6.5, Math.cos(a) * 24);
				Vec3 look = center.add(0, 2.5, 0);
				return new Vec3[]{cam, look};
			}
		}

		if (stage == DoomFinalEncounter.STAGE_KILL_THANOS_CUTSCENE && mc.level.dimension() == Level.OVERWORLD) {
			if (mc.player.position().distanceTo(center) > 96) return null;
			if (t < 28) {
				// Watching the dome shatter from Stormbreaker's lightning
				Vec3 cam = center.add(0, 7.5, 25);
				Vec3 look = center.add(0, 6.0, 0);
				return new Vec3[]{cam, look};
			} else if (t < 100) {
				// Orbiting Doom executing Thanos with the stolen Gauntlet
				double a = (t - 28) * 0.02;
				Vec3 cam = center.add(Math.sin(a) * 10, 3.2, Math.cos(a) * 10);
				Vec3 look = center.add(1.0, 1.6, 0);
				return new Vec3[]{cam, look};
			} else if (t < 155) {
				// Looking up as Doom blasts Sanctuary II out of the sky and it crashes
				Vec3 cam = center.add(-9, 2.5, 8);
				Vec3 look = center.add(28, 48 - (t - 100) * 0.55, 10);
				return new Vec3[]{cam, look};
			} else {
				// Close-up on Gauntlet Doom turning to challenge the player
				Entity d = doomId >= 0 ? mc.level.getEntity(doomId) : null;
				Vec3 base = d != null ? d.getPosition(partial) : center;
				Vec3 cam = base.add(0, 2.2, 5.5);
				Vec3 look = base.add(0, 1.8, 0);
				return new Vec3[]{cam, look};
			}
		}

		if (stage == DoomFinalEncounter.STAGE_VICTORY_CUTSCENE && mc.level.dimension() == Level.OVERWORLD) {
			if (mc.player.position().distanceTo(center) > 96) return null;
			Vec3 dPos = lastDoomPos.lengthSqr() > 1 ? lastDoomPos : center;
			if (t < 60) {
				// Orbiting kneeling Doom as he dissolves into green dust
				double a = t * 0.025;
				Vec3 cam = dPos.add(Math.sin(a) * 5.2, 2.0, Math.cos(a) * 5.2);
				Vec3 look = dPos.add(0, 1.2, 0);
				return new Vec3[]{cam, look};
			} else {
				// Tracking the Infinity Gauntlet as it flies to the player's left hand!
				float k = Mth.clamp((t - 58) / 56f, 0, 1);
				Vec3 gPos = gauntletFlightPos(mc, k, partial);
				Vec3 pPos = mc.player.getPosition(partial);
				Vec3 side = new Vec3(-(pPos.z - dPos.z), 0, pPos.x - dPos.x);
				side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
				Vec3 cam = gPos.add(side.scale(3.4)).add(0, 1.1, 0);
				return new Vec3[]{cam, gPos};
			}
		}

		return null;
	}

	private static Vec3 gauntletFlightPos(Minecraft mc, float k, float partial) {
		float smooth = k * k * (3 - 2 * k);
		Vec3 from = (lastDoomPos.lengthSqr() > 1 ? lastDoomPos : center).add(0, 1.35, 0);
		Vec3 pPos = mc.player.getPosition(partial);
		Vec3 look = mc.player.getViewVector(partial);
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		// Player's left hand position
		Vec3 to = pPos.add(0, 1.15, 0).subtract(right.scale(0.42)).add(look.scale(0.35));
		return from.lerp(to, smooth).add(0, Math.sin(smooth * Math.PI) * 2.2, 0);
	}

	private static void renderWorld(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack pose = ctx.poseStack();
		float t = localTicks + partial;

		// 1. Render the Energy Dome around Doom & Thanos during the duel
		boolean showDome = (stage == DoomFinalEncounter.STAGE_THANOS_ARRIVAL && t >= 150)
				|| stage == DoomFinalEncounter.STAGE_DOME_DUEL
				|| (stage == DoomFinalEncounter.STAGE_KILL_THANOS_CUTSCENE && t < 18);
		if (showDome && mc.level.dimension() == Level.OVERWORLD && cam.distanceTo(center) < 180) {
			float scale = 1.0f;
			float alpha = 1.0f;
			if (stage == DoomFinalEncounter.STAGE_THANOS_ARRIVAL) {
				scale = Mth.clamp((t - 150) / 16f, 0.05f, 1.0f);
				alpha = scale;
			} else if (stage == DoomFinalEncounter.STAGE_KILL_THANOS_CUTSCENE) {
				alpha = Mth.clamp(1.0f - t / 18f, 0f, 1f);
				scale = 1.0f + (1.0f - alpha) * 0.12f;
			}
			renderEnergyDome(ctx, pose, cam, center, (float) DoomFinalEncounter.DOME_RADIUS * scale, alpha, t);
		}

		// 2. Render the Flying Infinity Gauntlet during Theft (t < 24) or Victory Return (t = 58..115)
		if (stage == DoomFinalEncounter.STAGE_THEFT_CUTSCENE && t < 24 && mc.level.dimension() == com.dantemorrigan.gauntlet.doom.Latveria.LEVEL) {
			Entity d = doomId >= 0 ? mc.level.getEntity(doomId) : null;
			if (d != null) {
				float k = Mth.clamp(t / 24f, 0, 1);
				Vec3 from = mc.player.getPosition(partial).add(0, 1.2, 0);
				Vec3 to = d.getPosition(partial).add(0, 1.7, 0);
				Vec3 pos = from.lerp(to, k).add(0, Math.sin(k * Math.PI) * 1.4, 0);
				renderFlyingGauntlet(ctx, mc, pose, cam, pos, t * 14f);
			}
		} else if (stage == DoomFinalEncounter.STAGE_VICTORY_CUTSCENE && t >= 58 && t <= 115 && mc.level.dimension() == Level.OVERWORLD) {
			float k = Mth.clamp((t - 58) / 56f, 0, 1);
			Vec3 pos = gauntletFlightPos(mc, k, partial);
			renderFlyingGauntlet(ctx, mc, pose, cam, pos, t * 12f);
		}
	}

	private static void renderFlyingGauntlet(LevelRenderContext ctx, Minecraft mc, PoseStack pose, Vec3 cam, Vec3 pos, float spinDeg) {
		ItemStack stack = DoomFinalEncounter.buildGauntletFromMask(stoneMask);
		stack.set(DataComponents.ITEM_MODEL, InfinityGauntlet.id("gauntlet_worn"));
		mc.getItemModelResolver().updateForTopItem(FLYING_GAUNTLET_STATE, stack, ItemDisplayContext.NONE, mc.level, null, 0);
		pose.pushPose();
		pose.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(spinDeg));
		pose.mulPose(Axis.ZP.rotationDegrees(20f));
		pose.scale(1.35f, 1.35f, 1.35f);
		FLYING_GAUNTLET_STATE.submit(pose, ctx.submitNodeCollector(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	private static void renderEnergyDome(LevelRenderContext ctx, PoseStack pose, Vec3 cam, Vec3 c, float radius, float alpha, float time) {
		Minecraft mc = Minecraft.getInstance();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		pose.pushPose();
		pose.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
		int segsYaw = 32;
		int segsPitch = 12;
		float pulse = 0.92f + 0.08f * Mth.sin(time * 0.08f);

		// 1. Silhouette-blurring diffusion halos around Doom & Thanos inside the dome so their figures look
		// blurred and diffused as through thick frosted glass.
		Vec3 camRel = cam.subtract(c);
		Vec3 toCamHoriz = new Vec3(camRel.x, 0, camRel.z);
		Vec3 right = toCamHoriz.lengthSqr() > 1e-4
				? new Vec3(-toCamHoriz.z, 0, toCamHoriz.x).normalize()
				: new Vec3(1, 0, 0);
		Vec3 forward = toCamHoriz.lengthSqr() > 1e-4 ? toCamHoriz.normalize() : new Vec3(0, 0, 1);

		Entity doomEnt = (mc.level != null && doomId >= 0) ? mc.level.getEntity(doomId) : null;
		Entity thanosEnt = (mc.level != null && thanosId >= 0) ? mc.level.getEntity(thanosId) : null;
		Vec3 doomRel = doomEnt != null ? doomEnt.getPosition(partial).subtract(c) : new Vec3(-2.5, 0, 0);
		Vec3 thanosRel = thanosEnt != null ? thanosEnt.getPosition(partial).subtract(c) : new Vec3(2.5, 0, 0);

		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucent(GLOW_TEX), (p, buf) -> {
			// Blurred dark-jade silhouette aura around Doom
			drawSilhouetteBlur(buf, p, doomRel, right, forward, 1.35f, 2.4f,
					ARGB.color((int) (185 * alpha), 18, 42, 30),
					ARGB.color((int) (145 * alpha), 95, 175, 135));
			// Blurred dark-titan silhouette aura around Thanos
			if (thanosEnt != null || stage != DoomFinalEncounter.STAGE_KILL_THANOS_CUTSCENE) {
				drawSilhouetteBlur(buf, p, thanosRel, right, forward, 1.75f, 3.1f,
						ARGB.color((int) (190 * alpha), 32, 24, 48),
						ARGB.color((int) (150 * alpha), 130, 145, 175));
			}
			// Ambient cloudy interior mist planes so the entire interior behind the glass is milky/diffused
			for (int m = -2; m <= 2; m++) {
				Vec3 mistCenter = forward.scale(m * 1.8).add(0, 2.2, 0);
				int mistCol = ARGB.color((int) (78 * alpha), 155, 215, 190);
				drawBillboard(buf, p, mistCenter, right, 6.5f, 4.2f, mistCol);
			}
		});

		// 2. Three concentric frosted-glass hemisphere shells with counter-drifting cellular frost UVs
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucent(FROSTED_TEX), (p, buf) -> {
			float[] radii = {radius * 0.94f, radius * 0.972f, radius};
			int[] layerAlphas = {
					(int) (115 * alpha * pulse),
					(int) (130 * alpha * pulse),
					(int) (148 * alpha * pulse)
			};
			float[] uvShifts = {time * 0.0018f, -time * 0.0014f, time * 0.0009f};

			for (int layer = 0; layer < radii.length; layer++) {
				float rLayer = radii[layer];
				int aLayer = layerAlphas[layer];
				float shift = uvShifts[layer];

				for (int i = 0; i < segsPitch; i++) {
					float p0 = (i / (float) segsPitch) * Mth.HALF_PI;
					float p1 = ((i + 1) / (float) segsPitch) * Mth.HALF_PI;
					float r0 = Mth.cos(p0) * rLayer, y0 = Mth.sin(p0) * rLayer;
					float r1 = Mth.cos(p1) * rLayer, y1 = Mth.sin(p1) * rLayer;
					float v0 = (i / (float) segsPitch) * 2.0f + shift * 0.5f;
					float v1 = ((i + 1) / (float) segsPitch) * 2.0f + shift * 0.5f;

					int col = (layer == 1)
							? ARGB.color(aLayer, 185, 238, 218)
							: (i % 2 == 0)
									? ARGB.color(aLayer, 150, 222, 195)
									: ARGB.color(aLayer, 168, 210, 232);

					for (int j = 0; j < segsYaw; j++) {
						float a0 = (j / (float) segsYaw) * Mth.TWO_PI + shift;
						float a1 = ((j + 1) / (float) segsYaw) * Mth.TWO_PI + shift;
						float u0 = (j / (float) segsYaw) * 4.0f + shift;
						float u1 = ((j + 1) / (float) segsYaw) * 4.0f + shift;

						float x00 = Mth.cos(a0) * r0, z00 = Mth.sin(a0) * r0;
						float x10 = Mth.cos(a1) * r0, z10 = Mth.sin(a1) * r0;
						float x11 = Mth.cos(a1) * r1, z11 = Mth.sin(a1) * r1;
						float x01 = Mth.cos(a0) * r1, z01 = Mth.sin(a0) * r1;

						// Outer frosted glass face
						vtx(buf, p, x00, y0, z00, u0, v0, col);
						vtx(buf, p, x10, y0, z10, u1, v0, col);
						vtx(buf, p, x11, y1, z11, u1, v1, col);
						vtx(buf, p, x01, y1, z01, u0, v1, col);

						// Inner frosted glass face
						vtx(buf, p, x01, y1, z01, u0, v1, col);
						vtx(buf, p, x11, y1, z11, u1, v1, col);
						vtx(buf, p, x10, y0, z10, u1, v0, col);
						vtx(buf, p, x00, y0, z00, u0, v0, col);
					}
				}
			}

			// Subtle frosted glass ribs along latitude & longitude
			int ribCol = ARGB.color((int) (135 * alpha), 195, 250, 225);
			float rh = 0.11f;
			for (int i = 1; i < segsPitch; i += 2) {
				float p0 = (i / (float) segsPitch) * Mth.HALF_PI;
				float r0 = Mth.cos(p0) * (radius + 0.03f), y0 = Mth.sin(p0) * (radius + 0.03f);
				for (int j = 0; j < segsYaw; j++) {
					float a0 = (j / (float) segsYaw) * Mth.TWO_PI;
					float a1 = ((j + 1) / (float) segsYaw) * Mth.TWO_PI;
					float x0 = Mth.cos(a0) * r0, z0 = Mth.sin(a0) * r0;
					float x1 = Mth.cos(a1) * r0, z1 = Mth.sin(a1) * r0;
					vtx(buf, p, x0, y0, z0, 0, 1, ribCol);
					vtx(buf, p, x1, y0, z1, 1, 1, ribCol);
					vtx(buf, p, x1, y0 + rh, z1, 1, 0, ribCol);
					vtx(buf, p, x0, y0 + rh, z0, 0, 0, ribCol);
				}
			}
		});
		pose.popPose();
	}

	private static void drawSilhouetteBlur(VertexConsumer buf, PoseStack.Pose p, Vec3 relPos, Vec3 right, Vec3 forward,
			float halfW, float height, int coreCol, int haloCol) {
		// Multi-sample offset billboards in front of and around the character to blur their silhouette
		float[][] offsets = {
				{0f, 0f, 0.55f, 1.0f},
				{-0.28f, 0.12f, 0.75f, 1.18f},
				{0.28f, -0.10f, 0.75f, 1.18f},
				{0f, 0.22f, 0.95f, 1.35f},
				{0f, -0.15f, -0.35f, 1.25f}
		};
		for (int i = 0; i < offsets.length; i++) {
			float[] o = offsets[i];
			Vec3 at = relPos.add(right.scale(o[0])).add(forward.scale(o[2])).add(0, height * 0.5f + o[1], 0);
			int col = (i == 0) ? coreCol : haloCol;
			drawBillboard(buf, p, at, right, halfW * o[3], height * 0.55f * o[3], col);
		}
	}

	private static void drawBillboard(VertexConsumer buf, PoseStack.Pose p, Vec3 center, Vec3 right, float hw, float hh, int col) {
		float rx = (float) (right.x * hw), rz = (float) (right.z * hw);
		float x = (float) center.x, y = (float) center.y, z = (float) center.z;
		vtx(buf, p, x - rx, y - hh, z - rz, 0, 1, col);
		vtx(buf, p, x + rx, y - hh, z + rz, 1, 1, col);
		vtx(buf, p, x + rx, y + hh, z + rz, 1, 0, col);
		vtx(buf, p, x - rx, y + hh, z - rz, 0, 0, col);
		vtx(buf, p, x - rx, y + hh, z - rz, 0, 0, col);
		vtx(buf, p, x + rx, y + hh, z + rz, 1, 0, col);
		vtx(buf, p, x + rx, y - hh, z + rz, 1, 1, col);
		vtx(buf, p, x - rx, y - hh, z - rz, 0, 1, col);
	}

	private static void vtx(VertexConsumer b, PoseStack.Pose p, float x, float y, float z, float u, float v, int col) {
		b.addVertex(p, x, y, z).setColor(col).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0, 1, 0);
	}

	private static void renderHud(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui) return;
		if (stage != DoomFinalEncounter.STAGE_DOME_DUEL || mc.level.dimension() != Level.OVERWORLD) return;

		double dist = mc.player.position().distanceTo(center);
		if (dist > DoomFinalEncounter.DOME_RADIUS + 8.5) return;

		int w = g.guiWidth(), h = g.guiHeight();
		boolean hasAxe = holdsStormbreakerClient(mc);
		int maxTextW = Math.min(w - 32, 340);

		if (hasAxe) {
			Component btn = Component.translatable("hud.infinity_gauntlet.dome.break_button",
					BREAK_DOME_KEY.getTranslatedKeyMessage()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
			Component tip = Component.translatable("hud.infinity_gauntlet.dome.break_tooltip").withStyle(ChatFormatting.GRAY);
			var btnLines = mc.font.split(btn, maxTextW);
			var tipLines = mc.font.split(tip, maxTextW);
			int contentW = 200;
			for (var l : btnLines) contentW = Math.max(contentW, mc.font.width(l));
			for (var l : tipLines) contentW = Math.max(contentW, mc.font.width(l));
			int boxW = contentW + 24;
			int boxH = 12 + (btnLines.size() + tipLines.size()) * 11 + 4;
			int bx = (w - boxW) / 2;
			int by = h - 68 - boxH;

			float pulse = 0.7f + 0.3f * Mth.sin((localTicks) * 0.2f);
			int borderCol = ARGB.color((int) (220 * pulse), 95, 225, 255);
			g.fill(bx - 2, by - 2, bx + boxW + 2, by + boxH + 2, borderCol);
			g.fill(bx, by, bx + boxW, by + boxH, 0xDF0B1418);

			int ty = by + 6;
			for (var l : btnLines) {
				g.centeredText(mc.font, l, w / 2, ty, 0xFF70F4FF);
				ty += 11;
			}
			ty += 2;
			for (var l : tipLines) {
				g.centeredText(mc.font, l, w / 2, ty, 0xFFD8E8F0);
				ty += 11;
			}
		} else {
			Component need = Component.translatable("hud.infinity_gauntlet.dome.need_stormbreaker").withStyle(ChatFormatting.GOLD);
			var needLines = mc.font.split(need, maxTextW);
			int contentW = 200;
			for (var l : needLines) contentW = Math.max(contentW, mc.font.width(l));
			int boxW = contentW + 24;
			int boxH = 12 + needLines.size() * 11;
			int bx = (w - boxW) / 2;
			int by = h - 68 - boxH;

			int borderCol = ARGB.color(200, 255, 175, 70);
			g.fill(bx - 2, by - 2, bx + boxW + 2, by + boxH + 2, borderCol);
			g.fill(bx, by, bx + boxW, by + boxH, 0xDF0B1418);

			int ty = by + 6;
			for (var l : needLines) {
				g.centeredText(mc.font, l, w / 2, ty, 0xFFFFC864);
				ty += 11;
			}
		}
	}

	private DoomFinalClient() {}
}
