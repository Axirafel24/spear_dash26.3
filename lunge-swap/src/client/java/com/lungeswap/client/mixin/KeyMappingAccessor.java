package com.lungeswap.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.KeyMapping;

/**
 * Доступ к счётчику нажатий клавиши. Ванильный игровой цикл превращает каждое
 * "нажатие" клавиши атаки в обычный клик ЛКМ (startAttack), поэтому мы просто
 * добавляем одно нажатие — и удар копьём происходит ровно так же, как от настоящего клика.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int lungeswap_getClickCount();

	@Accessor("clickCount")
	void lungeswap_setClickCount(int clickCount);
}
