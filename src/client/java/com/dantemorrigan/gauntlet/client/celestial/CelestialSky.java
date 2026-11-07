package com.dantemorrigan.gauntlet.client.celestial;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.celestial.CelestialApocalypse;
import com.dantemorrigan.gauntlet.client.fx.VfxPresets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.MoonPhase;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drawn INSIDE the vanilla sky pass (see SkyRendererMixin), so there is no world fog and the order is right:
 * red cube-map first, then the real sun/moon (ours replaces the targeted one while it breaks), then stars.
 *
 * <p>The broken body uses the ORIGINAL texture from the active resource pack (sun.png / moon phase PNG) with the
 * pack's crack overlay composited in the same UVs, clipped by base alpha AND brightness (black sky pixels can have
 * alpha 255). Same transform as vanilla: rotY(-90) rotX(angle), quad at y=100, half-size 30 (sun) / 20 (moon).
 */
public final class CelestialSky {
	private static final String[] FACES = {"positive_x", "negative_x", "positive_y", "negative_y", "positive_z", "negative_z"};
	private static final String[] KEYS = {"+X", "-X", "+Y", "-Y", "+Z", "-Z"};
	private static final Map<Identifier, RenderType> TYPES = new HashMap<>();
	private static final Map<String, Identifier> COMPOSITES = new HashMap<>();
	private static String compositeKey = "";
	private static List<Fragment> fragments;
	private static JsonObject skyOrientation;

	private record Fragment(float u0, float v0, float u1, float v1, float cx, float cy, Vector3f out, Vector3f tumble) {}

	public static void reset() {
		COMPOSITES.values().forEach(id -> Minecraft.getInstance().getTextureManager().release(id));
		COMPOSITES.clear();
		compositeKey = "";
	}

	private static RenderType type(Identifier texture) {
		return TYPES.computeIfAbsent(texture, t -> RenderType.create("infinity_gauntlet_sky",
				RenderSetup.builder(RenderPipelines.END_SKY).withTexture("Sampler0", t).createRenderSetup()));
	}

	// ------------------------------------------------------------------ red sky

	/** Red cube-map (day/night blended), alpha = event influence at the camera. Called before sun/moon/stars. */
	public static void drawRedSky(PoseStack pose, float partial) {
		Minecraft mc = Minecraft.getInstance();
		float f = CelestialClient.redFactor(mc.gameRenderer.getMainCamera().position(), partial);
		if (f <= 0.003f) return;
		float day = CelestialClient.daylight(partial);
		drawCube(pose, "day", 0.9f * f * day);
		drawCube(pose, "night", 0.9f * f * (1 - day));
	}

	private static void drawCube(PoseStack pose, String variant, float alpha) {
		if (alpha <= 0.003f) return;
		if (skyOrientation == null) {
			skyOrientation = VfxPresets.readJson(InfinityGauntlet.id("celestial_apocalypse/sky_orientation.json"));
			if (skyOrientation == null) skyOrientation = new JsonObject();
		}
		JsonObject map = skyOrientation.has("mapping") ? skyOrientation.getAsJsonObject("mapping") : null;
		float r = 150f; // beyond the sun/moon quads (y=100) so they stay in front
		float inset = 0.5f / 512f;
		int color = ARGB.colorFromFloat(Math.min(1f, alpha), 1f, 1f, 1f);
		for (int f = 0; f < 6; f++) {
			JsonArray spec = map != null && map.has(KEYS[f]) ? map.getAsJsonArray(KEYS[f]) : null;
			if (spec == null) continue;
			Vector3f[] c = {dir(spec, -1, -1), dir(spec, 1, -1), dir(spec, 1, 1), dir(spec, -1, 1)};
			float[][] uv = {{inset, inset}, {1 - inset, inset}, {1 - inset, 1 - inset}, {inset, 1 - inset}};
			Identifier tex = InfinityGauntlet.id("textures/environment/celestial_apocalypse/" + variant + "/faces/" + FACES[f] + ".png");
			draw(pose, tex, buf -> {
				for (int i = 0; i < 4; i++) buf.addVertex(pose.last().pose(), c[i].x * r, c[i].y * r, c[i].z * r).setUv(uv[i][0], uv[i][1]).setColor(color);
				for (int i = 3; i >= 0; i--) buf.addVertex(pose.last().pose(), c[i].x * r, c[i].y * r, c[i].z * r).setUv(uv[i][0], uv[i][1]).setColor(color);
			});
		}
	}

	private static Vector3f dir(JsonArray spec, float u, float v) {
		float[] out = new float[3];
		for (int i = 0; i < 3; i++) {
			String s = spec.get(i).getAsString();
			float sign = s.startsWith("-") ? -1 : 1;
			String k = s.replace("-", "");
			out[i] = k.equals("u") ? sign * u : k.equals("v") ? sign * v : Float.parseFloat(s);
		}
		return new Vector3f(out[0], out[1], out[2]);
	}

	// ------------------------------------------------------------------ body: cracks and fragments

