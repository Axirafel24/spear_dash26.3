package com.lungeswap.client;

import java.util.Optional;

import com.lungeswap.client.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class LungeSwapClient implements ClientModInitializer {
	public static final String MOD_ID = "lungeswap";

	// ===================== НАСТРОЙКИ (в тиках; 20 тиков = 1 секунда) =====================

	/**
	 * Через сколько тиков после смены слота наносится удар.
	 * Не ставь меньше 2: клиенту нужно успеть сообщить серверу, что ты держишь копьё.
	 * Важно: в ванили смена предмета сбрасывает кулдаун атаки, поэтому при малой задержке
	 * удар будет "недозаряженным" (меньше урона). Хочешь полный урон — увеличь значение
	 * (примерно 15-20 тиков для большинства копий).
	 */
	private static final int ATTACK_DELAY_TICKS = 2;

	/** Через сколько тиков после команды на удар вернуться на прежний слот. */
	private static final int RETURN_DELAY_TICKS = 5;

	// ======================================================================================

	private static final ResourceKey<Enchantment> LUNGE =
			ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("lunge"));

	private static KeyMapping swapKey;

	private enum Phase { IDLE, WAIT_ATTACK, WAIT_RETURN }

	private static Phase phase = Phase.IDLE;
	private static int ticksLeft;
	private static int originalSlot;
	private static int spearSlot;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		swapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.lungeswap.swap_attack",
				InputConstants.KEY_R, // клавишу можно поменять в Настройки -> Управление
				category));

		ClientTickEvents.END_CLIENT_TICK.register(LungeSwapClient::onTick);
	}

	private static void onTick(Minecraft client) {
		LocalPlayer player = client.player;

		boolean pressed = false;
		while (swapKey.consumeClick()) {
			pressed = true;
		}

		if (player == null || client.level == null) {
			phase = Phase.IDLE;
			return;
		}

		if (phase == Phase.IDLE) {
			if (pressed) {
				start(client, player);
			}
			return;
		}

		// Идёт последовательность: ждём нужное число тиков.
		if (--ticksLeft > 0) {
			return;
		}

		Inventory inventory = player.getInventory();

		if (phase == Phase.WAIT_ATTACK) {
			if (inventory.getSelectedSlot() != spearSlot) {
				// Игрок сам переключил слот — не мешаем.
				phase = Phase.IDLE;
				return;
			}

			clickAttack(client);

			if (originalSlot == spearSlot) {
				phase = Phase.IDLE; // копьё уже было в руке, возвращаться некуда
			} else {
				phase = Phase.WAIT_RETURN;
				ticksLeft = RETURN_DELAY_TICKS;
			}
		} else if (phase == Phase.WAIT_RETURN) {
			// Возвращаем слот, только если игрок всё ещё держит копьё (сам не переключился).
			if (inventory.getSelectedSlot() == spearSlot) {
				inventory.setSelectedSlot(originalSlot);
			}
			phase = Phase.IDLE;
		}
	}

	private static void start(Minecraft client, LocalPlayer player) {
		int slot = findLungeSpearSlot(client, player);

		if (slot < 0) {
			player.sendSystemMessage(Component.translatable("message.lungeswap.no_spear"));
			return;
		}

		Inventory inventory = player.getInventory();
		originalSlot = inventory.getSelectedSlot();
		spearSlot = slot;

		if (slot != originalSlot) {
			inventory.setSelectedSlot(slot);
		}

		phase = Phase.WAIT_ATTACK;
		ticksLeft = ATTACK_DELAY_TICKS;
	}

	/** Ищет в хотбаре (слоты 0-8) копьё с Рывком; если таких несколько — берёт с большим уровнем. */
	private static int findLungeSpearSlot(Minecraft client, LocalPlayer player) {
		Optional<Holder.Reference<Enchantment>> lunge =
				client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(LUNGE);

		if (lunge.isEmpty()) {
			return -1;
		}

		Inventory inventory = player.getInventory();
		int bestSlot = -1;
		int bestLevel = 0;

		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (stack.isEmpty() || !stack.is(ItemTags.SPEARS)) {
				continue;
			}

			int level = EnchantmentHelper.getItemEnchantmentLevel(lunge.get(), stack);

			if (level > bestLevel) {
				bestLevel = level;
				bestSlot = slot;
			}
		}

		return bestSlot;
	}

	/** Эмулирует один клик по клавише атаки — дальше всё делает ванильный код (укол копьём, анимация, Рывок). */
	private static void clickAttack(Minecraft client) {
		KeyMappingAccessor attack = (KeyMappingAccessor) client.options.keyAttack;
		attack.lungeswap_setClickCount(attack.lungeswap_getClickCount() + 1);
	}
}
