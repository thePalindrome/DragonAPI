/*******************************************************************************
 * @author Reika Kalseki
 *
 * Copyright 2017
 *
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.DragonAPI.Instantiable.Data;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.Tessellator;

import Reika.DragonAPI.Instantiable.Data.Maps.MultiMap.MapDeterminator;
import Reika.DragonAPI.Libraries.MathSci.ReikaMathLibrary;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;


public class Proportionality<F> extends CircularDivisionRenderer<F> {

	private final Map<F, Double> data;
	private double totalValue = 0;

	public Proportionality() {
		this(null);
	}

	public Proportionality(MapDeterminator<F, ?> md) {
		data = md != null ? md.getMapType() : new HashMap();
	}

	public void addValue(F o, double amt) {
		Double get = data.get(o);
		double val = get != null ? get.doubleValue() : 0;
		data.put(o, val+amt);
		this.totalValue += amt;
		if (o instanceof ColorCallback)
			this.addColorRenderer(o, (ColorCallback)o);
		this.resetColors();
	}

	public void removeValue(F o, double amt) {
		Double get = data.get(o);
		double val = get != null ? get.doubleValue() : 0;
		double res = val-amt;
		this.totalValue -= Math.min(amt, val);
		if (res > 0)
			data.put(o, res);
		else
			data.remove(o);
		this.resetColors();
	}

	public double getValue(F o) {
		Double get = data.get(o);
		return get != null ? get.doubleValue() : 0;
	}

	public double getFraction(F o) {
		return this.getValue(o)/this.totalValue;
	}

	@Override
	public Collection<F> getElements() {
		return Collections.unmodifiableCollection(this.data.keySet());
	}

	public boolean hasMajority(F o) {
		return this.getFraction(o) >= 0.5;
	}

	public F getLargestCategory() {
		double max = -1;
		F big = null;
		for (F o : data.keySet()) {
			double has = this.getValue(o);
			if (has > max) {
				has = max;
				big = o;
			}
		}
		return big;
	}

	@Override
	public void clear() {
		data.clear();
		this.resetColors();
	}

	@Override
	public F getClickedSection(int x, int y) {
		double d = ReikaMathLibrary.py3d(x-centerX, y-centerY, 0);
		if (d > renderRadius)
			return null;
		double relAng = (Math.toDegrees(Math.atan2(y-centerY, x-centerX))+360)%360;
		double ang = renderOrigin;
		for (F o : data.keySet()) {
			double angw = 360D*this.getFraction(o);
			//ReikaJavaLibrary.pConsole(o+" > "+ang+" - "+(ang+angw)+" @ "+relAng);
			if (ang <= relAng && ang+angw >= relAng) {
				return o;
			}
			ang += angw;
		}
		return null;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void render(Map<F, Integer> colorMap) {
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		double ang = renderOrigin;
		Tessellator v5 = Tessellator.instance;
		int i = 0;
		for (F o : data.keySet()) {
			double angw = 360D*this.getFraction(o);

			v5.startDrawing(innerRadius == 0 ? GL11.GL_TRIANGLE_FAN : GL11.GL_TRIANGLE_STRIP);
			int c = this.getColorForElement(o, colorMap);
			v5.setColorOpaque_I(c);

			//ReikaJavaLibrary.pConsole(o+" > "+this.getFraction(o)+" = "+angw);

			this.renderSection(v5, ang, ang+angw);

			v5.draw();

			ang += angw;
		}
		GL11.glPopAttrib();
	}

	/*
	public void writeToNBT(NBTTagCompound NBT) {
		NBT.setDouble("total", totalValue);
		NBTTagList li = new NBTTagList();
		for (F o : data.keySet()) {
			NBTTagCompound tag = new NBTTagCompound();
			tag.setDouble("Prop_"+o.toString(), this.getValue(o));
		}
		NBT.setTag("data", li);
	}

	public void readFromNBT(NBTTagCompound NBT) {
		totalValue = NBT.getDouble("total");
		data.clear();


	}
	 */

}
