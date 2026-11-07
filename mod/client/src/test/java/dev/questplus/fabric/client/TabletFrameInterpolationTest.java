package dev.questplus.fabric.client;

import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TabletFrameInterpolationTest {
	@Test
	void interpolationDoesNotMutateEitherRawSnapshot() {
		var from = new PlayerAnimBone("tablet_root");
		from.position.set(2, 4, 6);
		from.rotation.set(1, 2, 3);
		from.scale.set(1, 2, 3);
		var to = new PlayerAnimBone("tablet_root");
		to.position.set(6, 8, 10);
		to.rotation.set(3, 4, 5);
		to.scale.set(3, 4, 5);
		var result = new PlayerAnimBone("tablet_root");
		TabletFrameInterpolation.lerp(from, to, result, .25f);
		assertEquals(3, result.position.x);
		assertEquals(5, result.position.y);
		assertEquals(1.5f, result.rotation.x);
		assertEquals(1.5f, result.scale.x);
		assertEquals(2, from.position.x);
		assertEquals(6, to.position.x);
		assertEquals(1, from.rotation.x);
		assertEquals(3, to.rotation.x);
	}

	@Test
	void finishesAtTheHeldLastFrameOfTheSameClip() {
		var snapshots = new TabletFrameInterpolation.Snapshots();
		var open = Identifier.fromNamespaceAndPath("questplus", "questplus.open");
		var close = Identifier.fromNamespaceAndPath("questplus", "questplus.close");
		var bones = Map.of("tablet_root", new PlayerAnimBone("tablet_root"));
		snapshots.accept(new TabletFrameInterpolation.Snapshot(open, 23, 24, bones));
		assertFalse(snapshots.settled(open, 24));
		snapshots.accept(new TabletFrameInterpolation.Snapshot(open, 24, 24, bones));
		assertTrue(snapshots.settled(open, 24));
		snapshots.accept(new TabletFrameInterpolation.Snapshot(close, 1, 24, bones));
		assertFalse(snapshots.settled(open, 24));
		assertFalse(snapshots.settled(close, 24));
		snapshots.accept(new TabletFrameInterpolation.Snapshot(close, 24, 24, bones));
		assertTrue(snapshots.settled(close, 24));
		snapshots.clear();
		assertFalse(snapshots.settled(close, 1));
	}
}
