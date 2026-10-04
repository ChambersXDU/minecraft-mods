package dev.aviation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class AirportData extends SavedData {
    public record ReturnPoint(String dimension, double x, double y, double z, float yaw, float pitch) {
        public static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dimension").forGetter(ReturnPoint::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(ReturnPoint::x), Codec.DOUBLE.fieldOf("y").forGetter(ReturnPoint::y),
                Codec.DOUBLE.fieldOf("z").forGetter(ReturnPoint::z), Codec.FLOAT.fieldOf("yaw").forGetter(ReturnPoint::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(ReturnPoint::pitch)).apply(instance, ReturnPoint::new));
    }
    public int cursor;
    public boolean commissioned;
    public boolean airlinerCommissioned;
    public final Map<String, ReturnPoint> returns;
    public AirportData() { this(0, false, false, Map.of()); }
    public AirportData(int cursor, boolean commissioned, boolean airlinerCommissioned, Map<String, ReturnPoint> returns) {
        this.cursor = cursor; this.commissioned = commissioned;
        this.airlinerCommissioned = airlinerCommissioned; this.returns = new HashMap<>(returns);
    }
    public static final Codec<AirportData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("cursor", 0).forGetter(data -> data.cursor),
            Codec.BOOL.optionalFieldOf("commissioned", false).forGetter(data -> data.commissioned),
            Codec.BOOL.optionalFieldOf("airliner_commissioned", false).forGetter(data -> data.airlinerCommissioned),
            Codec.unboundedMap(Codec.STRING, ReturnPoint.CODEC).optionalFieldOf("returns", Map.of()).forGetter(data -> data.returns)
    ).apply(instance, AirportData::new));
    public static final SavedDataType<AirportData> TYPE = new SavedDataType<>(AviationMod.id("airport_data"), AirportData::new, CODEC, null);
}
