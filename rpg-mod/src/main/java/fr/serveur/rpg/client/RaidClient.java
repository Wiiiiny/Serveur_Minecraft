package fr.serveur.rpg.client;

import fr.serveur.rpg.SurvieRpg;
import fr.serveur.rpg.raid.RaidWorld;
import fr.serveur.rpg.raid.statue.Fear;
import fr.serveur.rpg.raid.statue.StatueEntity;
import fr.serveur.rpg.raid.statue.Statues;
import fr.serveur.rpg.raid.thief.ThiefEntity;
import fr.serveur.rpg.raid.thief.Thieves;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Côté client du raid : modèles de la statue et du voleur, vignette de la Peur, info-bulle du butin du raid. */
public final class RaidClient {
    static final EntityModelLayer STATUE_LAYER = new EntityModelLayer(new Identifier(SurvieRpg.MOD_ID, "statue"), "main");
    static final EntityModelLayer THIEF_LAYER = new EntityModelLayer(new Identifier(SurvieRpg.MOD_ID, "chapardeur"), "main");
    private static int fear;

    private RaidClient() {}

    static void register() {
        EntityModelLayerRegistry.registerModelLayer(STATUE_LAYER, StatueModel::data);
        EntityModelLayerRegistry.registerModelLayer(THIEF_LAYER, ThiefModel::data);
        EntityRendererRegistry.register(Statues.TYPE, StatueRenderer::new);
        EntityRendererRegistry.register(Thieves.TYPE, ThiefRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(Fear.PACKET, (client, handler, buf, sender) -> {
            int lvl = buf.readVarInt();
            client.execute(() -> fear = lvl);
        });
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (fear <= 0) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
            // Vignette : bords assombris, de plus en plus épais et rouges avec la peur.
            int alpha = Math.min(200, 40 + fear * 16), thick = Math.min(w / 3, 20 + fear * 10);
            int dark = (alpha << 24) | (fear >= 7 ? 0x200000 : 0x000000), clear = 0x00000000;
            context.fillGradient(0, 0, w, thick, dark, clear);
            context.fillGradient(0, h - thick, w, h, clear, dark);
            for (int i = 0; i < thick; i += 2) {
                int a = (int) (alpha * (1 - i / (double) thick));
                int col = (a << 24) | (fear >= 7 ? 0x200000 : 0x000000);
                context.fill(i, 0, i + 2, h, col);
                context.fill(w - i - 2, 0, w - i, h, col);
            }
            if (mc.player != null && fear >= 4) {
                context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("PEUR " + fear).formatted(fear >= 7 ? Formatting.DARK_RED : Formatting.GRAY),
                        w / 2, h / 2 + 30, 0xFFFFFFFF);
            }
        });
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (RaidWorld.isLoot(stack)) lines.add(Text.translatable("tooltip.survierpg.raid_loot").formatted(Formatting.DARK_RED, Formatting.ITALIC));
        });
    }

    static void reset() {
        fear = 0;
    }

    // ------------------------------------------------------------------ statue

    /** Ange de pierre : tête, corps, robe, bras levés devant le visage, deux ailes. 2 blocs, agrandi × 1,5 au rendu. */
    static final class StatueModel extends EntityModel<StatueEntity> {
        private final ModelPart root;

        StatueModel(ModelPart root) {
            this.root = root;
        }

        static TexturedModelData data() {
            ModelData d = new ModelData();
            ModelPartData r = d.getRoot();
            r.addChild("head", ModelPartBuilder.create().uv(0, 0).cuboid(-4, -8, -4, 8, 8, 8), ModelTransform.pivot(0, 0, 0));
            r.addChild("body", ModelPartBuilder.create().uv(16, 16).cuboid(-4, 0, -2, 8, 12, 4), ModelTransform.pivot(0, 0, 0));
            r.addChild("robe", ModelPartBuilder.create().uv(0, 32).cuboid(-5, 12, -3, 10, 12, 6), ModelTransform.pivot(0, 0, 0));
            // Bras levés, mains devant le visage.
            r.addChild("right_arm", ModelPartBuilder.create().uv(40, 16).cuboid(-2, -2, -2, 3, 11, 3),
                    ModelTransform.of(-4.5f, 1.5f, -1f, -2.55f, -0.35f, 0.25f));
            r.addChild("left_arm", ModelPartBuilder.create().uv(40, 16).mirrored().cuboid(-1, -2, -2, 3, 11, 3),
                    ModelTransform.of(4.5f, 1.5f, -1f, -2.55f, 0.35f, -0.25f));
            r.addChild("right_wing", ModelPartBuilder.create().uv(32, 32).cuboid(-0.5f, -3, 0, 1, 20, 10),
                    ModelTransform.of(-2.5f, 1f, 2f, 0.25f, -0.55f, 0.1f));
            r.addChild("left_wing", ModelPartBuilder.create().uv(32, 32).mirrored().cuboid(-0.5f, -3, 0, 1, 20, 10),
                    ModelTransform.of(2.5f, 1f, 2f, 0.25f, 0.55f, -0.1f));
            return TexturedModelData.of(d, 64, 64);
        }

        @Override
        public void setAngles(StatueEntity entity, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            // Immobile : une statue ne bouge jamais un membre.
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a) {
            root.render(matrices, vertices, light, overlay, r, g, b, a);
        }
    }

    static final class StatueRenderer extends MobEntityRenderer<StatueEntity, StatueModel> {
        private static final Identifier TEXTURE = new Identifier(SurvieRpg.MOD_ID, "textures/entity/statue.png");

        StatueRenderer(EntityRendererFactory.Context ctx) {
            super(ctx, new StatueModel(ctx.getPart(STATUE_LAYER)), 0.7f);
        }

        @Override
        protected void scale(StatueEntity entity, MatrixStack matrices, float amount) {
            matrices.scale(1.5f, 1.5f, 1.5f);
        }

        @Override
        public Identifier getTexture(StatueEntity entity) {
            return TEXTURE;
        }
    }

    // ------------------------------------------------------------------ voleur

    /** Petit voleur rond : gros yeux, oreilles, pattes, sacoche. */
    static final class ThiefModel extends EntityModel<ThiefEntity> {
        private final ModelPart root, leftLeg, rightLeg, body;

        ThiefModel(ModelPart root) {
            this.root = root;
            this.body = root.getChild("body");
            this.leftLeg = root.getChild("left_leg");
            this.rightLeg = root.getChild("right_leg");
        }

        static TexturedModelData data() {
            ModelData d = new ModelData();
            ModelPartData r = d.getRoot();
            ModelPartData body = r.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-3, -6, -3, 6, 6, 6), ModelTransform.pivot(0, 21, 0));
            body.addChild("left_ear", ModelPartBuilder.create().uv(24, 0).cuboid(0, -3, 0, 2, 3, 1), ModelTransform.of(1, -6, 0, 0, 0, 0.3f));
            body.addChild("right_ear", ModelPartBuilder.create().uv(24, 0).mirrored().cuboid(-2, -3, 0, 2, 3, 1), ModelTransform.of(-1, -6, 0, 0, 0, -0.3f));
            body.addChild("bag", ModelPartBuilder.create().uv(0, 12).cuboid(0, 0, -1.5f, 2, 3, 3), ModelTransform.pivot(3, -3, 0));
            r.addChild("left_leg", ModelPartBuilder.create().uv(12, 12).cuboid(-1, 0, -1, 2, 3, 2), ModelTransform.pivot(1.5f, 21, 0));
            r.addChild("right_leg", ModelPartBuilder.create().uv(12, 12).mirrored().cuboid(-1, 0, -1, 2, 3, 2), ModelTransform.pivot(-1.5f, 21, 0));
            return TexturedModelData.of(d, 32, 32);
        }

        @Override
        public void setAngles(ThiefEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            leftLeg.pitch = MathHelper.cos(limbAngle * 0.9f) * 1.3f * limbDistance;
            rightLeg.pitch = MathHelper.cos(limbAngle * 0.9f + (float) Math.PI) * 1.3f * limbDistance;
            body.roll = MathHelper.sin(limbAngle * 0.9f) * 0.12f * limbDistance;
            body.pivotY = 21 - Math.abs(MathHelper.sin(age * 0.15f)) * 0.4f;
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a) {
            root.render(matrices, vertices, light, overlay, r, g, b, a);
        }
    }

    static final class ThiefRenderer extends MobEntityRenderer<ThiefEntity, ThiefModel> {
        private static final Identifier TEXTURE = new Identifier(SurvieRpg.MOD_ID, "textures/entity/chapardeur.png");

        ThiefRenderer(EntityRendererFactory.Context ctx) {
            super(ctx, new ThiefModel(ctx.getPart(THIEF_LAYER)), 0.25f);
            addFeature(new LootFeature(this));
        }

        @Override
        public Identifier getTexture(ThiefEntity entity) {
            return TEXTURE;
        }
    }

    /** L'objet volé, porté au-dessus de la tête. */
    static final class LootFeature extends FeatureRenderer<ThiefEntity, ThiefModel> {
        LootFeature(FeatureRendererContext<ThiefEntity, ThiefModel> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, ThiefEntity e, float limbAngle, float limbDistance,
                           float tickDelta, float age, float headYaw, float headPitch) {
            var stack = e.getMainHandStack();
            if (stack.isEmpty()) return;
            matrices.push();
            matrices.translate(0, 0.65, 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 4));
            matrices.scale(0.45f, 0.45f, 0.45f);
            MinecraftClient.getInstance().getItemRenderer().renderItem(stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
                    matrices, vcp, e.getWorld(), e.getId());
            matrices.pop();
        }
    }
}
