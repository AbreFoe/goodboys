package com.goodboys.item;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Dog toys. Right-click throws one; dogs nearby notice toys lying around and play with them. */
public class DogToyItem extends Item {
    public enum Toy {
        /** Fetch: the dog brings it back to whoever threw it. */
        TENNIS_BALL(1.2),
        /** The dog lies down and chews on it for a while. */
        CHEW_BONE(0.5),
        /** The dog shakes it, tosses it and pounces on it. */
        ROPE_TOY(0.6);

        private final double throwSpeed;

        Toy(double throwSpeed) {
            this.throwSpeed = throwSpeed;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Toy toy;

    public DogToyItem(Toy toy, Properties properties) {
        super(properties.stacksTo(16));
        this.toy = toy;
    }

    public Toy toy() {
        return toy;
    }

    public static Toy toyOf(ItemStack stack) {
        return stack.getItem() instanceof DogToyItem item ? item.toy : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Vec3 look = player.getLookAngle();
            ItemEntity thrown = new ItemEntity(level, player.getX(), player.getEyeY() - 0.2, player.getZ(), stack.copyWithCount(1));
            thrown.setDeltaMovement(look.scale(toy.throwSpeed).add(0, 0.18, 0));
            thrown.setPickUpDelay(40);
            thrown.setThrower(player);
            level.addFreshEntity(thrown);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 0.6F);
        }
        player.getCooldowns().addCooldown(this, 10);
        stack.consume(1, player);
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.goodboys." + toy.id() + ".info").withStyle(ChatFormatting.GRAY));
    }
}
