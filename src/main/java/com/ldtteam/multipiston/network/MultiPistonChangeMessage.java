package com.ldtteam.multipiston.network;

import com.ldtteam.multipiston.MultiPiston;
import com.ldtteam.multipiston.TileEntityMultiPiston;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Serverbound update for a multi-piston block entity.
 */
public record MultiPistonChangeMessage(
    BlockPos pos,
    Direction input,
    Direction output,
    int range,
    int speed
) implements CustomPacketPayload
{
    public static final Type<MultiPistonChangeMessage> ID =
        new Type<>(Identifier.fromNamespaceAndPath(MultiPiston.MOD_ID, "change_message"));

    /**
     * Extra reach beyond the player's block interaction range, the same buffer vanilla
     * containers allow before closing (Container#stillValidBlockEntity). The window stays
     * open while the player moves, so a packet from a few blocks further off is legitimate.
     */
    private static final double CONTAINER_REACH_BUFFER = 4.0D;

    public static final StreamCodec<RegistryFriendlyByteBuf, MultiPistonChangeMessage> CODEC =
        CustomPacketPayload.codec(MultiPistonChangeMessage::write, MultiPistonChangeMessage::read);

    private MultiPistonChangeMessage(final FriendlyByteBuf buf)
    {
        this(
            buf.readBlockPos(),
            readDirection(buf),
            readDirection(buf),
            buf.readInt(),
            buf.readInt()
        );
    }

    /**
     * Decode a direction defensively. A malformed serverbound payload must be
     * rejected by the handler rather than turning an out-of-range ordinal into
     * an ArrayIndexOutOfBoundsException on the server thread.
     */
    private static Direction readDirection(final FriendlyByteBuf buf)
    {
        final int ordinal = buf.readInt();
        return ordinal >= 0 && ordinal < Direction.values().length ? Direction.values()[ordinal] : null;
    }

    private static MultiPistonChangeMessage read(final FriendlyByteBuf buf)
    {
        return new MultiPistonChangeMessage(buf);
    }

    private void write(final FriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        buf.writeInt(input.ordinal());
        buf.writeInt(output.ordinal());
        buf.writeInt(range);
        buf.writeInt(speed);
    }

    @Override
    public Type<MultiPistonChangeMessage> type()
    {
        return ID;
    }

    public void sendToServer()
    {
        ClientPacketDistributor.sendToServer(this);
    }

    public static void onExecute(final MultiPistonChangeMessage message, final IPayloadContext context)
    {
        if (!(context.player() instanceof final ServerPlayer player)
            || message.pos() == null
            || message.input() == null
            || message.output() == null
            || message.range() < 0
            || message.range() > TileEntityMultiPiston.MAX_RANGE
            || message.speed() < TileEntityMultiPiston.MIN_SPEED
            || message.speed() > TileEntityMultiPiston.MAX_SPEED)
        {
            return;
        }

        context.enqueueWork(() -> applyOnMainThread(message, player));
    }

    private static void applyOnMainThread(final MultiPistonChangeMessage message, final ServerPlayer player)
    {
        final Level world = player.level();
        if (!(world instanceof final ServerLevel serverLevel)
            || !serverLevel.hasChunkAt(message.pos())
            || !player.isWithinBlockInteractionRange(message.pos(), CONTAINER_REACH_BUFFER)
            || !player.mayInteract(serverLevel, message.pos()))
        {
            return;
        }

        final BlockEntity entity = world.getBlockEntity(message.pos());
        if (entity instanceof TileEntityMultiPiston piston)
        {
            piston.setInput(message.input());
            piston.setOutput(message.output());
            piston.setRange(message.range());
            piston.setSpeed(message.speed());
            final BlockState state = world.getBlockState(message.pos());
            world.sendBlockUpdated(message.pos(), state, state, 0x3);
        }
    }
}
