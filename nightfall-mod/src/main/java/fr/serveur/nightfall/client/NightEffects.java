package fr.serveur.nightfall.client;

import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Ambiance de nuit, en plus de HorrorAmbience (côté client uniquement) :
 * - feux follets : lueurs pâles qui flottent dans la brume et s'enfuient quand on approche ;
 * - brume qui « respire » doucement ;
 * - coups de froid : la brume se referme d'un coup autour du joueur, avec des battements de cœur ;
 * - quelque chose qui court devant le joueur, dans le noir ;
 * - sons lointains : hurlements, cloche, porte qui grince, murmures, cri.
 */
public final class NightEffects {
    private static final Identifier WISP_TEXTURE = Nightfall.id("textures/misc/wisp.png");
    private static final Random RNG = Random.create();
    /** Développement : feux follets immédiats pour les captures (-Dnightfall.devshots=true). */
    private static final boolean DEMO = Boolean.getBoolean("nightfall.devshots");

    private static final class Wisp {
        Vec3d pos, vel;
        int age, life;
        float alpha, prevAlpha, hue;
        boolean fleeing;
    }

    private static final List<Wisp> WISPS = new ArrayList<>();

    // Coup de froid
    private static int dreadTicks = -1;
    private static float dread = 1f, prevDread = 1f;
    private static long ticks;

    // Course dans le noir
    private static int runnerSteps;
    private static int runnerDelay;
    private static double runnerYaw, runnerProgress;

    private NightEffects() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(NightEffects::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(NightEffects::renderWisps);
    }

    /** Multiplicateur appliqué à la distance de brume (respiration + coup de froid). */
    public static float fogMultiplier(float tickDelta) {
        double t = (ticks + tickDelta) / 20.0;
        float breathing = 1f + NightfallConfig.FOG_BREATHING * (float) Math.sin(t * 2 * Math.PI / 9.0);
        return breathing * MathHelper.lerp(tickDelta, prevDread, dread);
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            WISPS.clear();
            dreadTicks = -1;
            dread = prevDread = 1f;
            runnerSteps = 0;
            return;
        }
        if (client.isPaused()) return;
        ticks++;
        boolean fullNight = NightfallClient.nightFactor(world) >= 1f;

