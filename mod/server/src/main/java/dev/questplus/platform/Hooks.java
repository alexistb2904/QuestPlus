package dev.questplus.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class Hooks {
	private Hooks() {}

	@FunctionalInterface
	public interface BlockBreak {
		void broken(ServerPlayer player, BlockPos pos, BlockState state);
	}

	/** Right-click on an item, block or entity on either logical side. Anything but PASS stops vanilla handling. */
	@FunctionalInterface
	public interface Use {
		InteractionResult use(Player player, Level level, InteractionHand hand);
	}
}
