package dev.questplus.fabric;

import com.google.gson.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.fabricmc.loader.api.FabricLoader;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Opt-in outbound-only integration with a self-hosted QuestPlus Hub.
 * Credentials never enter the Minecraft play packets or browser.
 */
final class QuestHubService implements AutoCloseable {
	private static final Gson JSON = new Gson();
	private final MinecraftServer server;
	private final Path world;
	private final HttpClient http;
	private URI origin;
	private String apiKey, session, revisionSignature, lastPending, lastJobId;
	private UUID owner;
	private Path sessionDefinitions;
	private long lastPoll;
	private boolean inFlight, closed;
	private JsonObject pendingApproval, lastAck;
	private final Map<UUID, PendingImport> imports = new HashMap<>();
	private final Map<UUID, PendingRetrieve> retrieves = new HashMap<>();

	private record PendingImport(String id, JsonArray files, long expires) {
	}

	private record PendingRetrieve(JsonArray files, String worldSignature, long expires) {
	}

	private Path defaults() {
		return FabricLoader.getInstance().getConfigDir().resolve("questplus/default").toAbsolutePath().normalize();
	}

	QuestHubService(MinecraftServer server) {
		this.server = server;
		world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
		http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NEVER)
				.build();
		Path config = world.resolve("questplus-hub.json");
		if (!Files.exists(config))
			config = FabricLoader.getInstance().getConfigDir().resolve("questplus-hub.json");
		try {
			if (!Files.exists(config)) {
				QuestPlusMod.LOGGER.info(
						"QuestPlus Hub is disabled. Configure world/questplus-hub.json or config/questplus-hub.json to enable.");
				return;
			}
			JsonObject cfg = Defaults.readObject(config);
			String url = cfg.get("url").getAsString();
			apiKey = cfg.get("serverKey").getAsString();
			if (apiKey.length() < 32)
				throw new IllegalArgumentException("Hub key must contain at least 32 characters");
			origin = URI.create(url);
			if (origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
					|| origin.getPath() != null && !origin.getPath().equals("") && !origin.getPath().equals("/"))
				throw new IllegalArgumentException("Hub URL must be its origin only");
			boolean local = origin.getHost() != null && Set.of("localhost", "127.0.0.1").contains(origin.getHost());
			if (!"https".equalsIgnoreCase(origin.getScheme())
					&& !(local && "http".equalsIgnoreCase(origin.getScheme())))
				throw new IllegalArgumentException("QuestPlus Hub requires HTTPS (except localhost)");
			QuestPlusMod.LOGGER.info("QuestPlus Hub configured at {}", origin);
		} catch (Exception error) {
			origin = null;
			apiKey = null;
			QuestPlusMod.LOGGER.error("Invalid QuestPlus Hub configuration", error);
		}
	}

	boolean configured() {
		return origin != null && !closed;
	}

	private String endpoint(String p) {
		return origin.toString().replaceAll("/$", "") + p;
	}

	private void notify(CommandSourceStack source, String message) {
		source.sendSuccess(() -> chat(Component.literal(message).withStyle(ChatFormatting.GRAY)), false);
	}

	private void fail(CommandSourceStack source, String message) {
		source.sendFailure(chat(Component.literal(message).withStyle(ChatFormatting.RED)));
	}

	static Component chat(Component content) {
		return Component.literal("[QuestPlus] ").withStyle(ChatFormatting.GOLD).append(content);
	}

	static Component editorLink(String url) {
		URI target = URI.create(url);
		if (!Set.of("https", "http").contains(target.getScheme()) || target.getHost() == null)
			throw new IllegalArgumentException("Invalid editor URL");
		return Component.translatableWithFallback("questplus.hub.open_editor", "Open Quest Studio → ")
				.withStyle(ChatFormatting.GREEN).append(
						Component.literal(url)
								.withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
										.withClickEvent(new ClickEvent.OpenUrl(target))
										.withHoverEvent(new HoverEvent.ShowText(Component.translatableWithFallback(
												"questplus.hub.open_editor_hint",
												"Open the quest editor. Approval in Minecraft is still required.")))));
	}

	static Component commandLink(Component label, String command) {
		return label.copy()
				.withStyle(style -> style.withColor(ChatFormatting.GREEN).withUnderlined(true)
						.withClickEvent(new ClickEvent.SuggestCommand(command)).withHoverEvent(
								new HoverEvent.ShowText(Component.translatableWithFallback("questplus.hub.command_hint",
										"Click to fill the command, then press Enter: %s", command))));
	}

	private void messageOwner(String message) {
		messageOwner(Component.literal(message).withStyle(ChatFormatting.GRAY));
	}

	private void messageOwner(Component content) {
		ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
		if (player != null)
			player.sendSystemMessage(chat(content));
	}

	private String signature(JsonArray documents) {
		try {
			MessageDigest hash = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(hash.digest(JSON.toJson(documents).getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private JsonObject registry() {
		JsonObject result = new JsonObject();
		addIds(result, "items", BuiltInRegistries.ITEM.keySet());
		addIds(result, "blocks", BuiltInRegistries.BLOCK.keySet());
		addIds(result, "fluids", BuiltInRegistries.FLUID.keySet());
		addIds(result, "entities", BuiltInRegistries.ENTITY_TYPE.keySet());
		addIds(result, "effects", BuiltInRegistries.MOB_EFFECT.keySet());
		addIds(result, "enchantments", server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).keySet());
		addIds(result, "biomes", server.registryAccess().lookupOrThrow(Registries.BIOME).keySet());
		addIds(result, "structures", server.registryAccess().lookupOrThrow(Registries.STRUCTURE).keySet());
		addIds(result, "dimensions", server.levelKeys().stream().map(k -> k.identifier()).toList());
		addIds(result, "statistics", BuiltInRegistries.CUSTOM_STAT.keySet());
		addIds(result, "advancements",
				server.getAdvancements().getAllAdvancements().stream().map(a -> a.id()).toList());
		JsonObject tags = new JsonObject();
		tagIds(tags, "items", BuiltInRegistries.ITEM);
		tagIds(tags, "blocks", BuiltInRegistries.BLOCK);
		tagIds(tags, "entities", BuiltInRegistries.ENTITY_TYPE);
		result.add("tags", tags);
		result.add("extensions", dev.questplus.fabric.api.QuestStudioDescriptors.snapshot());
		Map<String, Set<String>> assets = new HashMap<>();
		for (String kind : List.of("items", "blocks", "entities", "effects", "enchantments", "biomes", "structures")) {
			Set<String> ids = new HashSet<>();
			result.getAsJsonArray(kind).forEach(id -> ids.add(id.getAsString()));
			assets.put(kind, ids);
		}
		List<Path> roots = FabricLoader.getInstance().getAllMods().stream().flatMap(mod -> mod.getRootPaths().stream())
				.toList();
		result.add("assets", new StudioRegistryAssets(roots).collect(assets));
		return result;
	}

	private void addIds(JsonObject output, String name, Collection<?> ids) {
		JsonArray a = new JsonArray();
		ids.stream().map(Object::toString).sorted().forEach(a::add);
		output.add(name, a);
	}

	private <T> void tagIds(JsonObject output, String name, Registry<T> registry) {
		JsonArray a = new JsonArray();
		registry.getTags().map(tag -> "#" + tag.key().location().toString()).sorted().forEach(a::add);
		output.add(name, a);
	}

	private void request(String method, String path, JsonObject data, Consumer<JsonObject> onSuccess,
			Consumer<String> onError) {
		if (!configured()) {
			onError.accept("Hub not configured");
			return;
		}
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint(path))).timeout(Duration.ofSeconds(18))
				.header("Authorization", "Bearer " + apiKey).header("Accept", "application/json");
		if (data != null)
			builder.header("Content-Type", "application/json; charset=utf-8");
		HttpRequest req = builder
				.method(method,
						data == null ? HttpRequest.BodyPublishers.noBody()
								: HttpRequest.BodyPublishers.ofString(JSON.toJson(data), StandardCharsets.UTF_8))
				.build();
		http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.whenComplete((response, error) -> server.execute(() -> {
					if (closed)
						return;
					if (error != null) {
						onError.accept(error.getCause() == null ? error.toString() : error.getCause().toString());
						return;
					}
					try {
						JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
						if (response.statusCode() / 100 != 2) {
							onError.accept(body.has("error") ? body.get("error").getAsString()
									: "HTTP " + response.statusCode());
							return;
						}
						onSuccess.accept(body);
					} catch (Exception e) {
						onError.accept("Invalid Hub response: " + e.getMessage());
					}
				}));
	}

	void createSession(CommandSourceStack source) {
		createSession(source, false);
	}

	void createSession(CommandSourceStack source, boolean defaultPack) {
		if (!configured()) {
			fail(source,
					"Configure world/questplus-hub.json or config/questplus-hub.json (HTTPS URL and server key) first");
			return;
		}
		ServerPlayer player;
		try {
			player = source.getPlayerOrException();
		} catch (Exception e) {
			fail(source, "Open the editor as an in-game operator");
			return;
		}
		try {
			Path target = defaultPack ? defaults() : world.resolve("questplus");
			JsonArray files = QuestHubFiles.snapshot(target);
			JsonObject payload = new JsonObject();
			payload.addProperty("serverName", ChatFormatting.stripFormatting(server.getMotd()));
			payload.add("files", files);
			payload.add("registry", registry());
			String localSignature = signature(files);
			UUID actor = player.getUUID();
			request("POST", "/api/server/sessions", payload, result -> {
				owner = actor;
				sessionDefinitions = target;
				session = result.get("id").getAsString();
				lastPending = null;
				pendingApproval = null;
				lastJobId = null;
				lastAck = null;
				revisionSignature = localSignature;
				lastPoll = 0;
				String url = result.get("url").getAsString();
				messageOwner(editorLink(url));
			}, e -> fail(source, "Could not create session: " + e));
			notify(source, "Creating a secured editor session…");
		} catch (Exception error) {
			fail(source, error.getMessage());
		}
	}

	void approve(CommandSourceStack source, String code) {
		if (session == null || pendingApproval == null || !pendingApproval.get("code").getAsString().equals(code)) {
			fail(source, "No matching pending browser request");
			return;
		}
		try {
			if (!source.getPlayerOrException().getUUID().equals(owner)) {
				fail(source, "Only the session owner can approve");
				return;
			}
		} catch (Exception e) {
			fail(source, "Approval requires the original in-game operator");
			return;
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("session", session);
		payload.addProperty("id", pendingApproval.get("id").getAsString());
		payload.addProperty("code", code);
		request("POST", "/api/server/approve", payload, result -> {
			pendingApproval = null;
			messageOwner(Component
					.translatableWithFallback("questplus.hub.browser_approved",
							"Browser approved. Load the server quests in Quest Studio, then use Save to server.")
					.withStyle(ChatFormatting.GREEN));
		}, e -> fail(source, "Approval denied: " + e));
	}

	void revoke(CommandSourceStack source) {
		if (session == null) {
			notify(source, "No active session");
			return;
		}
		dropSession();
		notify(source, "Session revoked locally; remote session revocation requested.");
	}

	void tick() {
		if (!configured() || session == null || inFlight || System.currentTimeMillis() - lastPoll < 2500)
			return;
		lastPoll = System.currentTimeMillis();
		inFlight = true;
		String id = session;
		request("GET", "/api/server/poll?session=" + id, null, data -> {
			inFlight = false;
			if (!Objects.equals(id, session))
				return;
			JsonElement approval = data.get("pending");
			if (approval != null && approval.isJsonObject()) {
				JsonObject p = approval.getAsJsonObject();
				String pendingId = p.get("id").getAsString();
				if (!pendingId.equals(lastPending)) {
					lastPending = pendingId;
					pendingApproval = p;
					messageOwner(Component
							.translatableWithFallback("questplus.hub.compare_code",
									"Compare this code with Quest Studio: ")
							.withStyle(ChatFormatting.YELLOW).append(Component.literal(p.get("code").getAsString())
									.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
					messageOwner(commandLink(
							Component.translatableWithFallback("questplus.hub.approve_browser",
									"[Approve this browser]"),
							"/questplus editor approve " + p.get("code").getAsString()));
				}
			}
			JsonElement publish = data.get("job");
			if (publish != null && publish.isJsonObject()) {
				JsonObject job = publish.getAsJsonObject();
				String jobId = job.get("id").getAsString();
				if (!jobId.equals(lastJobId)) {
					lastJobId = jobId;
					lastAck = new JsonObject();
					lastAck.addProperty("session", session);
					lastAck.addProperty("id", jobId);
					try {
						JsonArray actual = QuestHubFiles.snapshot(sessionDefinitions);
						if (!signature(actual).equals(revisionSignature))
							throw new IllegalStateException("Local quest files changed; open a new editor session");
						boolean active = sessionDefinitions.equals(world.resolve("questplus"));
						Path backups = active ? world.resolve("questplus-hub-backups")
								: defaults().getParent().resolve("default-backups");
						QuestHubFiles.applyTo(sessionDefinitions, backups, job.getAsJsonArray("files"),
								QuestPlusMod.runtime(), active, true);
						revisionSignature = signature(QuestHubFiles.snapshot(sessionDefinitions));
						lastAck.addProperty("success", true);
						messageOwner(
								Component
										.literal(active ? "World quests saved and reloaded."
												: "Default quests saved for new worlds.")
										.withStyle(ChatFormatting.GREEN));
					} catch (Exception e) {
						lastAck.addProperty("success", false);
						lastAck.addProperty("error", e.getMessage());
						messageOwner(Component
								.translatableWithFallback("questplus.hub.save_failed",
										"Save rejected. Previous quests are kept: %s", e.getMessage())
								.withStyle(ChatFormatting.RED));
					}
				}
			}
			if (lastAck != null)
				sendAck();
		}, e -> {
			inFlight = false;
			if (e.contains("expired")) {
				session = null;
				messageOwner("Editor session expired");
			} else
				QuestPlusMod.LOGGER.warn("QuestPlus Hub poll failed: {}", e);
		});
	}

	private void sendAck() {
		if (lastAck == null)
			return;
		JsonObject ack = lastAck.deepCopy();
		request("POST", "/api/server/ack", ack, result -> {
			if (lastAck != null && lastAck.get("id").getAsString().equals(ack.get("id").getAsString()))
				lastAck = null;
		}, e -> QuestPlusMod.LOGGER.warn("QuestPlus Hub acknowledgement retry pending: {}", e));
	}

	void previewImport(CommandSourceStack source, String packId) {
		if (!configured()) {
			fail(source, "Hub is not configured");
			return;
		}
		if (!packId.matches("[A-Za-z0-9_-]{32}")) {
			fail(source, "Invalid pack ID");
			return;
		}
		UUID id;
		try {
			id = source.getPlayerOrException().getUUID();
		} catch (Exception ex) {
			fail(source, "Import requires an in-game operator");
			return;
		}
		request("GET", "/api/server/packs/" + packId, null, result -> {
			try {
				JsonArray files = result.getAsJsonArray("files");
				QuestHubFiles.validate(files);
				long quests = 0, categories = 0;
				for (var el : files) {
					String name = el.getAsJsonObject().get("name").getAsString();
					if (name.startsWith("quests/"))
						quests++;
					if (name.startsWith("categories/"))
						categories++;
				}
				imports.put(id, new PendingImport(packId, files, System.currentTimeMillis() + 120_000));
				notify(source, "Pack " + packId + ": " + quests + " quests, " + categories
						+ " categories. Confirm within 2 minutes.");
				source.sendSuccess(() -> chat(commandLink(
						Component.translatableWithFallback("questplus.hub.import_pack", "[Import this pack]"),
						"/questplus import confirm")), false);
			} catch (Exception e) {
				fail(source, "Pack rejected: " + e.getMessage());
			}
		}, e -> fail(source, "Import failed: " + e));
	}

	void previewRetrieve(CommandSourceStack source) {
		try {
			UUID id = source.getPlayerOrException().getUUID();
			JsonArray files = QuestHubFiles.snapshot(defaults());
			String current = signature(QuestHubFiles.snapshot(world.resolve("questplus")));
			retrieves.put(id, new PendingRetrieve(files, current, System.currentTimeMillis() + 120_000));
			notify(source,
					"Replace this world's quest definitions with the current default pack? Player progress is retained. Confirm within 2 minutes.");
			source.sendSuccess(
					() -> chat(commandLink(Component.literal("[Confirm replacement]"), "/questplus retrieve confirm")),
					false);
		} catch (Exception e) {
			fail(source, "Cannot prepare replacement: " + e.getMessage());
		}
	}

	void confirmRetrieve(CommandSourceStack source) {
		try {
			UUID id = source.getPlayerOrException().getUUID();
			PendingRetrieve pending = retrieves.remove(id);
			if (pending == null || pending.expires() < System.currentTimeMillis())
				throw new IllegalStateException("No pending replacement or confirmation expired");
			if (!pending.worldSignature().equals(signature(QuestHubFiles.snapshot(world.resolve("questplus"))))
					|| !signature(pending.files()).equals(signature(QuestHubFiles.snapshot(defaults()))))
				throw new IllegalStateException("Quest files changed; run /questplus retrieve again");
			QuestHubFiles.applyTo(world.resolve("questplus"), world.resolve("questplus-hub-backups"), pending.files(),
					QuestPlusMod.runtime(), true, false);
			dropSession();
			notify(source,
					"Default quests installed; previous definitions were backed up and player progress was retained.");
		} catch (Exception e) {
			fail(source, "Replacement failed: " + e.getMessage());
		}
	}

	void rollback(CommandSourceStack source) {
		try {
			QuestHubFiles.rollback(world, QuestPlusMod.runtime());
			dropSession();
			notify(source, "Restored the latest pre-publication backup; clients resynchronized.");
		} catch (Exception e) {
			fail(source, "Rollback failed: " + e.getMessage());
		}
	}

	private void dropSession() {
		String id = session;
		session = null;
		pendingApproval = null;
		lastAck = null;
		lastJobId = null;
		if (id != null) {
			JsonObject body = new JsonObject();
			body.addProperty("session", id);
			request("POST", "/api/server/revoke", body, result -> {
			}, e -> QuestPlusMod.LOGGER.warn("Could not revoke old Hub session: {}", e));
		}
	}

	void disconnect(UUID player) {
		if (owner != null && owner.equals(player))
			dropSession();
		imports.remove(player);
		retrieves.remove(player);
	}

	void confirmImport(CommandSourceStack source) {
		UUID id;
		try {
			id = source.getPlayerOrException().getUUID();
		} catch (Exception e) {
			fail(source, "Requires an operator");
			return;
		}
		PendingImport pending = imports.remove(id);
		if (pending == null || pending.expires() < System.currentTimeMillis()) {
			fail(source, "No pending import or approval expired");
			return;
		}
		try {
			QuestHubFiles.apply(world, pending.files(), QuestPlusMod.runtime());
			dropSession();
			notify(source, "Pack " + pending.id() + " installed. Multiplayer clients have been resynchronized.");
		} catch (Exception e) {
			fail(source, "Pack rejected, existing quests restored: " + e.getMessage());
		}
	}

	@Override
	public void close() {
		closed = true;
		session = null;
		imports.clear();
		retrieves.clear();
		pendingApproval = null;
		lastAck = null;
	}
}
