package dev.questplus.platform.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Registrations NeoForge only accepts inside a mod-bus event. Shared code registers everything while
 * {@code RegisterEvent} runs. Each queue replays into its event, which NeoForge fires later, and
 * rejects anything that arrives after its event fired. Listeners are added during mod construction.
 */
public final class ModBus {
	public static Queue<RegisterPayloadHandlersEvent> payloads;
	public static Queue<RegisterCapabilitiesEvent> capabilities;
	public static Queue<EntityAttributeCreationEvent> attributes;

	private ModBus() {}

	static void attach(IEventBus bus) {
		payloads = new Queue<>(bus, RegisterPayloadHandlersEvent.class);
		capabilities = new Queue<>(bus, RegisterCapabilitiesEvent.class);
		attributes = new Queue<>(bus, EntityAttributeCreationEvent.class);
	}

	public static final class Queue<E extends Event> {
		private final Class<E> type;
		private final List<Consumer<E>> pending = new ArrayList<>();
		private boolean drained;

		public Queue(IEventBus bus, Class<E> type) {
			this.type = type;
			bus.addListener(type, this::drain);
		}

		public synchronized void add(Consumer<E> action) {
			if (drained)
				throw new IllegalStateException(type.getSimpleName() + " already fired: Quest Plus registered too late");
			pending.add(action);
		}

		private synchronized void drain(E event) {
			drained = true;
			pending.forEach(action -> action.accept(event));
		}
	}
}
