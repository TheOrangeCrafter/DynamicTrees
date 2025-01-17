package com.ferreusveritas.dynamictrees.systems.nodemappers;

import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.api.network.INodeInspector;
import com.ferreusveritas.dynamictrees.blocks.BlockBranch;
import com.ferreusveritas.dynamictrees.trees.Species;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Destroys all branches on a tree and the surrounding leaves.
 *
 * @author ferreusveritas
 */
public class NodeDestroyer implements INodeInspector {

	Species species;//Destroy any node that's made of the same kind of wood
	private final List<BlockPos> endPoints;//We always need to track endpoints during destruction

	public NodeDestroyer(Species species) {
		this.endPoints = new ArrayList<BlockPos>(32);
		this.species = species;
	}

	public List<BlockPos> getEnds() {
		return endPoints;
	}

	@Override
	public boolean run(IBlockState blockState, World world, BlockPos pos, EnumFacing fromDir) {
		BlockBranch branch = TreeHelper.getBranch(blockState);

		if (branch != null && species.getFamily() == branch.getFamily()) {
			if (branch.getRadius(blockState) == species.getFamily().getPrimaryThickness()) {
				endPoints.add(pos);
			}
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), 0);//Destroy the branch without notifying the client
		}

		return true;
	}

	@Override
	public boolean returnRun(IBlockState blockState, World world, BlockPos pos, EnumFacing fromDir) {
		return false;
	}

}
