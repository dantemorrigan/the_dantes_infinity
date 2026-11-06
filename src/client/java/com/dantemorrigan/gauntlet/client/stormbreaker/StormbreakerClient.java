package com.dantemorrigan.gauntlet.client.stormbreaker;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModComponents;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.client.celestial.CelestialSky;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.client.fx.ClientPower;
import com.dantemorrigan.gauntlet.client.fx.FxParticle;
import com.dantemorrigan.gauntlet.client.fx.TimeRewindClient;
import com.dantemorrigan.gauntlet.client.fx.VfxPresets;
import com.dantemorrigan.gauntlet.stormbreaker.StormFx;
import com.dantemorrigan.gauntlet.stormbreaker.StormNetwork;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public final class StormbreakerClient {
    private static final KeyMapping THROW = key("throw", GLFW.GLFW_KEY_X);
    private static final KeyMapping WRATH = key("wrath", GLFW.GLFW_KEY_H);
    private static final KeyMapping FLIGHT = key("flight", GLFW.GLFW_KEY_J);
    private static final Map<Integer, Animation> ANIMATIONS = new HashMap<>();
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private static ClientLevel level;
    private static boolean attackDown;
    private static JsonObject clips;
    private static Storm storm;

    private record Animation(String clip, long start, int length) {}
    private record Storm(int owner, Vec3 center, long start) {}
    public record Pose(Vec3 rotation, Vec3 translation) {}

    public static void init() {
        EntityRendererRegistry.register(ModEntities.STORMBREAKER, StormbreakerRenderer::new);
        for (StormFx fx : StormFx.values()) register(fx);
        ClientPlayNetworking.registerGlobalReceiver(StormNetwork.Visual.TYPE, (packet, context) -> receive(packet));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> reset(null));
        ClientTickEvents.END_CLIENT_TICK.register(StormbreakerClient::tick);
        HudElementRegistry.addLast(InfinityGauntlet.id("stormbreaker"), (g, delta) -> hud(g));
    }

    private static KeyMapping key(String name, int code) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.infinity_gauntlet.stormbreaker_" + name,
                InputConstants.Type.KEYSYM, code, ClientPower.CATEGORY));
    }

    private static void register(StormFx fx) {
        var spec = new VfxPresets.Spec(fx.frames, fx.ticksPerFrame, fx.frames * fx.ticksPerFrame, false, 1,
                fx != StormFx.BLOCK_SHARDS, VfxPresets.Orientation.CAMERA);
        ParticleProviderRegistry.getInstance().register(fx.particle, sprites -> (type, world, x, y, z, dx, dy, dz, random) -> {
            Vector3f direction = new Vector3f((float) dx, (float) dy, (float) dz);
            float size = direction.length();
            if (size < .001f) { size = 1; direction.set(0, 1, 0); }
            direction.normalize();
            var particle = new FxParticle(world, x, y + (fx.pivotY - .5f) * size, z, sprites, spec, size,
                    null, -1, null, 1f) {
                @Override
                public FacingCameraMode getFacingCameraMode() {
                    return switch (fx.facing) {
                        case VERTICAL -> (q, camera, partial) -> {
                            Vector3f right = new Vector3f(1, 0, 0).rotate(camera.rotation());
                            q.rotationY((float) Math.atan2(-right.z, right.x));
                        };
                        case DIRECTED -> (q, camera, partial) -> q.rotationY((float) Math.atan2(direction.x, direction.z));
                        case FLAT -> (q, camera, partial) -> q.rotationX(-Mth.HALF_PI);
                        default -> super.getFacingCameraMode();
                    };
                }
            };
            return fx.facing == StormFx.Facing.TRAIL ? particle.alignX(direction) : particle;
        });
    }

    private static void reset(ClientLevel next) {
        level = next;
        storm = null;
        attackDown = false;
        ANIMATIONS.clear();
        COOLDOWNS.clear();
        clips = null;
    }

    private static void tick(Minecraft mc) {
        if (level != mc.level) reset(mc.level);
        if (level == null || mc.player == null) return;
        long now = level.getGameTime();
        ANIMATIONS.values().removeIf(a -> now - a.start > a.length);
        if (storm != null && now - storm.start > 113) storm = null;
        boolean use = mc.player.getMainHandItem().is(ModItems.STORMBREAKER) && mc.player.isAlive()
                && !mc.player.isSpectator() && mc.screen == null && mc.isWindowActive()
                && !Cutscene.inputLocked() && !TimeRewindClient.inputLocked()
                && ClientPlayNetworking.canSend(StormNetwork.Action.TYPE);
        consume(THROW, StormNetwork.THROW, use);
        consume(WRATH, StormNetwork.WRATH, use);
        consume(FLIGHT, StormNetwork.FLIGHT, use);
        boolean attack = mc.options.keyAttack.isDown();
        if (use && attack && !attackDown && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.MISS) {
            ClientPlayNetworking.send(new StormNetwork.Action(StormNetwork.AIR_SWING));
        }
        attackDown = attack;
    }

    private static void consume(KeyMapping key, int action, boolean use) {
        boolean pressed = false;
        while (key.consumeClick()) pressed = true;
        if (use && pressed) ClientPlayNetworking.send(new StormNetwork.Action(action));
    }

    private static void receive(StormNetwork.Visual packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (level != mc.level) reset(mc.level);
        long start = level.getGameTime() - packet.elapsed();
        if ("stop".equals(packet.clip())) {
            if (storm != null && storm.owner == packet.entity()) storm = null;
            ANIMATIONS.remove(packet.entity());
            return;
        }
        JsonObject clip = clip(packet.clip());
        if (clip == null) return;
        if ("heavens_wrath".equals(packet.clip())) {
            storm = new Storm(packet.entity(), packet.origin(), start);
            if (packet.elapsed() != 0) return;
        }
        if (clip.has("keyframes")) ANIMATIONS.put(packet.entity(), new Animation(packet.clip(), start, clip.get("duration_ticks").getAsInt()));
        if (mc.player != null && packet.entity() == mc.player.getId()) {
            switch (packet.clip()) {
                case "heavens_wrath" -> COOLDOWNS.put("wrath", start + 440);
                case "lightning_cast" -> COOLDOWNS.put("lightning", start + 42);
                case "ground_slam" -> COOLDOWNS.put("ground_wave", start + 65);
                case "throw_release" -> COOLDOWNS.put("throw", start + 50);
                default -> { }
            }
        }
    }

    private static JsonObject clip(String name) {
        if (clips == null) {
            JsonObject data = VfxPresets.readJson(InfinityGauntlet.id("stormbreaker/visual_clips.json"));
            clips = data == null ? new JsonObject() : data.getAsJsonObject("clips");
        }
        return clips.getAsJsonObject(name);
    }

    public static Pose pose(int entity, float partial) {
        if (level == null) return null;
        Animation animation = ANIMATIONS.get(entity);
        if (animation == null) return null;
        float age = level.getGameTime() - animation.start + partial;
        if (age < 0 || age > animation.length) return null;
        JsonArray frames = clip(animation.clip).getAsJsonArray("keyframes");
        if (frames.isEmpty()) return null;
        JsonObject last = frames.get(0).getAsJsonObject();
        for (int i = 1; i < frames.size(); i++) {
            JsonObject next = frames.get(i).getAsJsonObject();
            float to = next.get("tick").getAsFloat();
            if (age <= to) {
                float from = last.get("tick").getAsFloat();
                float fraction = Mth.clamp((age - from) / (to - from), 0, 1);
                return new Pose(vec(last, "rotation_xyz_deg").lerp(vec(next, "rotation_xyz_deg"), fraction),
                        vec(last, "translation_xyz_blocks").lerp(vec(next, "translation_xyz_blocks"), fraction));
            }
            last = next;
        }
        return new Pose(vec(last, "rotation_xyz_deg"), vec(last, "translation_xyz_blocks"));
    }

    private static Vec3 vec(JsonObject frame, String key) {
        JsonArray a = frame.getAsJsonArray(key);
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    public static void firstPerson(PoseStack stack, int entity, float partial, boolean left) {
        Pose motion = pose(entity, partial);
        if (motion == null) return;
        Vec3 t = motion.translation.scale(.57);
        Vec3 r = motion.rotation;
        float side = left ? -1 : 1;
        stack.translate(side * t.x, t.y, -t.z);
        stack.mulPose(Axis.XP.rotationDegrees((float) r.x * .55f));
        stack.mulPose(Axis.YP.rotationDegrees((float) r.y * .48f * side));
        stack.mulPose(Axis.ZP.rotationDegrees((float) r.z * .5f * side));
    }

    public static float skyFactor(Vec3 camera, float partial) {
        if (level == null || storm == null) return 0;
        float age = level.getGameTime() - storm.start + partial;
        return Mth.clamp(Math.min(age / 24, (112 - age) / 26), 0, 1)
                * (float) Mth.clamp((112 - camera.distanceTo(storm.center)) / 45, 0, 1);
    }

    public static void drawClouds(PoseStack pose, float partial) {
        Minecraft mc = Minecraft.getInstance();
        float factor = skyFactor(mc.gameRenderer.getMainCamera().position(), partial);
        if (factor < .01f) return;
        int frame = ((int) (level.getGameTime() - storm.start) / 4) & 7;
        float u0 = (frame % 4) / 4f + .001f, v0 = (frame / 4) / 2f + .002f;
        float u1 = u0 + .248f, v1 = v0 + .496f;
        var tex = InfinityGauntlet.id("textures/environment/stormbreaker_clouds.png");
        int color = ARGB.colorFromFloat(.95f * factor, 1, 1, 1);
        CelestialSky.draw(pose, tex, buf -> {
            var matrix = pose.last().pose();
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4, b = (i + 1) * Math.PI / 4;
                float x1 = (float) Math.cos(a), z1 = (float) Math.sin(a);
                float x2 = (float) Math.cos(b), z2 = (float) Math.sin(b);
                buf.addVertex(matrix, x1 * 145, -7, z1 * 145).setUv(u0, v1).setColor(color);
                buf.addVertex(matrix, x2 * 145, -7, z2 * 145).setUv(u1, v1).setColor(color);
                buf.addVertex(matrix, x2 * 36, 139, z2 * 36).setUv(u1, v0).setColor(color);
                buf.addVertex(matrix, x1 * 36, 139, z1 * 36).setUv(u0, v0).setColor(color);
                buf.addVertex(matrix, x1 * 36, 139, z1 * 36).setUv(u0, v0).setColor(color);
                buf.addVertex(matrix, x2 * 36, 139, z2 * 36).setUv(u1, v0).setColor(color);
                buf.addVertex(matrix, x2 * 145, -7, z2 * 145).setUv(u1, v1).setColor(color);
                buf.addVertex(matrix, x1 * 145, -7, z1 * 145).setUv(u0, v1).setColor(color);
            }
        });
    }

    private static void hud(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || level == null || mc.screen != null || mc.options.hideGui
                || !mc.player.getMainHandItem().is(ModItems.STORMBREAKER)) return;
        boolean bound = mc.player.getMainHandItem().has(ModComponents.STORMBREAKER_SEAL);
        String[] icons = {"combo", "ground_wave", "lightning", "throw", "wrath", "flight", "guard"};
        String[] labels = {"", "", "", THROW.getTranslatedKeyMessage().getString(),
                WRATH.getTranslatedKeyMessage().getString(), FLIGHT.getTranslatedKeyMessage().getString(), ""};
        int x = g.guiWidth() - 132, y = g.guiHeight() - 45;
        g.fill(x - 4, y - 2, x + 126, y + 27, 0xC30A1729);
        for (int i = 0; i < icons.length; i++) {
            String id = icons[i];
            boolean cooldown = (bound || i == icons.length - 1) && level.getGameTime() < COOLDOWNS.getOrDefault(id, 0L);
            String state = cooldown || !bound && i == icons.length - 1 ? "_cooldown" : "_ready";
            g.blit(RenderPipelines.GUI_TEXTURED, InfinityGauntlet.id("textures/gui/sprites/stormbreaker/" + id + state + ".png"),
                    x + i * 18, y, 0, 0, 16, 16, 24, 24, 24, 24);
            if (!labels[i].isEmpty()) g.text(mc.font, labels[i], x + i * 18 + 5, y + 17, 0xFFC4E8FF, true);
        }
    }

    private StormbreakerClient() {}
}
