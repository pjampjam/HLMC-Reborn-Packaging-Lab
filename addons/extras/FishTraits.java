package holylois.boombox;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a trophy fish (Rare and up) does, by species: an effect when eaten (some are bad, a few none: pure trophies) and
 * sometimes a bonus while held in the main hand. Rarity, size and Shiny make it stronger and longer; cooking adds half again.
 */
public final class FishTraits {
    private FishTraits() {}

    /** eat: effect when eaten (null = trophy only); bad: it hurts; held: attribute while in the main hand (null = none). */
    record Trait(Holder<MobEffect> eat, Holder<MobEffect> extra, boolean bad, Holder<Attribute> held, double amount, String heldOp) {}

    static final Map<String, Trait> SPECIES = Map.ofEntries(
        Map.entry("minecraft:cod", new Trait(MobEffects.HASTE, null, false, null, 0, null)),
        Map.entry("minecraft:salmon", new Trait(MobEffects.STRENGTH, null, false, null, 0, null)),
        Map.entry("minecraft:tropical_fish", new Trait(MobEffects.NIGHT_VISION, null, false, null, 0, null)),
        Map.entry("minecraft:pufferfish", new Trait(MobEffects.POISON, MobEffects.NAUSEA, true, Attributes.ARMOR, 1, "add")),
        Map.entry("fishofthieves:splashtail", new Trait(MobEffects.SPEED, null, false, Attributes.MOVEMENT_SPEED, 0.05, "base")),
        Map.entry("fishofthieves:pondie", new Trait(MobEffects.REGENERATION, null, false, null, 0, null)),
        Map.entry("fishofthieves:islehopper", new Trait(MobEffects.JUMP_BOOST, null, false, Attributes.JUMP_STRENGTH, 0.06, "add")),
        Map.entry("fishofthieves:ancientscale", new Trait(MobEffects.RESISTANCE, null, false, Attributes.ARMOR, 3, "add")),
        Map.entry("fishofthieves:plentifin", new Trait(MobEffects.SATURATION, null, false, null, 0, null)),
        Map.entry("fishofthieves:wildsplash", new Trait(MobEffects.DOLPHINS_GRACE, null, false, Attributes.WATER_MOVEMENT_EFFICIENCY, 0.3, "add")),
        Map.entry("fishofthieves:devilfish", new Trait(MobEffects.FIRE_RESISTANCE, MobEffects.WEAKNESS, false, null, 0, null)),
        Map.entry("fishofthieves:battlegill", new Trait(MobEffects.STRENGTH, null, false, Attributes.ATTACK_DAMAGE, 2, "add")),
        Map.entry("fishofthieves:wrecker", new Trait(null, null, false, Attributes.KNOCKBACK_RESISTANCE, 0.2, "add")),
        Map.entry("fishofthieves:stormfish", new Trait(MobEffects.SLOW_FALLING, MobEffects.SPEED, false, null, 0, null)));
    static final Trait FALLBACK = new Trait(MobEffects.SATURATION, null, false, null, 0, null);

    static int tier(String rarity) { return switch (rarity) { case "rare" -> 1; case "epic" -> 2; case "legendary" -> 3; case "mythic" -> 4; default -> 0; }; }

    /** Seconds of the eaten effect: 30 s for a small Rare up to 8 minutes for a big Mythic; Shiny and cooked add more. */
    static int seconds(String rarity, double size, boolean shiny, boolean cooked) {
        double base = switch (tier(rarity)) { case 1 -> 30; case 2 -> 60; case 3 -> 120; case 4 -> 240; default -> 0; };
        double grow = 0.75 + Math.min(1, Math.max(0, size)) * 0.5;
        return (int) Math.round(base * grow * (shiny ? 2 : 1) * (cooked ? 1.5 : 1));
    }

    static int amplifier(String rarity) { return switch (tier(rarity)) { case 3 -> 1; case 4 -> 2; default -> 0; }; }

    /** Writes the eat effects, the held bonus and their tooltip lines; returns the lines (empty for Common/Uncommon). */
    static List<Component> apply(ItemStack stack, String species, String rarity, double size, boolean shiny, boolean cooked) {
        var lines = new ArrayList<Component>();
        if (tier(rarity) == 0) return lines;
        var trait = SPECIES.getOrDefault(species, FALLBACK);
        var food = stack.get(DataComponents.CONSUMABLE);
        if (trait.eat() != null && food != null) {
            int ticks = seconds(rarity, size, shiny, cooked) * 20, level = amplifier(rarity);
            var effects = new ArrayList<MobEffectInstance>();
            // Instant-ish effects (Saturation) would be absurd for minutes: a few seconds of them, by tier.
            boolean instant = trait.eat().value().isInstantaneous() || trait.eat() == MobEffects.SATURATION;
            effects.add(new MobEffectInstance(trait.eat(), instant ? 20 * (2 + tier(rarity)) : ticks, level));
            if (trait.extra() != null) effects.add(new MobEffectInstance(trait.extra(), ticks / 2, 0));
            var onEat = new ArrayList<>(food.onConsumeEffects());
            onEat.add(new ApplyStatusEffectsConsumeEffect(effects));
            stack.set(DataComponents.CONSUMABLE, new Consumable(food.consumeSeconds(), food.animation(), food.sound(), food.hasConsumeParticles(), onEat));
            lines.add(Component.literal("When eaten:").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
            for (var effect : effects) {
                boolean hurts = trait.bad() || effect.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL;
                var line = Component.literal(" ").append(Component.translatable(effect.getEffect().value().getDescriptionId()))
                    .append((effect.getAmplifier() > 0 ? " " + roman(effect.getAmplifier() + 1) : "") + " (" + time(effect.getDuration() / 20) + ")");
                lines.add(line.withStyle(s -> s.withColor(hurts ? ChatFormatting.RED : ChatFormatting.BLUE).withItalic(false)));
            }
        } else if (trait.eat() == null) {
            lines.add(Component.literal("A pure trophy: no effect when eaten").withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY).withItalic(true)));
        }
        if (trait.held() != null) {
            double times = switch (tier(rarity)) { case 2 -> 1.5; case 3 -> 2; case 4 -> 3; default -> 1; } * (shiny ? 1.5 : 1);
            var operation = "base".equals(trait.heldOp()) ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE;
            var modifier = new AttributeModifier(Identifier.fromNamespaceAndPath("holylois", "trophy_fish"), Math.round(trait.amount() * times * 100) / 100.0, operation);
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder().add(trait.held(), modifier, EquipmentSlotGroup.MAINHAND).build());
        }
        return lines;
    }

    static String time(int seconds) { return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60); }

    static String roman(int n) { return switch (n) { case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n); }; }
}
