package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** All rendering is cosmetic; only the server chooses endpoints, damage and excavated blocks. */
public final class InfinityBeamClient {
    public static final KeyMapping BEAM = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.infinity_gauntlet.infinity_beam", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, ClientPower.CATEGORY));
    private static final Map<Integer, Visual> BEAMS = new HashMap<>();
    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final int[] COLORS = {0xA23BFF, 0x168CFF, 0xEF293E, 0xFFE157, 0x32E268, 0xFF981F};
    private static boolean held;
    private static int ticks;
    private static Level level;
    private record Puff(Vec3 pos, int start) {}
    private static final class Visual {
        ModNetwork.BeamFx state;
        Vec3 previousEnd;
        int received;
        Visual(ModNetwork.BeamFx s) { state = s; previousEnd = s.to(); received = ticks; }
    }
    private static Identifier texture(String name, int frame) {
        return InfinityGauntlet.id("textures/particle/infinity_beam_" + name + "_" + frame + ".png");
    }
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.BeamFx.TYPE, (s, ctx) -> {
            if (s.age() < 0) {
                Visual old = BEAMS.remove(s.entityId());
                if (old != null && old.state.age() >= old.state.charge()) PUFFS.add(new Puff(old.state.to(), ticks));
            } else {
                Visual v = BEAMS.computeIfAbsent(s.entityId(), k -> new Visual(s));
                v.previousEnd = v.state.to(); v.state = s; v.received = ticks;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
            BEAMS.clear(); PUFFS.clear(); held = false; level = null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ticks++;
            if (level != mc.level) { BEAMS.clear(); PUFFS.clear(); level = mc.level; }
            boolean down = mc.player != null && mc.screen == null && BEAM.isDown()
                    && !Cutscene.inputLocked() && !TimeRewindClient.inputLocked();
            if (mc.player != null && down != held) ClientPlayNetworking.send(new ModNetwork.BeamAction(down));
            held = down;
            BEAMS.values().removeIf(v -> ticks - v.received > 10);
            PUFFS.removeIf(p -> ticks - p.start > 16);
            if (mc.player != null) {
                Visual v = BEAMS.get(mc.player.getId());
                if (v != null) {
                    float age = v.state.age() + Math.min(2, ticks - v.received);
                    boolean charging = age < v.state.charge();
                    float progress = charging ? age / v.state.charge() : (age - v.state.charge()) / v.state.duration();
                    com.dantemorrigan.gauntlet.client.StatusHud.persistent("beam", charging
                            ? net.minecraft.network.chat.Component.translatable("msg.infinity_gauntlet.beam.charging", String.valueOf(Math.min(100, (int)(progress * 100))))
                            : net.minecraft.network.chat.Component.translatable("msg.infinity_gauntlet.beam.firing", String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, (v.state.duration() - age + v.state.charge()) / 20f))));
                } else com.dantemorrigan.gauntlet.client.StatusHud.persistent("beam", null);
            }
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(InfinityBeamClient::render);
    }
    private static void render(LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        Vector3f r = new Vector3f(1, 0, 0).rotate(mc.gameRenderer.getMainCamera().rotation());
        Vector3f u = new Vector3f(0, 1, 0).rotate(mc.gameRenderer.getMainCamera().rotation());
        Vec3 right = new Vec3(r.x, r.y, r.z), up = new Vec3(u.x, u.y, u.z);
        for (Visual v : BEAMS.values()) {
            var s = v.state;
            var entity = mc.level.getEntity(s.entityId());
            if (entity == null) continue;
            float age = s.age() + Math.min(2, ticks - v.received + partial);
            Vec3 from = FistPosition.of(entity, partial);
            if (age < s.charge()) {
                int frame = Math.min(3, (int) (age * 4 / s.charge()));
                float charge = Math.min(1, age / s.charge());
                billboard(ctx, camera, from, right, up, .3f + .55f * charge, texture("charge", frame), 0xFFFFFFFF);
                for (int i = 0; i < 6; i++) {
                    double angle = age * .13 + i * Math.PI / 3;
                    double radius = .55 * (1 - charge) + .08;
                    Vec3 orb = from.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
                    billboard(ctx, camera, orb, right, up, .12f, texture("charge", frame), 0xE0000000 | COLORS[i]);
                }
                continue;
            }
            Vec3 end = v.previousEnd.lerp(s.to(), Math.min(1, (ticks - v.received + partial) / 2.0));
            Vec3 line = end.subtract(from);
            double length = line.length();
            if (length < .15) continue;
            Vec3 dir = line.scale(1 / length);
            Vec3 axis = Math.abs(dir.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 a = dir.cross(axis).normalize(), b = dir.cross(a).normalize();
            float fireAge = age - s.charge();
            double envelope = Math.min(1, (fireAge + 1) / 8.0) * Math.min(1, (s.duration() - fireAge) / 12.0);
            double power = Math.max(.12, envelope) * (1 + .06 * Math.sin(age * .65));
            int frame = ((int) (age - s.charge()) / 2) % 4;
            Identifier body = InfinityGauntlet.id("textures/infinity_beam/beam_body_" + frame + ".png");
            // Mirrored UV tiles, split at boundaries: stable core, no giant stretched billboard.
            double offset = (age * .2) % 8;
            int tile = 0;
            for (double d = -offset; d < length; d += 4, tile++) {
                double x = Math.max(0, d), y = Math.min(length, d + 4);
                if (y <= x) continue;
                float u0 = (float) ((x - d) / 4), u1 = (float) ((y - d) / 4);
                if ((tile & 1) != 0) { u0 = 1-u0; u1 = 1-u1; }
                Vec3 p = from.add(dir.scale(x)), q = from.add(dir.scale(y));
                ribbon(ctx, camera, p, q, a, 1.1 * power, body, 0xFFFFFFFF, u0, u1);
                ribbon(ctx, camera, p, q, b, 1.1 * power, body, 0xDFFFFFFF, u0, u1);
            }
            // Six separate helical streams around the bright beam, one for each stone.
            for (int color = 0; color < 6; color++) {
                for (double d = 0; d < length; d += 2) {
                    double next = Math.min(length, d+2);
                    double angle0 = d*.55 - age*.25 + color*Math.PI/3;
                    double angle1 = next*.55 - age*.25 + color*Math.PI/3;
                    Vec3 p = from.add(dir.scale(d)).add(a.scale(Math.cos(angle0)*.65)).add(b.scale(Math.sin(angle0)*.65));
                    Vec3 q = from.add(dir.scale(next)).add(a.scale(Math.cos(angle1)*.65)).add(b.scale(Math.sin(angle1)*.65));
                    Vec3 side = q.subtract(p).cross(camera.subtract(p)).normalize();
                    ribbon(ctx, camera, p, q, side, .1 * power, body, 0xE0000000 | COLORS[color], 0, 1);
                }
            }
            billboard(ctx, camera, from, right, up, (float)(.9 * power), texture("muzzle", frame), 0xFFFFFFFF);
            billboard(ctx, camera, end, right, up, (float)(2.1 * power), texture("impact", ((int)age/2)%4), 0xFFFFFFFF);
        }
        for (Puff puff : PUFFS) {
            int age = ticks - puff.start;
            billboard(ctx, camera, puff.pos, right, up, 1.7f, texture("residual", Math.min(3, age/4)), 0xDFFFFFFF);
        }
    }
    private static void billboard(LevelRenderContext ctx, Vec3 camera, Vec3 center, Vec3 right, Vec3 up,
                                  float half, Identifier tex, int color) {
        quad(ctx, camera, center.subtract(right.scale(half)).subtract(up.scale(half)),
                center.add(right.scale(half)).subtract(up.scale(half)),
                center.add(right.scale(half)).add(up.scale(half)),
                center.subtract(right.scale(half)).add(up.scale(half)), tex, color, 0, 1);
    }
    private static void ribbon(LevelRenderContext ctx, Vec3 camera, Vec3 from, Vec3 to, Vec3 side,
                               double half, Identifier tex, int color, float u0, float u1) {
        Vec3 width = side.scale(half);
        quad(ctx, camera, from.subtract(width), to.subtract(width), to.add(width), from.add(width), tex, color, u0, u1);
    }
    private static void quad(LevelRenderContext ctx, Vec3 camera, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             Identifier tex, int color, float u0, float u1) {
        ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.entityTranslucentEmissive(tex), (pose, out) -> {
            vertex(out, pose, a.subtract(camera), u0, 1, color);
            vertex(out, pose, b.subtract(camera), u1, 1, color);
            vertex(out, pose, c.subtract(camera), u1, 0, color);
            vertex(out, pose, d.subtract(camera), u0, 0, color);
            // Double-sided: beam stays visible from every camera angle.
            vertex(out, pose, d.subtract(camera), u0, 0, color);
            vertex(out, pose, c.subtract(camera), u1, 0, color);
            vertex(out, pose, b.subtract(camera), u1, 1, color);
            vertex(out, pose, a.subtract(camera), u0, 1, color);
        });
    }
    private static void vertex(VertexConsumer out, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                               Vec3 p, float u, float v, int color) {
        out.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(color).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }
    private InfinityBeamClient() {}
}
