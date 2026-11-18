package fr.serveur.nightfall.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.serveur.nightfall.Nightfall;
import fr.serveur.nightfall.NightfallClient;
import fr.serveur.nightfall.NightfallConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

/**
 * Ambiance horreur la nuit, uniquement côté client :
 * - des yeux qui t'observent depuis la brume (ils disparaissent si tu les fixes ou t'approches),
 * - un cœur qui bat quand ils sont là,
 * - des bruits de pas qui se rapprochent derrière toi,
 * - des bruits de grotte lointains,
 * - de la cendre qui flotte dans l'air.
 */
public final class HorrorAmbience {
    private static final Identifier EYES_TEXTURE = Nightfall.id("textures/misc/eyes.png");
    private static final Random RNG = Random.create();

    // Yeux
    private static Vec3d eyesPos;
    private static int eyesAge, eyesLife, stareTicks;
    private static float eyesAlpha, prevEyesAlpha;
    private static boolean eyesFading, eyesStartled;
    private static float eyesSize;

    // Pas
    private static int footstepsLeft, footstepDelay;
    private static double footstepAngle, footstepDist;

    private HorrorAmbience() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HorrorAmbience::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(HorrorAmbience::renderEyes);
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) { eyesPos = null; footstepsLeft = 0; return; }
        if (client.isPaused()) return;

        float night = NightfallClient.nightFactor(world);
        boolean fullNight = night >= 1f;

        if (night > 0.3f) spawnAsh(world, player, night);

        if (fullNight) {
            if (eyesPos == null && RNG.nextInt(NightfallConfig.EYES_CHANCE) == 0) spawnEyes(world, player);
            if (footstepsLeft == 0 && RNG.nextInt(NightfallConfig.FOOTSTEPS_CHANCE) == 0) startFootsteps();
            if (RNG.nextInt(NightfallConfig.CAVE_SOUND_CHANCE) == 0) {
                double a = RNG.nextDouble() * Math.PI * 2;
                world.playSound(player.getX() + Math.cos(a) * 12, player.getY(), player.getZ() + Math.sin(a) * 12,
                        SoundEvents.AMBIENT_CAVE.value(), SoundCategory.AMBIENT, 0.8f, 0.7f + RNG.nextFloat() * 0.3f, false);
            }
        } else if (eyesPos != null) {
            eyesFading = true;
        }

        updateEyes(world, player);
        updateFootsteps(world, player, fullNight);
    }

    // ---------------------------------------------------------------- cendre

    private static void spawnAsh(ClientWorld world, ClientPlayerEntity player, float night) {
        int count = Math.round(NightfallConfig.ASH_PER_TICK * night);
        for (int i = 0; i < count; i++) {
            world.addParticle(ParticleTypes.ASH,
                    player.getX() + (RNG.nextDouble() - 0.5) * 14,
                    player.getY() + RNG.nextDouble() * 5 - 1,
                    player.getZ() + (RNG.nextDouble() - 0.5) * 14,
                    0, 0, 0);
        }
        if (RNG.nextInt(6) == 0) {
            world.addParticle(ParticleTypes.WHITE_ASH,
                    player.getX() + (RNG.nextDouble() - 0.5) * 10,
                    player.getY() + RNG.nextDouble() * 3,
                    player.getZ() + (RNG.nextDouble() - 0.5) * 10,
                    0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ yeux

    private static void spawnEyes(ClientWorld world, ClientPlayerEntity player) {
        double dist = NightfallClient.nightFogDistance(1f) + 2 + RNG.nextDouble() * 4;
        float yaw = player.getYaw() + (RNG.nextFloat() - 0.5f) * 150f;
        if (RNG.nextInt(4) == 0) yaw += 180f; // parfois derrière toi
        double rad = Math.toRadians(yaw);
        double x = player.getX() - Math.sin(rad) * dist;
        double z = player.getZ() + Math.cos(rad) * dist;
        double y = player.getEyeY() + (RNG.nextDouble() - 0.4) * 1.2;

        // On cherche un endroit libre (pas dans un bloc plein) en montant un peu.
        for (int i = 0; i < 4; i++) {
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (!state.isOpaqueFullCube(world, pos)) {
                eyesPos = new Vec3d(x, y, z);
                eyesAge = 0;
                eyesLife = 100 + RNG.nextInt(200);
                stareTicks = 0;
                eyesAlpha = prevEyesAlpha = 0f;
                eyesFading = false;
                eyesStartled = false;
                eyesSize = 0.8f + RNG.nextFloat() * 0.5f;
                return;
            }
            y += 1;
        }
    }

    private static void updateEyes(ClientWorld world, ClientPlayerEntity player) {
        prevEyesAlpha = eyesAlpha;
        if (eyesPos == null) return;
        eyesAge++;

        Vec3d toEyes = eyesPos.subtract(player.getEyePos());
        double dist = toEyes.length();
        double look = player.getRotationVec(1f).dotProduct(toEyes.normalize());
        if (look > 0.985) stareTicks++;

        if (!eyesFading && (dist < 4.5 || stareTicks > 25)) {
            eyesFading = true;
            eyesStartled = true;
            world.playSound(eyesPos.x, eyesPos.y, eyesPos.z, SoundEvents.BLOCK_GRASS_STEP, SoundCategory.AMBIENT,
                    0.9f, 0.6f, false);
        }
        if (eyesAge > eyesLife) eyesFading = true;

        if (eyesFading) {
            eyesAlpha -= eyesStartled ? 0.35f : 0.04f;
            if (eyesAlpha <= 0f) { eyesPos = null; eyesAlpha = prevEyesAlpha = 0f; return; }
        } else {
            eyesAlpha = Math.min(1f, eyesAlpha + 0.03f);
            if (eyesAge % 22 == 0) {
                player.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.5f, 0.9f);
            }
        }
    }

    private static void renderEyes(WorldRenderContext ctx) {
        if (eyesPos == null) return;
        float alpha = MathHelper.lerp(ctx.tickDelta(), prevEyesAlpha, eyesAlpha);
        int blink = eyesAge % 80;
        if (alpha <= 0.01f || blink < 3) return; // clignement

        Camera camera = ctx.camera();
        Vec3d cam = camera.getPos();
        MatrixStack matrices = ctx.matrixStack();
        matrices.push();
        matrices.translate(eyesPos.x - cam.x, eyesPos.y - cam.y, eyesPos.z - cam.z);
        matrices.multiply(camera.getRotation());
        Matrix4f m = matrices.peek().getPositionMatrix();

        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, EYES_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        float w = 0.45f * eyesSize, h = w / 2f;
        int a = (int) (alpha * 255);
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(m, -w, -h, 0).texture(0f, 1f).color(255, 255, 255, a).next();
        buf.vertex(m, w, -h, 0).texture(1f, 1f).color(255, 255, 255, a).next();
        buf.vertex(m, w, h, 0).texture(1f, 0f).color(255, 255, 255, a).next();
        buf.vertex(m, -w, h, 0).texture(0f, 0f).color(255, 255, 255, a).next();
        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        matrices.pop();
    }

    // ------------------------------------------------------------------ pas

    private static void startFootsteps() {
        footstepsLeft = 3 + RNG.nextInt(4);
        footstepDelay = 0;
        footstepAngle = 180 + (RNG.nextDouble() - 0.5) * 60; // derrière toi
        footstepDist = 9;
    }

    private static void updateFootsteps(ClientWorld world, ClientPlayerEntity player, boolean fullNight) {
        if (footstepsLeft <= 0) return;
        if (!fullNight) { footstepsLeft = 0; return; }
        if (--footstepDelay > 0) return;
        footstepDelay = 7 + RNG.nextInt(3);
        footstepsLeft--;

        double rad = Math.toRadians(player.getYaw() + footstepAngle);
        double x = player.getX() - Math.sin(rad) * footstepDist;
        double z = player.getZ() + Math.cos(rad) * footstepDist;
        BlockPos below = BlockPos.ofFloored(x, player.getY() - 0.5, z);
        BlockState ground = world.getBlockState(below);
        var step = ground.isAir() ? SoundEvents.BLOCK_GRASS_STEP : ground.getSoundGroup().getStepSound();
        world.playSound(x, player.getY(), z, step, SoundCategory.AMBIENT, 0.6f, 0.8f + RNG.nextFloat() * 0.15f, false);
        footstepDist = Math.max(3, footstepDist - 1.3);
    }
}
