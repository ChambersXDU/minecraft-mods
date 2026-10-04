package dev.aviation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A 512 x 320 metre airfield, with a 480 x 32 metre runway and connected facilities. */
public final class AirportLayout {
    public record Placement(BlockPos pos, BlockState state) {}
    private static List<Placement> cached;
    private final List<Placement> blocks = new ArrayList<>();
    private void at(int x, int y, int z, Block block) { blocks.add(new Placement(new BlockPos(x, y, z), block.defaultBlockState())); }
    private void box(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) for (int y = y1; y <= y2; y++) at(x, y, z, block);
    }
    private void building(int x, int z, int width, int depth, int height, boolean glass) {
        box(x, 63, z, x + width, 63, z + depth, Blocks.SMOOTH_QUARTZ);
        box(x, 64 + height, z, x + width, 64 + height, z + depth, Blocks.SMOOTH_QUARTZ);
        for (int y = 64; y < 64 + height; y++) {
            Block facade = glass && y >= 66 && y <= 64 + height - 2 ? Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE) : Blocks.CONCRETE.pick(DyeColor.WHITE);
            box(x, y, z, x + width, y, z, facade);
            box(x, y, z + depth, x + width, y, z + depth, facade);
            box(x, y, z, x, y, z + depth, facade);
            box(x + width, y, z, x + width, y, z + depth, facade);
        }
        for (int i = 0; i <= width; i += 12) box(x + i, 64, z, x + i, 64 + height, z, Blocks.SMOOTH_QUARTZ);
        for (int i = 6; i < width; i += 12) at(x + i, 64 + height - 1, z + depth / 2, AviationMod.LIGHT);
    }
    private void glyph(String[] pattern, int x, int z, boolean reverse) {
        for (int row = 0; row < pattern.length; row++) for (int col = 0; col < pattern[row].length(); col++) {
            if (pattern[row].charAt(col) == '1') {
                int dx = reverse ? -row * 2 : row * 2;
                int dz = reverse ? -col * 2 : col * 2;
                box(x + dx, 63, z + dz, x + dx + 1, 63, z + dz + 1, AviationMod.WHITE);
            }
        }
    }
    private void build() {
        // Runway 09/27, parallel taxiway, two connecting taxiways and the apron.
        box(16, 63, 48, 495, 63, 79, AviationMod.RUNWAY);
        box(16, 63, 96, 495, 63, 109, AviationMod.RUNWAY);
        box(46, 63, 80, 59, 63, 150, AviationMod.RUNWAY);
        box(450, 63, 80, 463, 63, 150, AviationMod.RUNWAY);
        box(96, 63, 118, 335, 63, 194, AviationMod.APRON);
        box(52, 63, 103, 457, 63, 104, AviationMod.YELLOW);
        box(52, 63, 80, 53, 63, 149, AviationMod.YELLOW);
        box(456, 63, 80, 457, 63, 149, AviationMod.YELLOW);
        box(52, 63, 148, 335, 63, 149, AviationMod.YELLOW);
        box(16, 63, 49, 495, 63, 49, AviationMod.WHITE);
        box(16, 63, 78, 495, 63, 78, AviationMod.WHITE);
        for (int x = 48; x < 466; x += 20) box(x, 63, 63, x + 7, 63, 64, AviationMod.WHITE);
        // Threshold piano keys and touchdown-zone stripes, sized for the runway rather than vanilla road markings.
        for (int z = 52; z <= 74; z += 4) {
            box(20, 63, z, 33, 63, z + 1, AviationMod.WHITE);
            box(478, 63, z, 491, 63, z + 1, AviationMod.WHITE);
        }
        for (int x : new int[]{82, 120, 158, 354, 392, 430}) {
            box(x, 63, 54, x + 12, 63, 56, AviationMod.WHITE);
            box(x, 63, 71, x + 12, 63, 73, AviationMod.WHITE);
        }
        glyph(new String[]{"111","101","101","101","111"}, 40, 53, false);
        glyph(new String[]{"111","101","111","001","111"}, 40, 66, false);
        glyph(new String[]{"111","001","111","100","111"}, 470, 74, true);
        glyph(new String[]{"111","001","001","010","010"}, 470, 61, true);
        for (int x = 16; x <= 495; x += 12) { at(x, 63, 47, AviationMod.LIGHT); at(x, 63, 80, AviationMod.LIGHT); }
        for (int x = 40; x <= 475; x += 24) at(x, 63, 64, AviationMod.LIGHT);
        // Terminal: glass curtain wall, entrance halls, piers, lounges, baggage area, canopy and jet bridges.
        building(100, 210, 220, 42, 15, true);
        for (int x : new int[]{120, 190, 260}) {
            box(x, 64, 210, x + 9, 69, 210, Blocks.AIR);
            box(x - 2, 71, 200, x + 11, 71, 219, Blocks.SMOOTH_QUARTZ);
            box(x, 64, 250, x + 9, 69, 252, Blocks.AIR);
        }
        for (int x = 108; x < 312; x += 16) {
            box(x, 63, 218, x + 9, 63, 243, Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY));
            box(x, 64, 223, x + 8, 64, 223, Blocks.CONCRETE.pick(DyeColor.GRAY));
            box(x, 64, 235, x + 8, 64, 235, Blocks.CONCRETE.pick(DyeColor.GRAY));
            at(x + 4, 65, 244, Blocks.LANTERN);
        }
        for (int x : new int[]{116, 160, 204, 248, 292}) {
            building(x, 187, 8, 22, 5, true);
            box(x + 1, 64, 187, x + 7, 67, 187, Blocks.AIR);
            box(x + 2, 63, 155, x + 2, 63, 185, AviationMod.YELLOW);
            box(x - 7, 63, 165, x + 15, 63, 165, AviationMod.YELLOW);
            for (int y = 64; y <= 68; y++) { at(x, y, 205, Blocks.SMOOTH_QUARTZ); at(x + 8, y, 205, Blocks.SMOOTH_QUARTZ); }
        }
        // Control tower, access stair, radar mast, equipment hut and two open-front hangars.
        building(355, 211, 14, 14, 29, false);
        building(350, 225, 24, 20, 7, false);
        box(351, 91, 207, 373, 91, 229, Blocks.SMOOTH_QUARTZ);
        for (int y = 92; y <= 97; y++) {
            box(351, y, 207, 373, y, 207, Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE));
            box(351, y, 229, 373, y, 229, Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE));
            box(351, y, 207, 351, y, 229, Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE));
            box(373, y, 207, 373, y, 229, Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE));
        }
        box(350, 98, 206, 374, 98, 230, Blocks.SMOOTH_QUARTZ);
        box(361, 99, 217, 361, 105, 217, Blocks.IRON_BLOCK);
        box(352, 104, 216, 370, 105, 218, Blocks.IRON_BLOCK);
        for (int x : new int[]{402, 456}) {
            building(x, 185, 42, 50, 18, false);
            box(x + 3, 64, 185, x + 39, 79, 185, Blocks.AIR);
            box(x, 63, 125, x + 42, 63, 184, AviationMod.APRON);
            box(x + 20, 63, 125, x + 21, 63, 184, AviationMod.YELLOW);
        }
        // Landside access road, car parking, pedestrian crossing and perimeter fence.
        box(80, 63, 262, 335, 63, 292, AviationMod.RUNWAY);
        box(80, 63, 277, 335, 63, 277, AviationMod.YELLOW);
        for (int x = 86; x <= 310; x += 12) box(x, 63, 265, x, 63, 273, AviationMod.WHITE);
        for (int x = 182; x <= 198; x += 3) box(x, 63, 274, x + 1, 63, 284, AviationMod.WHITE);
        for (int x = 0; x <= 511; x++) { at(x, 64, 24, Blocks.IRON_BARS); at(x, 64, 306, Blocks.IRON_BARS); }
        for (int z = 24; z <= 306; z++) { at(0, 64, z, Blocks.IRON_BARS); at(511, 64, z, Blocks.IRON_BARS); }
        // Public arrival plaza and return gate. The runway remains clear for takeoff.
        box(230, 63, 196, 249, 63, 207, Blocks.SMOOTH_QUARTZ);
        at(240, 64, 199, AviationMod.GATE);
        at(238, 64, 199, AviationMod.LIGHT); at(242, 64, 199, AviationMod.LIGHT);
        // Append docking corridors so older airports can build only these new tail entries.
        for (int center : PassengerBoarding.BRIDGE_CENTERS) {
            box(center + 2, 63, 180, center + 4, 63, 186, Blocks.SMOOTH_QUARTZ);
            box(center + 2, 69, 180, center + 4, 69, 186, Blocks.SMOOTH_QUARTZ);
            box(center + 2, 64, 180, center + 2, 68, 186, Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE));
            box(center + 4, 64, 180, center + 4, 68, 186, Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE));
            box(center + 2, 64, 180, center + 2, 67, 182, Blocks.AIR);
        }
    }
    public static List<Placement> plan() {
        if (cached == null) { var layout = new AirportLayout(); layout.build(); cached = List.copyOf(layout.blocks); }
        return cached;
    }
}
