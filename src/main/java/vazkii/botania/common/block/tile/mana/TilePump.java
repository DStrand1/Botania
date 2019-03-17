/**
 * This class was created by <Vazkii>. It's distributed as
 * part of the Botania Mod. Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 *
 * File Created @ [Mar 18, 2015, 3:16:57 PM (GMT)]
 */
package vazkii.botania.common.block.tile.mana;

import com.google.common.collect.ImmutableMap;
import net.minecraft.init.Particles;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.animation.TimeValues;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.model.animation.CapabilityAnimation;
import net.minecraftforge.common.model.animation.IAnimationStateMachine;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ObjectHolder;
import vazkii.botania.api.internal.VanillaPacketDispatcher;
import vazkii.botania.common.block.tile.TileMod;
import vazkii.botania.common.lib.LibBlockNames;
import vazkii.botania.common.lib.LibMisc;

import javax.annotation.Nonnull;

public class TilePump extends TileMod implements ITickable {
	@ObjectHolder(LibMisc.MOD_ID + ":" + LibBlockNames.PUMP)
	public static TileEntityType<TilePump> TYPE;
	private static final String TAG_ACTIVE = "active";

	private float innerRingPos;
	public boolean active = false;
	public boolean hasCart = false;
	public boolean hasCartOnTop = false;
	private float moving = 0F;

	public int comparator;
	public boolean hasRedstone = false;
	private int lastComparator = 0;

	private final TimeValues.VariableValue move;
	private final IAnimationStateMachine asm;
	private final LazyOptional<IAnimationStateMachine> asmCap;

	public TilePump() {
		super(TYPE);
		if (FMLEnvironment.dist == Dist.CLIENT) {
			move = new TimeValues.VariableValue(0);
			asm = ModelLoaderRegistry.loadASM(new ResourceLocation("botania", "asms/block/pump.json"), ImmutableMap.of("move", move));
			asmCap = LazyOptional.of(() -> asm);
		} else {
			move = null;
			asm = null;
			asmCap = LazyOptional.empty();
		}
	}

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, EnumFacing side) {
		return CapabilityAnimation.ANIMATION_CAPABILITY.orEmpty(cap, asmCap);
	}

	@Override
	public void tick() {
		hasRedstone = false;
		for(EnumFacing dir : EnumFacing.values()) {
			int redstoneSide = world.getRedstonePower(pos.offset(dir), dir);
			if(redstoneSide > 0) {
				hasRedstone = true;
				break;
			}
		}

		float max = 8F;
		float min = 0F;

		float incr = max / 10F;

		if(innerRingPos < max && active && moving >= 0F) {
			innerRingPos += incr;
			moving = incr;
			if(innerRingPos >= max) {
				innerRingPos = Math.min(max, innerRingPos);
				moving = 0F;
				for(int x = 0; x < 2; x++)
					world.addParticle(Particles.SMOKE, getPos().getX() + Math.random(), getPos().getY() + Math.random(), getPos().getZ() + Math.random(), 0, 0, 0);
			}
		} else if(innerRingPos > min) {
			innerRingPos -= incr * 2;
			moving = -incr * 2;
			if(innerRingPos <= min) {
				innerRingPos = Math.max(min, innerRingPos);
				moving = 0F;
			}
		}

		if(world.isRemote)
			move.setValue(innerRingPos / 8 * 0.5F); // rescale to 0 - 0.5 for json animation

		if(!hasCartOnTop)
			comparator = 0;
		if(!hasCart && active)
			setActive(false);
		if(active && hasRedstone)
			setActive(false);

		hasCart = false;
		hasCartOnTop = false;

		if(comparator != lastComparator)
			world.updateComparatorOutputLevel(pos, world.getBlockState(pos).getBlock());
		lastComparator = comparator;
	}

	@Override
	public void writePacketNBT(NBTTagCompound cmp) {
		cmp.putBoolean(TAG_ACTIVE, active);
	}

	@Override
	public void readPacketNBT(NBTTagCompound cmp) {
		boolean prevActive = active;
		active = cmp.getBoolean(TAG_ACTIVE);
		if(world != null && world.isRemote)
			if(prevActive != active)
				asm.transition(active ? "moving" : "default");
	}

	public void setActive(boolean active) {
		if(!world.isRemote) {
			boolean diff = this.active != active;
			this.active = active;
			if(diff)
				VanillaPacketDispatcher.dispatchTEToNearbyPlayers(world, pos);
		}
	}
}
