package dev.questplus.clientmodel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoardWheelTest {
	@Test
	void rowsThatFitDoNotMove() {
		assertArrayEquals(new int[] { 0, 1, 2 }, BoardWheel.rows(3, 5, 0, 7));
		assertArrayEquals(new int[0], BoardWheel.rows(4, 0, 2, 3));
	}

	@Test
	void overflowTurnsEndlessly() {
		assertArrayEquals(new int[] { 0, 1, 2 }, BoardWheel.rows(5, 3, 0, 0));
		assertArrayEquals(new int[] { 1, 2, 3 }, BoardWheel.rows(5, 3, 0, 1));
		assertArrayEquals(new int[] { 4, 0, 1 }, BoardWheel.rows(5, 3, 0, 4));
		assertArrayEquals(BoardWheel.rows(5, 3, 0, 0), BoardWheel.rows(5, 3, 0, 5));
	}

	@Test
	void pinsStayOnTopWhileOthersTurn() {
		// 2 pins + 4 in progress, 4 slots: pins fixed, 2 slots turn over the 4 others.
		assertArrayEquals(new int[] { 0, 1, 2, 3 }, BoardWheel.rows(6, 4, 2, 0));
		assertArrayEquals(new int[] { 0, 1, 5, 2 }, BoardWheel.rows(6, 4, 2, 3));
		// More pins than slots: one slot still turns so every row gets shown.
		assertArrayEquals(new int[] { 0, 1, 3 }, BoardWheel.rows(6, 3, 5, 1));
	}
}
