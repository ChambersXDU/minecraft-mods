package dev.aviation.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class PlaneModel extends EntityModel<PlaneRenderState> {
    private final ModelPart propeller;
    public PlaneModel(boolean fighter) { this(fighter ? 1 : 0); }
    public PlaneModel(int kind) { this(make(kind)); }
    private PlaneModel(ModelPart root) { super(root); propeller = root.getChild("propeller"); }
    private static void cube(PartDefinition root, String name, int u, int v, float x, float y, float z, float w, float h, float d) {
        root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d), PartPose.ZERO);
    }
    private static ModelPart make(int kind) {
        boolean fighter = kind == 1, airliner = kind == 2;
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        if (airliner) {
            cube(root, "body", 0, 0, -20, -48, -90, 40, 28, 180);
            cube(root, "nose", 0, 0, -14, -45, -118, 28, 25, 28);
            cube(root, "cockpit", 600, 0, -15, -54, -109, 30, 10, 30);
            for (int i = 0; i < 7; i++) {
                cube(root, "windowLeft" + i, 600, 0, -20.5F, -41, -66 + i * 21, 1, 8, 10);
                cube(root, "windowRight" + i, 600, 0, 19.5F, -41, -66 + i * 21, 1, 8, 10);
            }
            root.addOrReplaceChild("leftWing", CubeListBuilder.create().texOffs(0, 512).addBox(-112, -31, -22, 92, 4, 42),
                    PartPose.rotation(0, -0.18F, 0));
            root.addOrReplaceChild("rightWing", CubeListBuilder.create().texOffs(0, 512).addBox(20, -31, -22, 92, 4, 42),
                    PartPose.rotation(0, 0.18F, 0));
            cube(root, "tail", 0, 0, -10, -42, 90, 20, 22, 26);
            cube(root, "tailWings", 0, 512, -45, -34, 78, 90, 4, 28);
            cube(root, "fin", 0, 512, -3, -91, 68, 6, 55, 44);
            cube(root, "engineLeft", 700, 0, -62, -27, -31, 18, 18, 43);
            cube(root, "engineRight", 700, 0, 44, -27, -31, 18, 18, 43);
            cube(root, "door", 700, 0, 19.6F, -42, 52, 1, 19, 12);
        } else if (fighter) {
            cube(root, "body", 0, 0, -7, -24, -44, 14, 12, 84);
            cube(root, "nose", 0, 0, -4, -22, -64, 8, 8, 20);
            cube(root, "cockpit", 128, 0, -6, -34, -22, 12, 10, 26);
            root.addOrReplaceChild("leftWing", CubeListBuilder.create().texOffs(0, 128).addBox(-56, -21, -8, 49, 3, 26),
                    PartPose.rotation(0, -0.20F, 0));
            root.addOrReplaceChild("rightWing", CubeListBuilder.create().texOffs(0, 128).addBox(7, -21, -8, 49, 3, 26),
                    PartPose.rotation(0, 0.20F, 0));
            cube(root, "tailWings", 0, 128, -26, -22, 30, 52, 3, 15);
            cube(root, "fin", 0, 128, -2, -49, 27, 4, 27, 20);
            cube(root, "engine", 192, 0, -6, -23, 40, 12, 10, 9);
            cube(root, "gunLeft", 192, 0, -12, -19, -35, 4, 4, 22);
            cube(root, "gunRight", 192, 0, 8, -19, -35, 4, 4, 22);
        } else {
            cube(root, "body", 0, 0, -9, -28, -38, 18, 17, 70);
            cube(root, "nose", 0, 0, -7, -25, -48, 14, 13, 10);
            cube(root, "cockpit", 128, 0, -8, -34, -30, 16, 7, 20);
            cube(root, "leftWindows", 128, 0, -9.3F, -25, -6, 1, 6, 24);
            cube(root, "rightWindows", 128, 0, 8.3F, -25, -6, 1, 6, 24);
            cube(root, "wings", 0, 128, -65, -27, -8, 130, 3, 26);
            cube(root, "strutLeft", 192, 0, -35, -24, 1, 2, 13, 2);
            cube(root, "strutRight", 192, 0, 33, -24, 1, 2, 13, 2);
            cube(root, "tail", 0, 0, -5, -24, 32, 10, 10, 26);
            cube(root, "tailWings", 0, 128, -27, -22, 44, 54, 3, 14);
            cube(root, "fin", 0, 128, -2, -47, 36, 4, 25, 24);
        }
        cube(root, "wheelLeft", 0, 220, -14, -6, 6, 6, 6, 10);
        cube(root, "wheelRight", 0, 220, 8, -6, 6, 6, 6, 10);
        cube(root, "wheelFront", 0, 220, -3, -6, -27, 6, 6, 8);
        cube(root, "gearLeft", 192, 0, -11, -14, 9, 2, 8, 3);
        cube(root, "gearRight", 192, 0, 9, -14, 9, 2, 8, 3);
        cube(root, "gearFront", 192, 0, -1, -13, -25, 2, 7, 3);
        float windshieldZ = airliner ? -108 : fighter ? -22 : -30;
        float windshieldX = airliner ? 14 : fighter ? 6 : 8;
        float windshieldY = airliner ? -55 : -35;
        cube(root, "windshieldTop", 0, 0, -windshieldX - 1, windshieldY, windshieldZ,
                windshieldX * 2 + 2, 1, 1);
        cube(root, "windshieldLeft", 0, 0, -windshieldX - 1, windshieldY, windshieldZ, 1, airliner ? 14 : 10, 1);
        cube(root, "windshieldRight", 0, 0, windshieldX, windshieldY, windshieldZ, 1, airliner ? 14 : 10, 1);
        cube(root, "instrumentPanel", airliner ? 600 : 0, airliner ? 200 : 220, -windshieldX, airliner ? -41 : -26,
                windshieldZ + 1, windshieldX * 2, 3, 3);
        root.addOrReplaceChild("propeller", fighter || airliner ? CubeListBuilder.create() : CubeListBuilder.create().texOffs(0, 220)
                .addBox(-17, -2, -1, 34, 4, 2).addBox(-2, -17, -1, 4, 34, 2), PartPose.offset(0, -19, -50));
        return LayerDefinition.create(mesh, airliner ? 1024 : 512, airliner ? 1024 : 512).bakeRoot();
    }
    @Override public void setupAnim(PlaneRenderState state) {
        super.setupAnim(state); propeller.zRot = state.propeller;
        propeller.visible = !state.cockpitView;
        root().getChild("cockpit").visible = !state.cockpitView;
    }
}
