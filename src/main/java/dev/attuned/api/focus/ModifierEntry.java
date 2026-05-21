package dev.attuned.api.focus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * One declarative attribute modifier a Focus contributes while it is active.
 * The framework applies and removes these automatically — no cleanup code needed.
 */
public record ModifierEntry(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {

	public static final Codec<ModifierEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("attribute").forGetter(ModifierEntry::attribute),
		Codec.DOUBLE.fieldOf("amount").forGetter(ModifierEntry::amount),
		AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(ModifierEntry::operation)
	).apply(instance, ModifierEntry::new));
}
