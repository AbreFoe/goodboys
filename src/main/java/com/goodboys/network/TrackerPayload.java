package com.goodboys.network;

import java.util.List;

import com.goodboys.GoodBoys;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Server to client: status of the player's dogs that wear a tracker, sent once a second. */
public record TrackerPayload(List<Entry> dogs) implements CustomPacketPayload {
    public record Entry(Component name, int level, float health, float maxHealth, double x, double y, double z,
                        ResourceKey<Level> dimension, boolean downed) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, e) -> {
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, e.name());
                    buf.writeVarInt(e.level());
                    buf.writeFloat(e.health());
                    buf.writeFloat(e.maxHealth());
                    buf.writeDouble(e.x());
                    buf.writeDouble(e.y());
                    buf.writeDouble(e.z());
                    buf.writeResourceKey(e.dimension());
                    buf.writeBoolean(e.downed());
                },
                buf -> new Entry(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readResourceKey(Registries.DIMENSION), buf.readBoolean()));
    }

    public static final Type<TrackerPayload> TYPE = new Type<>(GoodBoys.id("tracker"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrackerPayload> STREAM_CODEC =
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(TrackerPayload::new, TrackerPayload::dogs);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