	/** True while our renderer owns the given body (vanilla must not draw it). */
	public static boolean hideVanilla(String body) {
		if (CelestialClient.destroyed(body) && !(CelestialClient.phase() == 1 && body.equals(CelestialClient.body())
				&& CelestialClient.eventTime(0) < CelestialApocalypse.T_DETACH)) return true;
		return CelestialClient.phase() != 0 && body.equals(CelestialClient.body())
				&& CelestialClient.eventTime(0) >= CelestialApocalypse.T_FRACTURE - 1;
	}

	/** Called at the end of renderSunMoonAndStars with the vanilla angles (radians). */
	public static void drawBody(PoseStack pose, float sunAngle, float moonAngle, MoonPhase moonPhase, float rainBrightness, float partial) {
		if (Minecraft.getInstance().level == null || !Minecraft.getInstance().level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
		if (CelestialClient.phase() == 0) return;
		String body = CelestialClient.body();
		boolean sun = body.equals("sun");
		float t = CelestialClient.eventTime(partial);
		if (t < CelestialApocalypse.T_FRACTURE - 1 || t > CelestialApocalypse.T_FIRST_IMPACT + 5) return;
		float size = sun ? 30f : 20f;
		float angle = sun ? sunAngle : moonAngle;
		Quaternionf frame = new Quaternionf().rotateY(-Mth.HALF_PI).rotateX(angle);

		if (t < CelestialApocalypse.T_DETACH) {
			int stage = Mth.clamp((int) ((t - CelestialApocalypse.T_FRACTURE) / 30f), 0, 3);
			Identifier tex = composite(body, moonPhase, stage);
			if (tex == null) return;
			// Trembling grows with the damage.
			float shake = (stage + 1) * 0.004f;
			Quaternionf q = new Quaternionf(frame).rotateZ(Mth.sin(t * 2.3f) * shake).rotateX(Mth.cos(t * 1.7f) * shake);
			int color = ARGB.colorFromFloat(rainBrightness, 1f, 1f, 1f);
			Vector3f n = new Vector3f(0, 100, 0).rotate(q), x = new Vector3f(size, 0, 0).rotate(q), z = new Vector3f(0, 0, size).rotate(q);
			draw(pose, tex, buf -> quad(buf, pose.last().pose(), n, x, z, 0, 0, 1, 1, color));
			return;
		}
		Identifier tex = composite(body, moonPhase, 3);
		if (tex == null) return;
		for (Fragment f : fragments()) drawFragment(pose, tex, frame, f, size, t - CelestialApocalypse.T_DETACH);
	}

	private static void drawFragment(PoseStack pose, Identifier tex, Quaternionf frame, Fragment f, float size, float u) {
		// 0..20: pieces separate and tumble; 20..60: pulled down toward the impact zone, growing (approaching), then hand-off.
		float detach = Mth.clamp(u / 20f, 0, 1);
		float pull = Mth.clamp((u - 20) / 40f, 0, 1);
		float pullE = pull * pull * (3 - 2 * pull);
		Vector3f local = new Vector3f(f.cx() * 2 * size, 100, -f.cy() * 2 * size)
				.add(new Vector3f(f.out().x * size, f.out().z * size, -f.out().y * size).mul(0.8f * detach + 0.6f * pullE));
		Vector3f world = local.rotate(new Quaternionf(frame));
		// Pull target: from the body's side of the sky, down toward the horizon in front of the viewer.
		Vector3f bodyDir = new Vector3f(0, 1, 0).rotate(new Quaternionf(frame));
		Vector3f target = new Vector3f(bodyDir.x, 0, bodyDir.z);
		if (target.lengthSquared() < 1e-4f) target.set(1, 0, 0);
		target.normalize().mul(100).add(0, 22, 0).add(f.cx() * 70, f.cy() * 30, 0);
		world.lerp(target, pullE);
		float half = 0.25f * size * (1 + 1.2f * pullE);
		Quaternionf tumble = new Quaternionf(frame).rotateXYZ(f.tumble().x * u / 20f * Mth.DEG_TO_RAD,
				f.tumble().y * u / 20f * Mth.DEG_TO_RAD, f.tumble().z * u / 20f * Mth.DEG_TO_RAD);
		Vector3f x = new Vector3f(half, 0, 0).rotate(tumble), z = new Vector3f(0, 0, half).rotate(tumble);
		float alpha = pull > 0.7f ? (1 - pull) / 0.3f : 1f;
		int color = ARGB.colorFromFloat(Mth.clamp(alpha, 0, 1), 1f, 1f, 1f);
		draw(pose, tex, buf -> quad(buf, pose.last().pose(), world, x, z, f.u0(), f.v0(), f.u1(), f.v1(), color));
	}

	/** Quad centered at c with half-axes x (u) and z (v), both windings. */
	private static void quad(BufferBuilder buf, Matrix4f m, Vector3f c, Vector3f x, Vector3f z, float u0, float v0, float u1, float v1, int color) {
		Vector3f[] p = {new Vector3f(c).sub(x).sub(z), new Vector3f(c).add(x).sub(z), new Vector3f(c).add(x).add(z), new Vector3f(c).sub(x).add(z)};
		float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
		for (int i = 0; i < 4; i++) buf.addVertex(m, p[i].x, p[i].y, p[i].z).setUv(uv[i][0], uv[i][1]).setColor(color);
		for (int i = 3; i >= 0; i--) buf.addVertex(m, p[i].x, p[i].y, p[i].z).setUv(uv[i][0], uv[i][1]).setColor(color);
	}

	public interface Builder {
		void build(BufferBuilder buffer);
	}

	/** Immediate draw with the current sky model-view (the pose is multiplied in). */
	public static void draw(PoseStack pose, Identifier texture, Builder builder) {
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		builder.build(buffer);
		MeshData mesh = buffer.build();
		if (mesh == null) return;
		Matrix4fStack mv = RenderSystem.getModelViewStack();
		mv.pushMatrix();
		type(texture).draw(mesh);
		mv.popMatrix();
	}

	private static List<Fragment> fragments() {
		if (fragments == null) {
			fragments = new ArrayList<>();
			JsonObject root = VfxPresets.readJson(InfinityGauntlet.id("celestial_apocalypse/celestial_uv_fragments.json"));
			if (root != null) {
				for (JsonElement e : root.getAsJsonArray("fragments")) {
					JsonObject o = e.getAsJsonObject();
					JsonArray uv = o.getAsJsonArray("uv"), c = o.getAsJsonArray("local_center_in_body_widths");
					JsonArray out = o.getAsJsonArray("outward_direction"), tb = o.getAsJsonArray("tumble_degrees_per_second");
					fragments.add(new Fragment(uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat(),
							c.get(0).getAsFloat(), c.get(1).getAsFloat(),
							new Vector3f(out.get(0).getAsFloat(), out.get(1).getAsFloat(), out.get(2).getAsFloat()),
							new Vector3f(tb.get(0).getAsFloat(), tb.get(1).getAsFloat(), tb.get(2).getAsFloat())));
				}
			}
		}
		return fragments;
	}

	// ------------------------------------------------------------------ composites (original texture + cracks)

	private static Identifier composite(String body, MoonPhase phase, int stage) {
		String base = body.equals("sun") ? "sun" : "moon/" + phase.getSerializedName();
		String key = base + "#" + stage;
		if (!compositeKey.startsWith(base)) reset();
		compositeKey = key;
		return COMPOSITES.computeIfAbsent(key, k -> build(body, base, stage));
	}

	private static Identifier build(String body, String base, int stage) {
		Minecraft mc = Minecraft.getInstance();
		try (NativeImage baseImg = read(Identifier.withDefaultNamespace("textures/environment/celestial/" + base + ".png"))) {
			if (baseImg == null) return null;
			int w = baseImg.getWidth(), h = baseImg.getHeight();
			String crackDir = w <= 32 ? "textures/celestial/pixel32/" : "textures/celestial/";
			try (NativeImage crack = read(InfinityGauntlet.id(crackDir + (body.equals("sun") ? "sun" : "moon") + "_crack_" + stage + ".png"))) {
				NativeImage out = new NativeImage(w, h, false);
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int b = baseImg.getPixel(x, y);
						float br = Math.max(ARGB.red(b), Math.max(ARGB.green(b), ARGB.blue(b))) / 255f;
						float mask = ARGB.alpha(b) / 255f * Mth.clamp((br - 0.04f) / 0.12f, 0, 1);
						// Vanilla draws celestials additively; with alpha blending we emulate it: opacity follows brightness.
						float opacity = mask * (float) Math.pow(br, 1.3);
						int r = ARGB.red(b), g = ARGB.green(b), bl = ARGB.blue(b);
						if (crack != null) {
							int c = crack.getPixel(x * crack.getWidth() / w, y * crack.getHeight() / h);
							float a = ARGB.alpha(c) / 255f * mask;
							r = Math.round(Mth.lerp(a, r, ARGB.red(c)));
							g = Math.round(Mth.lerp(a, g, ARGB.green(c)));
							bl = Math.round(Mth.lerp(a, bl, ARGB.blue(c)));
							opacity = Math.max(opacity, a);
						}
						out.setPixel(x, y, ARGB.color(Math.round(Mth.clamp(opacity, 0, 1) * 255), r, g, bl));
					}
				}
				Identifier id = InfinityGauntlet.id("dynamic/celestial_" + base.replace('/', '_') + "_" + stage);
				mc.getTextureManager().register(id, new DynamicTexture(() -> id.toString(), out));
				return id;
			}
		} catch (Exception e) {
			InfinityGauntlet.LOGGER.warn("Could not build celestial composite for {}", base, e);
			return null;
		}
	}

	private static NativeImage read(Identifier id) throws java.io.IOException {
		var res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) return null;
		try (InputStream in = res.get().open()) {
			return NativeImage.read(in);
		}
	}

	private CelestialSky() {}
}
