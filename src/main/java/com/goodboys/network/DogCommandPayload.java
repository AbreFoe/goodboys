package com.goodboys.network;

import com.goodboys.GoodBoys;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the owner picked a command in the dog menu.
 *
 * @param command {@link com.goodboys.entity.DogCommand} ordinal
 * @param dogId   entity id of the dog the owner was looking at, or -1 for the whole pack nearby
 */
public record DogCommandPayload(int command, int dogId) implements CustomPacketPayload {
    public static final int WHOLE_PACK = -1;

    public static final Type<DogCommandPayload> TYPE = new Type<>(GoodBoys.id("dog_command"));
    public static final StreamCodec<ByteBuf, DogCommandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DogCommandPayload::command,
            ByteBufCodecs.VAR_INT, DogCommandPayload::dogId,
            DogCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
