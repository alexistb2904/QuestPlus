package dev.questplus.clientmodel;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class QuestCameraParityTest {
	@Test
	void initialFitContainsLargeCategoryIncludingNodePadding() {
		QuestCamera camera = new QuestCamera(.2, 5);
		camera.fitAll(List.of(new QuestLayout.Point(-900, -400), new QuestLayout.Point(900, 400)),
				40, 50, 450, 230, 24, 24);
		assertTrue(camera.screenX(-924) >= 40);
		assertTrue(camera.screenX(924) <= 490);
		assertTrue(camera.screenY(-424) >= 50);
		assertTrue(camera.screenY(424) <= 280);
	}
	@Test
	void anchorCoordinatesRemainInvertibleWithPointerAnchoredZoom() {
		QuestCamera camera = new QuestCamera(.25, 2.5);
		camera.fitBounds(List.of(new QuestLayout.Point(-60, 40), new QuestLayout.Point(140, 100)), 0, 0, 680, 390, 85,
				50);
		double beforeX = camera.screenX(140), beforeY = camera.screenY(100);
		assertEquals(140, camera.worldX(beforeX), .00001);
		assertEquals(100, camera.worldY(beforeY), .00001);
		double original = camera.screenX(140) - camera.screenX(-60);
		camera.zoomAt(1.5, beforeX, beforeY);
		assertEquals(beforeX, camera.screenX(140), .00001);
		assertEquals(beforeY, camera.screenY(100), .00001);
		assertEquals(original * 1.5, camera.screenX(140) - camera.screenX(-60), .00001);
		camera.pan(-30, 45);
		assertEquals(beforeX - 30, camera.screenX(140), .00001);
		assertEquals(beforeY + 45, camera.screenY(100), .00001);
		assertEquals(1.5, camera.zoom());
	}

	@Test
	void cameraClampsZoomAndKeepsAnchorUnderCursor() {
		QuestCamera camera = new QuestCamera(.25, 2.5);
		camera.pan(125, 83);
		double cursorX = 260, cursorY = 145;
		double worldX = camera.worldX(cursorX), worldY = camera.worldY(cursorY);
		camera.zoomAt(500, cursorX, cursorY);
		assertEquals(2.5, camera.zoom(), .00001);
		assertEquals(cursorX, camera.screenX(worldX), .00001);
		assertEquals(cursorY, camera.screenY(worldY), .00001);
		camera.zoomAt(.001, cursorX, cursorY);
		assertEquals(.25, camera.zoom(), .00001);
		assertEquals(cursorX, camera.screenX(worldX), .00001);
		assertEquals(cursorY, camera.screenY(worldY), .00001);
	}

	@Test
	void sharedVideoStyleZoomRangeAndWheelStepKeepFocusUnderPointer() {
		QuestCamera camera = new QuestCamera(QuestCamera.MIN_ZOOM, QuestCamera.MAX_ZOOM);
		assertEquals(.2, QuestCamera.MIN_ZOOM, .00001);
		assertEquals(5, QuestCamera.MAX_ZOOM, .00001);
		assertEquals(1.15, QuestCamera.ZOOM_STEP, .00001);
		double cursorX = 195, cursorY = 104;
		double anchorX = camera.worldX(cursorX), anchorY = camera.worldY(cursorY);
		for (int i = 0; i < 20; i++) {
			camera.zoomAt(QuestCamera.ZOOM_STEP, cursorX, cursorY);
			assertEquals(cursorX, camera.screenX(anchorX), .00001);
			assertEquals(cursorY, camera.screenY(anchorY), .00001);
		}
		assertEquals(5, camera.zoom(), .00001);
		for (int i = 0; i < 60; i++)
			camera.zoomAt(1 / QuestCamera.ZOOM_STEP, cursorX, cursorY);
		assertEquals(.2, camera.zoom(), .00001);
		assertEquals(cursorX, camera.screenX(anchorX), .00001);
		assertEquals(cursorY, camera.screenY(anchorY), .00001);
		camera.zoomAt(1 / QuestCamera.ZOOM_STEP, cursorX, cursorY);
		assertEquals(.2, camera.zoom(), .00001);
	}

	@Test
	void recenterNeverChangesActiveZoom() {
		QuestCamera camera = new QuestCamera(.25, 2.5);
		camera.zoomAt(2, 0, 0);
		camera.fitBounds(List.of(new QuestLayout.Point(0, 0), new QuestLayout.Point(200, 60)), 20, 30, 420, 260, 24,
				24);
		assertEquals(2, camera.zoom(), .00001);
		assertEquals(230, (camera.screenX(0) + camera.screenX(200)) / 2, .00001);
		assertEquals(160, (camera.screenY(0) + camera.screenY(60)) / 2, .00001);
	}

	@Test
	void fittingAnnotationsUsesTheirWorldCentersAndFixedScreenPadding() {
		QuestCamera camera = new QuestCamera(.25, 2.5);
		var quest = new QuestLayout.Point(0, 0);
		var annotation = new QuestLayout.Point(180, 65);
		camera.fitBounds(List.of(quest, annotation), 40, 30, 500, 320, 100, 80);
		assertEquals(40 + 500 / 2.0, (camera.screenX(0) + camera.screenX(180)) / 2, .00001);
		assertEquals(30 + 320 / 2.0, (camera.screenY(0) + camera.screenY(65)) / 2, .00001);
		assertEquals(1, camera.zoom());
	}
}
