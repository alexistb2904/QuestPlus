package dev.questplus.platform.neoforge;

import com.google.gson.JsonParser;
import dev.questplus.fabric.LoaderTestSupport;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Runs the shared server GameTests on NeoForge. Test classes keep Fabric's {@code @GameTest}
 * annotation (only the annotation class is on this classpath). They are read from the shared
 * {@code fabric.mod.json} so both loaders run the same list, and get Fabric's test ids.
 * <p>The tests share packages with the mod, which JPMS forbids across modules, so this source set
 * joins the {@code questplus} mod in dev runs. It only acts in the GameTest run ({@code questplus.gametest}).
 */
@EventBusSubscriber(modid = "questplus")
public final class QuestPlusTestNeoForge {
	private static final boolean ENABLED = Boolean.getBoolean("questplus.gametest");
	private static final String NAMESPACE = "questplus-test";
	// Fabric's 8x8x8 fabric-gametest-api-v1:empty, shipped here as a vanilla structure.
	private static final Identifier EMPTY = Identifier.fromNamespaceAndPath(NAMESPACE, "empty");
	private record Test(Identifier id, Object instance, Method method, GameTest settings) {}
	private static final List<Test> tests = new ArrayList<>();

	private QuestPlusTestNeoForge() {}

	@SubscribeEvent
	static void register(RegisterEvent event) {
		if (!ENABLED) return;
		if (event.getRegistryKey().equals(Registries.ITEM)) LoaderTestSupport.register();
		if (event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
			discover();
			for (var test : tests) Registry.register(BuiltInRegistries.TEST_FUNCTION, test.id(), invoker(test));
		}
	}

	@SubscribeEvent
	static void capabilities(RegisterCapabilitiesEvent event) {
		if (ENABLED) LoaderTestSupport.registerCapabilities(event);
	}

	@SubscribeEvent
	static void tests(RegisterGameTestsEvent event) {
		if (!ENABLED) return;
		var environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(NAMESPACE, "default"),
				new TestEnvironmentDefinition.AllOf(List.of()));
		// Only primitive attributes: the annotation lives in the app class loader, Minecraft types
		// (rotation()) would not link from the game's. No shared test sets a rotation.
		for (var test : tests) {
			var settings = test.settings();
			event.registerTest(test.id(), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, test.id()),
					new TestData<>(environment, EMPTY, settings.maxTicks(), settings.setupTicks(), settings.required())));
		}
	}

	// Test classes touch game classes: load them in the registry window, not during mod construction.
	private static void discover() {
		for (var name : testClasses()) {
			try {
				var type = Class.forName(name);
				var instance = type.getDeclaredConstructor().newInstance();
				for (var method : type.getMethods()) {
					var settings = method.getAnnotation(GameTest.class);
					if (settings != null) tests.add(new Test(Identifier.fromNamespaceAndPath(NAMESPACE,
							snake(type.getSimpleName() + "_" + method.getName())), instance, method, settings));
				}
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Cannot load GameTest class " + name, e);
			}
		}
	}

	private static List<String> testClasses() {
		try (var stream = QuestPlusTestNeoForge.class.getResourceAsStream("/fabric.mod.json")) {
			var names = new ArrayList<String>();
			JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("entrypoints").getAsJsonArray("fabric-gametest").forEach(name -> names.add(name.getAsString()));
			return names;
		} catch (Exception e) {
			throw new IllegalStateException("Cannot read the shared GameTest list", e);
		}
	}

	private static Consumer<GameTestHelper> invoker(Test test) {
		return helper -> {
			try {
				test.method().invoke(test.instance(), helper);
			} catch (InvocationTargetException e) {
				if (e.getCause() instanceof RuntimeException runtime) throw runtime;
				if (e.getCause() instanceof Error error) throw error;
				throw new RuntimeException(e.getCause());
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		};
	}

	/** Same id scheme as Fabric's TestAnnotationLocator, which the integrationTest coverage check relies on. */
	static String snake(String name) {
		return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
	}
}
