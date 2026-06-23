package dev.attuned.api.focus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** One declarative attribute modifier a Focus contributes while it is active. */
public record ModifierEntry(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
	private static final double MIN_AMOUNT = -1024.0D;
	private static final double MAX_AMOUNT = 1024.0D;
	private static final Codec<Double> BOUNDED_AMOUNT_CODEC =
		Codec.DOUBLE.flatXmap(ModifierEntry::validateAmount, ModifierEntry::validateAmount);
	private static final Codec<AttributeModifier.Operation> OPERATION_CODEC =
		Codec.STRING.flatXmap(ModifierEntry::operationByName,
			operation -> DataResult.success(operationName(operation)));

	public ModifierEntry {
		attribute = Objects.requireNonNull(attribute, "attribute");
		operation = Objects.requireNonNull(operation, "operation");
		if (!Double.isFinite(amount)) {
			throw new IllegalArgumentException("Modifier amount must be finite");
		}
		if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
			throw new IllegalArgumentException(
				"Modifier amount must be between " + MIN_AMOUNT + " and " + MAX_AMOUNT);
		}
	}

	private static DataResult<Double> validateAmount(double amount) {
		if (!Double.isFinite(amount)) {
			return DataResult.error("Modifier amount must be finite");
		}
		if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
			return DataResult.error("Modifier amount must be between " + MIN_AMOUNT + " and " + MAX_AMOUNT);
		}
		return DataResult.success(amount);
	}

	private static DataResult<AttributeModifier.Operation> operationByName(String name) {
		return switch (name) {
			case "add_value", "addition" -> DataResult.success(AttributeModifier.Operation.ADDITION);
			case "add_multiplied_base", "multiply_base" -> DataResult.success(AttributeModifier.Operation.MULTIPLY_BASE);
			case "add_multiplied_total", "multiply_total" -> DataResult.success(AttributeModifier.Operation.MULTIPLY_TOTAL);
			default -> DataResult.error("Unknown attribute modifier operation: " + name);
		};
	}

	private static String operationName(AttributeModifier.Operation operation) {
		return switch (operation) {
			case ADDITION -> "add_value";
			case MULTIPLY_BASE -> "add_multiplied_base";
			case MULTIPLY_TOTAL -> "add_multiplied_total";
		};
	}

	public static final Codec<ModifierEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("attribute").forGetter(ModifierEntry::attribute),
		BOUNDED_AMOUNT_CODEC.fieldOf("amount").forGetter(ModifierEntry::amount),
		OPERATION_CODEC.fieldOf("operation").forGetter(ModifierEntry::operation)
	).apply(instance, ModifierEntry::new));
}
