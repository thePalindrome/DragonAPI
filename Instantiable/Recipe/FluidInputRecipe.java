/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2016
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.DragonAPI.Instantiable.Recipe;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidContainerRegistry.FluidContainerData;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import Reika.DragonAPI.Instantiable.Data.Maps.MultiMap;


public class FluidInputRecipe extends ShapedOreRecipe {

	private static final MultiMap<String, ItemStack> fluidItems = new MultiMap().setNullEmpty();

	public FluidInputRecipe(ItemStack result, Object... recipe) {
		super(result, parseFluids(recipe));
	}

	public static class ShapelessFluidInputRecipe extends ShapelessOreRecipe {

		public ShapelessFluidInputRecipe(ItemStack result, Object... recipe) {
			super(result, parseFluids(recipe));
		}

	}

	private static Object[] parseFluids(Object[] recipe) {
		for (int i = 0; i < recipe.length; i++) {
			if (recipe[i] instanceof Fluid) {
				String s = registerItemsFor((Fluid)recipe[i]);
				recipe[i] = s;
			}
		}
		return recipe;
	}

	private static String registerItemsFor(Fluid f) {
		if (!fluidItems.containsKey(f.getName())) {
			registerItems(f);
		}
		return "container_"+f.getName();
	}

	private static void registerItems(Fluid f) {
		String s2 = "container_"+f.getName();
		FluidContainerData[] dat = FluidContainerRegistry.getRegisteredFluidContainerData();
		for (int i = 0; i < dat.length; i++) {
			FluidContainerData fcd = dat[i];
			if (fcd.fluid != null && fcd.fluid.getFluid() == f && fcd.filledContainer != null) {
				fluidItems.addValue(f.getName(), fcd.filledContainer);
				OreDictionary.registerOre(s2, fcd.filledContainer);
			}
		}
	}

}
