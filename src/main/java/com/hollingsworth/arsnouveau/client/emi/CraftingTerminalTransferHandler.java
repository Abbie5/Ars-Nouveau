package com.hollingsworth.arsnouveau.client.emi;

import com.hollingsworth.arsnouveau.client.container.CraftingTerminalMenu;
import com.hollingsworth.arsnouveau.client.container.IAutoFillTerminal;
import com.hollingsworth.arsnouveau.client.container.StoredItemStack;
import com.hollingsworth.arsnouveau.common.menu.MenuRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CraftingTerminalTransferHandler<C extends AbstractContainerMenu & IAutoFillTerminal> implements EmiRecipeHandler<C> {
//    private static final List<Class<? extends AbstractContainerMenu>> containerClasses = new ArrayList<>();
//    private static final IRecipeTransferError ERROR_INSTANCE = new IRecipeTransferError() {
//        @Override
//        public Type getType() {
//            return Type.INTERNAL;
//        }
//    };

//    static {
//        containerClasses.add(CraftingTerminalMenu.class);
//    }

//    private final Class<C> containerClass;
//    private final IRecipeTransferHandlerHelper helper;

//	public CraftingTerminalTransferHandler(Class<C> containerClass, IRecipeTransferHandlerHelper helper) {
//		this.containerClass = containerClass;
//		this.helper = helper;
//	}

//	@Override
//	public Class<C> getContainerClass() {
//		return containerClass;
//	}

//    public static void registerTransferHandlers(IRecipeTransferRegistration recipeTransferRegistry) {
//        for (Class<? extends AbstractContainerMenu> aClass : containerClasses)
//            recipeTransferRegistry.addRecipeTransferHandler(new CraftingTerminalTransferHandler(aClass, recipeTransferRegistry.getTransferHelper()), RecipeTypes.CRAFTING);
//    }

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<C> screen) {
        List<EmiStack> stacks = new ArrayList<>();

        stacks.addAll(screen.getMenu().getItems().stream().map(EmiStack::of).toList());

        stacks.addAll(screen.getMenu().getStoredItems()
                .stream()
                .map(StoredItemStack::getActualStack)
                .map(EmiStack::of)
                .toList()
        );

        return new EmiPlayerInventory(stacks);
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return VanillaEmiRecipeCategories.CRAFTING.equals(recipe.getCategory());
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<C> context) {
        IAutoFillTerminal term = context.getScreenHandler();
        List<EmiIngredient> missing = new ArrayList<>();
        List<EmiIngredient> ingredients = recipe.getInputs();
        Set<StoredItemStack> stored = new HashSet<>(term.getStoredItems());
        LocalPlayer player = Minecraft.getInstance().player;

        for (EmiIngredient ingredient : ingredients) {
            ItemStack[] list = ingredient.getEmiStacks().stream().map(EmiStack::getItemStack).toArray(ItemStack[]::new);

            boolean found = false;
            for (ItemStack stack : list) {
                if (!stack.isEmpty() && player.getInventory().findSlotMatchingItem(stack) != -1) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                for (ItemStack stack : list) {
                    StoredItemStack s = new StoredItemStack(stack);
                    if (stored.contains(s)) {
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                missing.add(ingredient);
            }
        }
        return missing.isEmpty();
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<C> context) {
        IAutoFillTerminal term = context.getScreenHandler();
        //List<ItemStack[]> inputs = new ArrayList<>();
        Set<StoredItemStack> stored = new HashSet<>(term.getStoredItems());
        LocalPlayer player = Minecraft.getInstance().player;

        List<ItemStack[]> inputs = recipe.getInputs()
                .stream()
                .map(EmiIngredient::getEmiStacks)
                .map(EmiStack::getItemStack)
                .toArray(ItemStack[]::new);

        ItemStack[][] stacks = inputs.toArray(new ItemStack[][]{});
        CompoundTag compound = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.length; ++i) {
            if (stacks[i] != null) {
                CompoundTag CompoundNBT = new CompoundTag();
                CompoundNBT.putByte("s", (byte) i);
                int k = 0;
                for (int j = 0; j < stacks[i].length && k < 9; j++) {
                    if (stacks[i][j] != null && !stacks[i][j].isEmpty()) {
                        StoredItemStack s = new StoredItemStack(stacks[i][j]);
                        if (stored.contains(s) || player.getInventory().findSlotMatchingItem(stacks[i][j]) != -1) {
                            CompoundTag tag = new CompoundTag();
                            stacks[i][j].save(tag);
                            CompoundNBT.put("i" + (k++), tag);
                        }
                    }
                }
                CompoundNBT.putByte("l", (byte) Math.min(9, k));
                list.add(CompoundNBT);
            }
        }
        compound.put("i", list);
        term.sendMessage(compound);

        return true;
    }
}