        if (fullNight) {
            if (WISPS.size() < NightfallConfig.MAX_WISPS && (DEMO || RNG.nextInt(NightfallConfig.WISP_CHANCE) == 0)) spawnWisp(world, player);
            if (RNG.nextInt(NightfallConfig.EERIE_SOUND_CHANCE) == 0) eerieSound(world, player);
            if (runnerSteps == 0 && RNG.nextInt(NightfallConfig.RUNNER_CHANCE) == 0) {
                runnerSteps = 9;
                runnerDelay = 0;
                runnerYaw = player.getYaw() + (RNG.nextBoolean() ? -70 : 70);
                runnerProgress = 0;
            }
            if (dreadTicks < 0 && RNG.nextInt(NightfallConfig.DREAD_CHANCE) == 0) {
                dreadTicks = 0;
                player.playSound(SoundEvents.ENTITY_WARDEN_NEARBY_CLOSER, 0.6f, 0.7f);
            }
        }
        updateWisps(world, player, fullNight);
        updateRunner(world, player, fullNight);
        updateDread(player, fullNight);
    }

    // ------------------------------------------------------------------ feux follets

    private static void spawnWisp(ClientWorld world, ClientPlayerEntity player) {
        double dist = NightfallClient.nightFogDistance(1f) + 3 + RNG.nextDouble() * 8;
        double rad = Math.toRadians(player.getYaw() + (RNG.nextFloat() - 0.5f) * 200f);
        double x = player.getX() - Math.sin(rad) * dist, z = player.getZ() + Math.cos(rad) * dist;
        double y = player.getEyeY() + (RNG.nextDouble() - 0.3) * 2.0;
        if (world.getBlockState(BlockPos.ofFloored(x, y, z)).isOpaqueFullCube(world, BlockPos.ofFloored(x, y, z))) return;
        Wisp w = new Wisp();
        w.pos = new Vec3d(x, y, z);
        w.vel = new Vec3d((RNG.nextDouble() - 0.5) * 0.02, 0, (RNG.nextDouble() - 0.5) * 0.02);
        w.life = 500 + RNG.nextInt(500);
        w.hue = RNG.nextFloat();
        WISPS.add(w);
    }

    private static void updateWisps(ClientWorld world, ClientPlayerEntity player, boolean fullNight) {
        WISPS.removeIf(w -> {
            w.prevAlpha = w.alpha;
            w.age++;
            Vec3d away = w.pos.subtract(player.getEyePos());
            double dist = away.length();
            if (!w.fleeing && (dist < 6 || w.age > w.life || !fullNight)) {
                w.fleeing = true;
                if (dist < 6) world.playSound(w.pos.x, w.pos.y, w.pos.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                        SoundCategory.AMBIENT, 0.6f, 0.5f, false);
            }
            if (w.fleeing) {
                w.vel = w.vel.multiply(0.9).add(away.normalize().multiply(0.03)).add(0, 0.006, 0);
                w.alpha -= 0.03f;
            } else {
                // dérive lente et hésitante
                w.vel = w.vel.multiply(0.97).add((RNG.nextDouble() - 0.5) * 0.004, (RNG.nextDouble() - 0.5) * 0.002, (RNG.nextDouble() - 0.5) * 0.004);
                w.alpha = Math.min(1f, w.alpha + 0.02f);
            }
            w.pos = w.pos.add(w.vel).add(0, Math.sin((w.age + w.hue * 50) / 18.0) * 0.006, 0);
            return w.fleeing && w.alpha <= 0f;
        });
    }

    private static void renderWisps(WorldRenderContext ctx) {
        if (WISPS.isEmpty()) return;
        Camera camera = ctx.camera();
        Vec3d cam = camera.getPos();
        MatrixStack matrices = ctx.matrixStack();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, WISP_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE); // lumière additive
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        for (Wisp w : WISPS) {
            float a = MathHelper.lerp(ctx.tickDelta(), w.prevAlpha, w.alpha);
            if (a <= 0.01f) continue;
            float pulse = 0.75f + 0.25f * (float) Math.sin((w.age + ctx.tickDelta()) / 6.0 + w.hue * 10);
            matrices.push();
            matrices.translate(w.pos.x - cam.x, w.pos.y - cam.y, w.pos.z - cam.z);
            matrices.multiply(camera.getRotation());
            Matrix4f m = matrices.peek().getPositionMatrix();
            // teinte : vert d'eau pâle à bleu glacé
            int r = (int) (150 + 40 * w.hue), g = 235, b = (int) (200 + 55 * w.hue);
            int alpha = (int) (a * pulse * 230);
            BufferBuilder buf = Tessellator.getInstance().getBuffer();
            buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (float size : new float[]{0.9f, 0.35f}) { // halo + cœur
                int al = size > 0.5f ? alpha / 2 : alpha;
                buf.vertex(m, -size, -size, 0).texture(0f, 1f).color(r, g, b, al).next();
                buf.vertex(m, size, -size, 0).texture(1f, 1f).color(r, g, b, al).next();
                buf.vertex(m, size, size, 0).texture(1f, 0f).color(r, g, b, al).next();
                buf.vertex(m, -size, size, 0).texture(0f, 0f).color(r, g, b, al).next();
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
            matrices.pop();
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ sons lointains

    private static void eerieSound(ClientWorld world, ClientPlayerEntity player) {
        double a = RNG.nextDouble() * Math.PI * 2, d = 12 + RNG.nextDouble() * 10;
        double x = player.getX() + Math.cos(a) * d, z = player.getZ() + Math.sin(a) * d;
        Object[][] pool = {
                {SoundEvents.ENTITY_WOLF_HOWL, 0.55f, 0.6f},
                {SoundEvents.BLOCK_BELL_RESONATE, 0.5f, 0.5f},
                {SoundEvents.BLOCK_WOODEN_DOOR_OPEN, 0.7f, 0.55f},
                {SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS, 1.0f, 0.8f},
                {SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.8f, 0.7f},
                {SoundEvents.ENTITY_GHAST_SCREAM, 0.18f, 0.45f}};
        Object[] pick = pool[RNG.nextInt(pool.length)];
        world.playSound(x, player.getEyeY(), z, sound(pick[0]), SoundCategory.AMBIENT, (float) pick[1],
                (float) pick[2] + (RNG.nextFloat() - 0.5f) * 0.1f, false);
    }

    @SuppressWarnings("unchecked")
    private static SoundEvent sound(Object o) {
        return o instanceof RegistryEntry<?> e ? ((RegistryEntry<SoundEvent>) e).value() : (SoundEvent) o;
    }

    // ------------------------------------------------------------------ course dans le noir

    private static void updateRunner(ClientWorld world, ClientPlayerEntity player, boolean fullNight) {
        if (runnerSteps <= 0) return;
        if (!fullNight) {
            runnerSteps = 0;
            return;
        }
        if (--runnerDelay > 0) return;
        runnerDelay = 3;
        runnerSteps--;
        runnerProgress += 1;
        // passe devant le joueur, d'un côté à l'autre, juste au-delà de la brume
        double dist = NightfallClient.nightFogDistance(1f) + 1.5;
        double yaw = Math.toRadians(runnerYaw + (runnerYaw > player.getYaw() ? -1 : 1) * runnerProgress * 16);
        double x = player.getX() - Math.sin(yaw) * dist, z = player.getZ() + Math.cos(yaw) * dist;
        BlockState ground = world.getBlockState(BlockPos.ofFloored(x, player.getY() - 0.5, z));
        SoundEvent step = ground.isAir() ? SoundEvents.BLOCK_GRASS_STEP : ground.getSoundGroup().getStepSound();
        world.playSound(x, player.getY(), z, step, SoundCategory.AMBIENT, 0.75f, 1.15f + RNG.nextFloat() * 0.1f, false);
    }

    // ------------------------------------------------------------------ coup de froid

    private static void updateDread(ClientPlayerEntity player, boolean fullNight) {
        prevDread = dread;
        if (dreadTicks < 0) {
            dread = Math.min(1f, dread + 0.05f);
            return;
        }
        dreadTicks++;
        if (!fullNight || dreadTicks > 130) {
            dreadTicks = -1;
            return;
        }
        if (dreadTicks <= 20) dread = MathHelper.lerp(dreadTicks / 20f, 1f, NightfallConfig.DREAD_FOG);       // la brume se referme
        else if (dreadTicks <= 90) dread = NightfallConfig.DREAD_FOG;                                      // on ne voit plus rien
        else dread = MathHelper.lerp((dreadTicks - 90) / 40f, NightfallConfig.DREAD_FOG, 1f);               // elle se relâche
        if (dreadTicks <= 90 && dreadTicks % 14 == 0) player.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.9f, 1.0f);
    }
}
