package dev.attuned.api.focus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * One declarative attribute modifier a Focus contributes while it is active.
 * The framework applies and removes these automatically — no cleanup code needed.
 */
public record ModifierEntry(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {

	private static final Codec<Double> FINITE_AMOUNT_CODEC = Codec.DOUBLE.validate(ModifierEntry::validateAmount);

	public ModifierEntry {
		attribute = Objects.requireNonNull(attribute, "attribute");
		operation = Objects.requireNonNull(operation, "operation");
		if (!Double.isFinite(amount)) {
			throw new IllegalArgumentException("Modifier amount must be finite");
		}
	}

	private static DataResult<Double> validateAmount(double amount) {
		if (!Double.isFinite(amount)) {
			return DataResult.error(() -> "Modifier amount must be finite");
		}
		return DataResult.success(amount);
	}

	public static final Codec<ModifierEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("attribute").forGetter(ModifierEntry::attribute),
		FINITE_AMOUNT_CODEC.fieldOf("amount").forGetter(ModifierEntry::amount),
		AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(ModifierEntry::operation)
	).apply(instance, ModifierEntry::new));
}
