package org.mcteam.ancientgates.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

public class ItemStackUtil {

	private static final String PAPER_FORMAT = "AG2:";

	public static String itemStackToString(final ItemStack[] itemStack) {
		// Paper's versioned NBT retains data components, PDC and nested containers.
		return PAPER_FORMAT + java.util.Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(itemStack));
	}

	public static String itemStackToString(final ItemStack itemStack) {
		return itemStackToString(new ItemStack[] { itemStack });
	}

	public static ItemStack[] stringToItemStack(final String itemStackString) {
		if (itemStackString.startsWith(PAPER_FORMAT)) {
			return ItemStack.deserializeItemsFromBytes(java.util.Base64.getDecoder().decode(itemStackString.substring(PAPER_FORMAT.length())));
		}
		// Read existing pre-Paper messages without shifting items into different slots.
		final String[] serializedBlocks = itemStackString.split(";");
		final String isInfo = serializedBlocks[0];
		final ItemStack[] deserializedItemStack = new ItemStack[Integer.valueOf(isInfo)];

		for (int i = 1; i < serializedBlocks.length; i++) {
			final String[] serializedBlock = serializedBlocks[i].split("#");
			final int stackPosition = Integer.valueOf(serializedBlock[0]);

			if (stackPosition < 0 || stackPosition >= deserializedItemStack.length) {
				continue;
			}

			ItemStack is = new ItemStack(Material.AIR);
			boolean createdItemStack = false;

			final String[] serializedItemStack = serializedBlock[1].split(":");
			for (final String itemInfo : serializedItemStack) {
				final String[] itemAttribute = itemInfo.split("@");
				if (itemAttribute[0].equals("t")) {
					is = new ItemStack(Material.getMaterial(itemAttribute[1]));
					createdItemStack = true;
				} else if (itemAttribute[0].equals("d") && createdItemStack) {
					is.setDurability(Short.valueOf(itemAttribute[1]));
				} else if (itemAttribute[0].equals("a") && createdItemStack) {
					is.setAmount(Integer.valueOf(itemAttribute[1]));
				} else if (itemAttribute[0].equals("e") && createdItemStack) {
					is.addEnchantment(Enchantment.getByName(itemAttribute[1]), Integer.valueOf(itemAttribute[2]));
				}
			}
			deserializedItemStack[stackPosition] = new ItemStack(is);
		}
		return deserializedItemStack;

	}

}
