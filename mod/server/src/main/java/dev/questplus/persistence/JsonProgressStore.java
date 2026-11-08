package dev.questplus.persistence;

import com.google.gson.*;
import dev.questplus.core.PlayerProgress;
import dev.questplus.core.QuestDefinition;
import dev.questplus.core.QuestRegistry;
import dev.questplus.team.TeamData;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

public final class JsonProgressStore implements AutoCloseable {
	private static final int MAX_BYTES = 8 * 1024 * 1024;
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();
	private final Path root;
	private final Path players;
	private final Path journal;
	private final Path inventoryJournal;
	private final SavedMarker savedMarker;
	private final WriteHook hook;
	private final AtomicMove move;
	private volatile boolean poisoned;
	private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(128), new ThreadPoolExecutor.AbortPolicy());

	@FunctionalInterface
	public interface SavedMarker {
		String read(UUID player) throws IOException;

		default void verifyInventory(UUID player, JsonArray expected) throws IOException {
			throw new IOException("Saved inventory verification is required");
		}

		default void verifyExperience(UUID player, JsonObject expected) throws IOException {
			throw new IOException("Saved experience verification is required");
		}
	}

	@FunctionalInterface
	interface WriteHook {
		void after(String point) throws IOException;
	}

	@FunctionalInterface
	interface AtomicMove {
		void move(Path source, Path target) throws IOException;
	}

	private record Image(String path, String before, String after, String json) {
	}

	private record Operation(int schemaVersion, UUID id, boolean committed, List<Image> images) {
	}

	private record InventoryOperation(int schemaVersion, UUID id, UUID player, String previousMarker,
			JsonArray inventoryBefore, JsonArray inventoryAfter, List<Image> images, JsonObject experienceBefore,
			JsonObject experienceAfter) {
	}

	public record ClaimAudit(UUID id, UUID player, String claim, String operator, String outcome, String reason,
			long timestamp) {
	}

	public JsonProgressStore(Path root) throws IOException {
		this(root, point -> {
		}, null);
	}

	public static JsonProgressStore withSavedMarker(Path root, SavedMarker savedMarker) throws IOException {
		return new JsonProgressStore(root, point -> {
		}, savedMarker);
	}

	JsonProgressStore(Path root, WriteHook hook) throws IOException {
		this(root, hook, null);
	}

	JsonProgressStore(Path root, WriteHook hook, SavedMarker savedMarker) throws IOException {
		this(root, hook, savedMarker, (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING));
	}

	JsonProgressStore(Path root, WriteHook hook, SavedMarker savedMarker, AtomicMove move) throws IOException {
		this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
		players = this.root.resolve("players");
		journal = this.root.resolve("transactions/pending.json");
		inventoryJournal = this.root.resolve("transactions/inventory.json");
		this.savedMarker = savedMarker;
		this.hook = Objects.requireNonNull(hook);
		this.move = Objects.requireNonNull(move);
		recoverInventory();
		recover();
	}

	public PlayerProgress load(UUID id) throws IOException {
		Path file = file(id);
		if (Files.notExists(file)) {
			if (Files.exists(backup(file)) || Files.exists(temporary(file)) || Files.exists(backupTemporary(file)))
				throw new IOException("Progress missing while backup or temporary file exists: " + file);
			return new PlayerProgress();
		}
		return decode(read(file));
	}

	public CompletableFuture<Void> save(UUID id, PlayerProgress player) {
		return saveBatch(Map.of(id, player));
	}

	/**
	 * One queued batch avoids starving durable claims behind hundreds of queued
	 * autosave requests.
	 */
	public CompletableFuture<Void> saveBatch(Map<UUID, PlayerProgress> states) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		try {
			Map<Path, byte[]> snapshots = new LinkedHashMap<>();
			for (var entry : states.entrySet()) {
				entry.getValue().validate();
				byte[] bytes = GSON.toJson(entry.getValue()).getBytes(StandardCharsets.UTF_8);
				if (bytes.length > MAX_BYTES)
					throw new IOException("Progress exceeds 8 MiB");
				decode(bytes);
				snapshots.put(file(entry.getKey()), bytes);
			}
			writer.execute(() -> {
				try {
					checkPending();
					IOException failures = null;
					for (var entry : snapshots.entrySet()) {
						try {
							write(entry.getKey(), entry.getValue());
						} catch (IOException error) {
							if (failures == null)
								failures = error;
							else
								failures.addSuppressed(error);
						}
					}
					if (failures != null)
						throw failures;
					future.complete(null);
				} catch (Exception e) {
					future.completeExceptionally(e);
				}
			});
		} catch (Exception e) {
			future.completeExceptionally(e);
		}
		return future;
	}

	/**
	 * Non-critical autosave only: snapshot mutable state on the server thread, then
	 * perform JSON serialization + strict decode on the single IO worker. The queue
	 * is bounded and ordered with reward transactions; callers must re-dirty the
	 * same player IDs if the returned future fails.
	 */
	public CompletableFuture<Void> saveAutosaveBatch(Map<UUID, PlayerProgress> states) {
		Map<UUID, PlayerProgress> detached;
		try {
			detached = new LinkedHashMap<>(states.size());
			for (var entry : states.entrySet())
				detached.put(entry.getKey(), entry.getValue().copyForAutosave());
		} catch (Exception error) {
			return CompletableFuture.failedFuture(error);
		}
		return queued(() -> {
			checkPending();
			IOException failures = null;
			for (var entry : detached.entrySet()) {
				try {
					var state = entry.getValue();
					state.validate();
					byte[] bytes = GSON.toJson(state).getBytes(StandardCharsets.UTF_8);
					if (bytes.length > MAX_BYTES)
						throw new IOException("Progress exceeds 8 MiB");
					decode(bytes);
					write(file(entry.getKey()), bytes);
				} catch (IOException error) {
					if (failures == null)
						failures = error;
					else
						failures.addSuppressed(error);
				} catch (RuntimeException error) {
					var failure = new IOException("Autosave validation failed for " + entry.getKey(), error);
					if (failures == null)
						failures = failure;
					else
						failures.addSuppressed(failure);
				}
			}
			if (failures != null)
				throw failures;
		});
	}

	public static PlayerProgress detachedCopy(PlayerProgress source) {
		var copy = GSON.fromJson(GSON.toJson(source), PlayerProgress.class);
		copy.revision = source.revision;
		copy.recentEvents = source.recentEvents == null ? new LinkedHashSet<>()
				: new LinkedHashSet<>(source.recentEvents);
		copy.migratedFromV1 = source.migratedFromV1;
		copy.validate();
		return copy;
	}

	public Map<UUID, TeamData> loadTeams() throws IOException {
		Map<UUID, TeamData> result = new LinkedHashMap<>();
		Path directory = root.resolve("teams");
		if (!Files.exists(directory))
			return result;
		try (var files = Files.list(directory)) {
			for (Path file : files.sorted().toList()) {
				String name = file.getFileName().toString();
				if (name.endsWith(".json")) {
					TeamData team = decodeTeam(read(file));
					if (!file.equals(teamFile(team.id)))
						throw new IOException("Team filename/ID mismatch: " + file);
					result.put(team.id, team);
				} else {
					int suffix = name.indexOf(".json.");
					if (suffix >= 0 && Files.notExists(directory.resolve(name.substring(0, suffix + 5))))
						throw new IOException("Team primary missing while recovery file exists: " + file);
				}
			}
		}
		return result;
	}

	/**
	 * Snapshots are captured on the caller thread and all disk writes share the
	 * ordinary save queue.
	 */
	public CompletableFuture<Void> transaction(Map<UUID, PlayerProgress> states, Map<UUID, TeamData> teams) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		try {
			Map<Path, byte[]> snapshots = snapshots(states, teams);
			writer.execute(() -> {
				try {
					checkPending();
					transact(snapshots);
					future.complete(null);
				} catch (Exception e) {
					future.completeExceptionally(e);
				}
			});
		} catch (Exception e) {
			future.completeExceptionally(e);
		}
		return future;
	}

	private Map<Path, byte[]> snapshots(Map<UUID, PlayerProgress> states, Map<UUID, TeamData> teams)
			throws IOException {
		Map<Path, byte[]> snapshots = new LinkedHashMap<>();
		for (var entry : new TreeMap<>(states).entrySet()) {
			entry.getValue().validate();
			byte[] bytes = GSON.toJson(entry.getValue()).getBytes(StandardCharsets.UTF_8);
			decode(bytes);
			snapshots.put(file(entry.getKey()), bytes);
		}
		for (var entry : new TreeMap<>(teams).entrySet()) {
			if (!entry.getKey().equals(entry.getValue().id))
				throw new IllegalArgumentException("Team key/ID mismatch");
			entry.getValue().validate();
			byte[] bytes = GSON.toJson(entry.getValue()).getBytes(StandardCharsets.UTF_8);
			decodeTeam(bytes);
			snapshots.put(teamFile(entry.getKey()), bytes);
		}
		if (snapshots.isEmpty())
			throw new IllegalArgumentException("Empty transaction");
		for (byte[] bytes : snapshots.values())
			if (bytes.length > MAX_BYTES)
				throw new IOException("Progress exceeds 8 MiB");
		return snapshots;
	}

	/**
	 * Intent is durable before the caller may touch inventory. All arguments are
	 * detached here.
	 */
	public CompletableFuture<Void> prepareInventory(UUID operation, UUID player, String previousMarker,
			JsonArray before, JsonArray after, Map<UUID, PlayerProgress> states, Map<UUID, TeamData> teams) {
		return prepareInventory(operation, player, previousMarker, before, after, states, teams, null, null);
	}

	public CompletableFuture<Void> prepareInventory(UUID operation, UUID player, String previousMarker,
			JsonArray before, JsonArray after, Map<UUID, PlayerProgress> states, Map<UUID, TeamData> teams,
			JsonObject experienceBefore, JsonObject experienceAfter) {
		try {
			Map<Path, byte[]> snapshots = snapshots(states, teams);
			JsonArray beforeCopy = before.deepCopy(), afterCopy = after.deepCopy();
			var experienceBeforeCopy = experienceBefore == null ? null : experienceBefore.deepCopy();
			var experienceAfterCopy = experienceAfter == null ? null : experienceAfter.deepCopy();
			return queued(() -> {
				checkPending();
				var intent = new InventoryOperation(1, operation, player, previousMarker, beforeCopy, afterCopy,
						images(snapshots), experienceBeforeCopy, experienceAfterCopy);
				validateInventory(intent);
				byte[] bytes = GSON.toJson(intent).getBytes(StandardCharsets.UTF_8);
				if (bytes.length > 128 * 1024 * 1024)
					throw new IOException("Inventory journal exceeds 128 MiB");
				atomic(inventoryJournal, bytes);
				hook.after("inventory:intent");
			});
		} catch (Exception error) {
			return CompletableFuture.failedFuture(error);
		}
	}

	public CompletableFuture<Void> commitInventory(UUID operation) {
		return queued(() -> {
			InventoryOperation intent = readInventory();
			if (!intent.id().equals(operation))
				throw new IOException("Inventory operation mismatch");
			if (savedMarker == null || !operation.toString().equals(savedMarker.read(intent.player())))
				throw new IOException("Inventory consumption has not been saved");
			savedMarker.verifyInventory(intent.player(), intent.inventoryAfter());
			if (intent.experienceAfter() != null)
				savedMarker.verifyExperience(intent.player(), intent.experienceAfter());
			applyInventory(intent);
		});
	}

	public CompletableFuture<Void> resolveClaim(UUID player, PlayerProgress after, ClaimAudit audit) {
		return resolveClaim(player, after, audit, Map.of());
	}

	public CompletableFuture<Void> resolveClaim(UUID player, PlayerProgress after, ClaimAudit audit,
			Map<UUID, TeamData> teams) {
		try {
			if (!player.equals(audit.player()))
				throw new IllegalArgumentException("Audit player mismatch");
			Map<Path, byte[]> snapshots = snapshots(Map.of(player, after), teams);
			Path file = root.resolve("audit/" + audit.id() + ".json");
			byte[] bytes = GSON.toJson(audit).getBytes(StandardCharsets.UTF_8);
			validateImage(file, bytes);
			snapshots.put(file, bytes);
			return queued(() -> {
				checkPending();
				if (Files.exists(file))
					throw new IOException("Duplicate audit ID");
				transact(snapshots);
			});
		} catch (Exception error) {
			return CompletableFuture.failedFuture(error);
		}
	}

	@FunctionalInterface
	private interface IOAction {
		void run() throws IOException;
	}

	private CompletableFuture<Void> queued(IOAction action) {
		var future = new CompletableFuture<Void>();
		try {
			writer.execute(() -> {
				try {
					action.run();
					future.complete(null);
				} catch (Exception error) {
					future.completeExceptionally(error);
				}
			});
		} catch (Exception error) {
			future.completeExceptionally(error);
		}
		return future;
	}

	private InventoryOperation readInventory() throws IOException {
		try {
			if (Files.size(inventoryJournal) > 128 * 1024 * 1024)
				throw new IOException("Inventory journal exceeds 128 MiB");
			JsonObject value;
			try (Reader reader = Files.newBufferedReader(inventoryJournal, StandardCharsets.UTF_8)) {
				value = object(QuestRegistry.parseStrict(reader));
			}
			var allowed = Set.of("schemaVersion", "id", "player", "previousMarker", "inventoryBefore", "inventoryAfter",
					"images", "experienceBefore", "experienceAfter");
			if (!allowed.containsAll(value.keySet()) || !value.keySet().containsAll(Set.of("schemaVersion", "id",
					"player", "previousMarker", "inventoryBefore", "inventoryAfter", "images")))
				throw new IOException("Invalid inventory journal fields");
			for (String field : List.of("experienceBefore", "experienceAfter"))
				if (value.has(field) && value.get(field).isJsonNull())
					value.remove(field);
			var intent = GSON.fromJson(value, InventoryOperation.class);
			validateInventory(intent);
			return intent;
		} catch (RuntimeException error) {
			throw new IOException("Invalid inventory journal", error);
		}
	}

	private void validateInventory(InventoryOperation intent) throws IOException {
		if (intent.schemaVersion() != 1 || intent.id() == null || intent.player() == null
				|| intent.previousMarker() == null || intent.inventoryBefore() == null
				|| intent.inventoryAfter() == null || intent.inventoryBefore().size() != 37
				|| intent.inventoryAfter().size() != 37 || intent.images() == null || intent.images().isEmpty())
			throw new IOException("Invalid inventory intent");
		if (!intent.previousMarker().isEmpty())
			UUID.fromString(intent.previousMarker());
		if ((intent.experienceBefore() == null) != (intent.experienceAfter() == null))
			throw new IOException("Incomplete experience intent");
		if (intent.experienceBefore() != null)
			for (var xp : List.of(intent.experienceBefore(), intent.experienceAfter())) {
				fields(xp, "level", "progress", "total");
				if (!xp.keySet().equals(Set.of("level", "progress", "total")) || integer(xp.get("level")) < 0
						|| integer(xp.get("total")) < 0 || !xp.get("progress").isJsonPrimitive()
						|| !xp.getAsJsonPrimitive("progress").isNumber()
						|| !Double.isFinite(xp.get("progress").getAsDouble()) || xp.get("progress").getAsDouble() < 0
						|| xp.get("progress").getAsDouble() >= 1)
					throw new IOException("Invalid experience intent");
			}
		Set<Path> targets = new HashSet<>();
		for (Image image : intent.images()) {
			Path target = resolveImage(image.path());
			if (!targets.add(target) || image.json() == null || image.after() == null
					|| image.before() != null && !image.before().matches("[a-f0-9]{64}"))
				throw new IOException("Invalid inventory image");
			byte[] bytes = image.json().getBytes(StandardCharsets.UTF_8);
			if (bytes.length > MAX_BYTES || !hash(bytes).equals(image.after()))
				throw new IOException("Invalid inventory image hash/size");
			validateImage(target, bytes);
		}
	}

	private void recoverInventory() throws IOException {
		if (Files.notExists(inventoryJournal)) {
			if (Files.exists(temporary(inventoryJournal)))
				throw new IOException("Incomplete inventory journal; retain for review");
			return;
		}
		if (savedMarker == null)
			throw new IOException("Inventory recovery requires vanilla player save evidence");
		var intent = readInventory();
		String marker = savedMarker.read(intent.player());
		if (intent.id().toString().equals(marker)) {
			savedMarker.verifyInventory(intent.player(), intent.inventoryAfter());
			if (intent.experienceAfter() != null)
				savedMarker.verifyExperience(intent.player(), intent.experienceAfter());
			applyInventory(intent);
			return;
		}
		if (!intent.previousMarker().equals(marker))
			throw new IOException("Unknown inventory marker; retain files for review");
		savedMarker.verifyInventory(intent.player(), intent.inventoryBefore());
		if (intent.experienceBefore() != null)
			savedMarker.verifyExperience(intent.player(), intent.experienceBefore());
		// The old marker proves the withdrawal was never saved. Never restore inventory
		// or replay the event.
		checkImages(intent.images(), false);
		Files.delete(inventoryJournal);
		Files.deleteIfExists(temporary(inventoryJournal));
	}

	private void applyInventory(InventoryOperation intent) throws IOException {
		checkImages(intent.images(), true);
		try {
			for (Image image : intent.images()) {
				Path target = resolveImage(image.path());
				if (Files.exists(target) && hash(read(target)).equals(image.after()))
					continue;
				// Hashes already proved this target belongs to the pending intent, including
				// interrupted temporary files.
				atomic(target, image.json().getBytes(StandardCharsets.UTF_8));
				hook.after("inventory:file:" + image.path());
			}
			hook.after("inventory:commit");
			Files.delete(inventoryJournal);
			Files.deleteIfExists(temporary(inventoryJournal));
		} catch (IOException | RuntimeException error) {
			poisoned = true;
			throw error;
		}
	}

	private void checkImages(List<Image> images, boolean allowAfter) throws IOException {
		for (Image image : images) {
			Path target = resolveImage(image.path());
			String current = Files.exists(target) ? hash(read(target)) : null;
			if (!Objects.equals(current, image.before()) && !(allowAfter && Objects.equals(current, image.after())))
				throw new IOException("Inventory recovery conflict for " + image.path() + "; retain files for review");
		}
	}

	private List<Image> images(Map<Path, byte[]> snapshots) throws IOException {
		List<Image> images = new ArrayList<>();
		for (var entry : snapshots.entrySet()) {
			Path target = entry.getKey();
			String before = null;
			if (Files.exists(target)) {
				byte[] old = read(target);
				validateImage(target, old);
				before = hash(old);
			} else
				rejectLeftovers(target);
			images.add(new Image(root.relativize(target).toString().replace('\\', '/'), before, hash(entry.getValue()),
					new String(entry.getValue(), StandardCharsets.UTF_8)));
		}
		return List.copyOf(images);
	}

	private void checkPending() throws IOException {
		if (poisoned || Files.exists(journal) || Files.exists(inventoryJournal)
				|| Files.exists(temporary(inventoryJournal)))
			throw new IOException("Pending Quest Plus transaction requires restart/recovery");
	}

	private void transact(Map<Path, byte[]> snapshots) throws IOException {
		List<Image> images = images(snapshots);
		Operation operation = new Operation(1, UUID.randomUUID(), false, List.copyOf(images));
		byte[] bytes = GSON.toJson(operation).getBytes(StandardCharsets.UTF_8);
		if (bytes.length > 128 * 1024 * 1024)
			throw new IOException("Transaction journal exceeds 128 MiB");
		try {
			atomic(journal, bytes);
			hook.after("journal");
			for (Image image : images) {
				write(resolveImage(image.path()), image.json().getBytes(StandardCharsets.UTF_8));
				hook.after("file:" + image.path());
			}
			atomic(journal,
					GSON.toJson(new Operation(1, operation.id(), true, images)).getBytes(StandardCharsets.UTF_8));
			hook.after("commit");
			Files.delete(journal);
		} catch (IOException | RuntimeException e) {
			poisoned = true;
			throw e;
		}
	}

	private void recover() throws IOException {
		if (Files.notExists(journal)) {
			if (Files.exists(temporary(journal)))
				throw new IOException("Incomplete transaction journal: " + temporary(journal));
			return;
		}
		Operation operation;
		try {
			if (Files.size(journal) > 128 * 1024 * 1024)
				throw new IOException("Transaction journal exceeds 128 MiB");
			JsonObject value;
			try (Reader reader = Files.newBufferedReader(journal, StandardCharsets.UTF_8)) {
				value = object(QuestRegistry.parseStrict(reader));
			}
			fields(value, "schemaVersion", "id", "committed", "images");
			if (integer(value.get("schemaVersion")) != 1)
				throw new IllegalArgumentException("Unsupported journal version");
			UUID.fromString(text(value.get("id")));
			bool(value.get("committed"));
			if (!value.get("images").isJsonArray() || value.getAsJsonArray("images").isEmpty())
				throw new IllegalArgumentException("Empty journal");
			Set<Path> targets = new HashSet<>();
			for (JsonElement item : value.getAsJsonArray("images")) {
				JsonObject image = object(item);
				fields(image, "path", "before", "after", "json");
				Path target = resolveImage(text(image.get("path")));
				if (!targets.add(target))
					throw new IllegalArgumentException("Duplicate journal path");
				if (!image.get("before").isJsonNull() && !text(image.get("before")).matches("[a-f0-9]{64}"))
					throw new IllegalArgumentException("Invalid before hash");
				byte[] after = text(image.get("json")).getBytes(StandardCharsets.UTF_8);
				if (after.length > MAX_BYTES)
					throw new IOException("Journal image exceeds 8 MiB");
				validateImage(target, after);
				if (!hash(after).equals(text(image.get("after"))))
					throw new IllegalArgumentException("Invalid after hash");
			}
			operation = GSON.fromJson(value, Operation.class);
		} catch (RuntimeException e) {
			throw new IOException("Invalid transaction journal: " + e.getMessage(), e);
		}
		// Check the entire operation before changing any file, even when its commit
		// marker is present.
		for (Image image : operation.images()) {
			Path target = resolveImage(image.path());
			String current = Files.exists(target) ? hash(read(target)) : null;
			if (!Objects.equals(current, image.before()) && !Objects.equals(current, image.after()))
				throw new IOException(
						"Transaction recovery conflict for " + image.path() + "; retain all files for repair");
		}
		for (Image image : operation.images()) {
			Path target = resolveImage(image.path());
			if (Files.exists(target) && hash(read(target)).equals(image.after()))
				continue;
			atomic(target, image.json().getBytes(StandardCharsets.UTF_8));
		}
		Files.delete(journal);
		Files.deleteIfExists(temporary(journal));
	}

	private Path resolveImage(String path) throws IOException {
		if (!path.matches("(?:players|teams|audit)/[a-fA-F0-9-]{36}\\.json"))
			throw new IOException("Invalid transaction path " + path);
		UUID.fromString(path.substring(path.indexOf('/') + 1, path.length() - 5));
		Path target = root.resolve(path).normalize();
		if (!target.startsWith(root))
			throw new IOException("Transaction path escaped world data");
		return target;
	}

	private static String hash(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private void validateImage(Path path, byte[] bytes) throws IOException {
		if (path.getParent().equals(root.resolve("audit"))) {
			try {
				JsonObject value = object(
						QuestRegistry.parseStrict(new StringReader(new String(bytes, StandardCharsets.UTF_8))));
				fields(value, "id", "player", "claim", "operator", "outcome", "reason", "timestamp");
				ClaimAudit audit = GSON.fromJson(value, ClaimAudit.class);
				if (audit.id() == null || audit.player() == null
						|| !path.getFileName().toString().equals(audit.id() + ".json") || audit.claim() == null
						|| audit.claim().isBlank() || audit.operator() == null || audit.operator().isBlank()
						|| !Set.of("delivered", "discard").contains(audit.outcome()) || audit.reason() == null
						|| audit.reason().isBlank() || audit.reason().length() > 1024 || audit.timestamp() < 0)
					throw new IllegalArgumentException("Invalid claim audit");
			} catch (RuntimeException error) {
				throw new IOException("Invalid claim audit", error);
			}
		} else if (path.getParent().equals(players))
			decode(bytes);
		else {
			TeamData team = decodeTeam(bytes);
			if (!path.equals(teamFile(team.id)))
				throw new IOException("Team filename/ID mismatch");
		}
	}

	private static void rejectLeftovers(Path file) throws IOException {
		if (Files.exists(backup(file)) || Files.exists(temporary(file)) || Files.exists(backupTemporary(file)))
			throw new IOException("Progress missing while backup or temporary file exists: " + file);
	}

	private void atomic(Path target, byte[] bytes) throws IOException {
		Files.createDirectories(target.getParent());
		writeForced(temporary(target), bytes);
		move(temporary(target), target);
	}

	private void move(Path source, Path target) throws IOException {
		for (int attempt = 0;; attempt++) {
			try {
				move.move(source, target);
				return;
			} catch (AccessDeniedException locked) {
				if (attempt == 4)
					throw locked;
				try {
					Thread.sleep(10L << attempt);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					var error = new InterruptedIOException("Interrupted atomic rename: " + target);
					error.initCause(locked);
					throw error;
				}
			}
		}
	}

	private Path teamFile(UUID id) {
		return root.resolve("teams").resolve(Objects.requireNonNull(id) + ".json");
	}

	private void write(Path file, byte[] bytes) throws IOException {
		Files.createDirectories(file.getParent());
		if (Files.exists(file)) {
			byte[] old = read(file);
			validateImage(file, old);
			writeForced(backupTemporary(file), old);
			move(backupTemporary(file), backup(file));
		} else if (Files.exists(backup(file)) || Files.exists(temporary(file)) || Files.exists(backupTemporary(file))) {
			throw new IOException("Progress missing while backup or temporary file exists: " + file);
		}
		writeForced(temporary(file), bytes);
		move(temporary(file), file);
	}

	private static void writeForced(Path path, byte[] bytes) throws IOException {
		try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			while (buffer.hasRemaining())
				channel.write(buffer);
			channel.force(true);
		}
	}

	private static byte[] read(Path file) throws IOException {
		if (Files.size(file) > MAX_BYTES)
			throw new IOException("Progress exceeds 8 MiB: " + file);
		byte[] bytes = Files.readAllBytes(file);
		if (bytes.length > MAX_BYTES)
			throw new IOException("Progress exceeds 8 MiB: " + file);
		return bytes;
	}

	private static PlayerProgress decode(byte[] bytes) throws IOException {
		try {
			JsonElement parsed = QuestRegistry.parseStrict(new InputStreamReader(new ByteArrayInputStream(bytes),
					StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
							.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)));
			JsonObject root = object(parsed);
			long version = integer(root.get("schemaVersion"));
			if (version == 1)
				fields(root, "schemaVersion", "quests", "claims");
			else if (version == 2)
				fields(root, "schemaVersion", "quests", "claims", "entitlements", "teamId", "legacyUnclaimed");
			else
				throw new IllegalArgumentException("Unsupported schemaVersion " + version);
			JsonObject quests = object(root.get("quests"));
			JsonObject claims = object(root.get("claims"));
			for (var entry : quests.entrySet()) {
				JsonObject q = object(entry.getValue());
				if (version == 1)
					fields(q, "counters", "completed", "fingerprint");
				else
					fields(q, "counters", "completed", "fingerprint", "cumulative", "cycles", "tier", "attemptId",
							"startedAt", "waitingRestart", "terminal", "reachedTiers");
				JsonObject counters = object(q.get("counters"));
				for (var counter : counters.entrySet())
					integer(counter.getValue());
				if (!q.has("completed") || !q.get("completed").isJsonPrimitive()
						|| !q.get("completed").getAsJsonPrimitive().isBoolean())
					throw new IllegalArgumentException("Invalid completed");
				string(q.get("fingerprint"));
				if (version == 2) {
					for (var counter : object(q.get("cumulative")).entrySet())
						integer(counter.getValue());
					integer(q.get("cycles"));
					integer(q.get("tier"));
					integer(q.get("startedAt"));
					if (!q.get("attemptId").isJsonNull())
						UUID.fromString(text(q.get("attemptId")));
					bool(q.get("waitingRestart"));
					bool(q.get("terminal"));
					strings(q.get("reachedTiers"));
				}
			}
			for (var claim : claims.entrySet())
				string(claim.getValue());
			if (version == 2) {
				if (!root.get("teamId").isJsonNull())
					UUID.fromString(text(root.get("teamId")));
				strings(root.get("legacyUnclaimed"));
				for (var entitlement : object(root.get("entitlements")).entrySet()) {
					JsonObject grant = object(entitlement.getValue());
					fields(grant, "questId", "rewards");
					string(grant.get("questId"));
					if (!grant.get("rewards").isJsonArray())
						throw new IllegalArgumentException("Invalid reward grant");
					for (JsonElement reward : grant.getAsJsonArray("rewards")) {
						JsonObject r = object(reward);
						if (!r.has("policy"))
							r.add("policy", GSON.toJsonTree(QuestDefinition.RewardPolicy.DEFAULT));
						fields(r, "type", "item", "count", "amount", "data", "text", "policy");
						if (r.has("policy") && !r.get("policy").isJsonNull()) {
							var policy = object(r.get("policy"));
							fields(policy, "teamReward", "autoClaim", "excludeFromClaimAll", "teamScope");
							bool(policy.get("teamReward"));
							bool(policy.get("excludeFromClaimAll"));
							string(policy.get("autoClaim"));
							if (policy.has("teamScope") && !policy.get("teamScope").isJsonNull())
								UUID.fromString(text(policy.get("teamScope")));
						}
						string(r.get("type"));
						if (!r.get("item").isJsonNull())
							string(r.get("item"));
						integer(r.get("count"));
						integer(r.get("amount"));
						string(r.get("data"));
						for (var translation : object(r.get("text")).entrySet())
							string(translation.getValue());
					}
				}
			}
			PlayerProgress player = GSON.fromJson(root, PlayerProgress.class);
			if (version == 1) {
				player.schemaVersion = 2;
				player.migratedFromV1 = true;
				player.quests.forEach((id, q) -> {
					q.terminal = q.completed;
					if (q.completed && !player.claims.containsKey(id))
						player.legacyUnclaimed.add(id);
				});
			}
			player.validate();
			return player;
		} catch (RuntimeException e) {
			throw new IOException("Invalid player progress: " + e.getMessage(), e);
		}
	}

	private static TeamData decodeTeam(byte[] bytes) throws IOException {
		try {
			JsonObject root = object(QuestRegistry.parseStrict(new InputStreamReader(new ByteArrayInputStream(bytes),
					StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
							.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT))));
			if (!root.has("rewardClaims"))
				root.add("rewardClaims", new JsonObject());
			if (!root.has("managers"))
				root.add("managers", new JsonArray());
			if (!root.has("sharedQuestProgress"))
				root.addProperty("sharedQuestProgress", true);
			fields(root, "schemaVersion", "id", "name", "owner", "members", "invitations", "progress", "disbanded",
					"rewardClaims", "managers", "sharedQuestProgress");
			if (integer(root.get("schemaVersion")) != 1)
				throw new IllegalArgumentException("Unsupported team version");
			UUID.fromString(text(root.get("id")));
			UUID.fromString(text(root.get("owner")));
			string(root.get("name"));
			bool(root.get("disbanded"));
			bool(root.get("sharedQuestProgress"));
			if (!root.get("managers").isJsonArray())
				throw new IllegalArgumentException("Invalid team managers");
			Set<UUID> managers = new HashSet<>();
			for (JsonElement manager : root.getAsJsonArray("managers"))
				if (!managers.add(UUID.fromString(text(manager))))
					throw new IllegalArgumentException("Duplicate team manager");
			if (!root.get("members").isJsonArray())
				throw new IllegalArgumentException("Invalid team members");
			Set<UUID> members = new HashSet<>();
			for (JsonElement member : root.getAsJsonArray("members"))
				if (!members.add(UUID.fromString(text(member))))
					throw new IllegalArgumentException("Duplicate team member");
			for (var invitation : object(root.get("invitations")).entrySet()) {
				UUID.fromString(invitation.getKey());
				integer(invitation.getValue());
			}
			PlayerProgress progress = decode(GSON.toJson(root.get("progress")).getBytes(StandardCharsets.UTF_8));
			TeamData team = GSON.fromJson(root, TeamData.class);
			team.progress = progress;
			team.validate();
			return team;
		} catch (RuntimeException e) {
			throw new IOException("Invalid team data: " + e.getMessage(), e);
		}
	}

	private static void strings(JsonElement element) {
		if (element == null || !element.isJsonArray())
			throw new IllegalArgumentException("Expected string array");
		Set<String> unique = new HashSet<>();
		for (JsonElement value : element.getAsJsonArray())
			if (!unique.add(text(value)))
				throw new IllegalArgumentException("Duplicate array value");
	}

	private static void bool(JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
			throw new IllegalArgumentException("Expected boolean");
	}

	private static String text(JsonElement value) {
		string(value);
		return value.getAsString();
	}

	private static JsonObject object(JsonElement value) {
		if (value == null || !value.isJsonObject())
			throw new IllegalArgumentException("Expected object");
		return value.getAsJsonObject();
	}

	private static void fields(JsonObject object, String... names) {
		Set<String> required = Set.of(names);
		if (!object.keySet().equals(required))
			throw new IllegalArgumentException("Missing or unknown fields: " + object.keySet());
	}

	private static long integer(JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
			throw new IllegalArgumentException("Expected integer");
		return value.getAsBigDecimal().longValueExact();
	}

	private static void string(JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
			throw new IllegalArgumentException("Expected string");
	}

	private Path file(UUID id) {
		return players.resolve(Objects.requireNonNull(id).toString() + ".json");
	}

	private static Path backup(Path file) {
		return file.resolveSibling(file.getFileName() + ".bak");
	}

	private static Path temporary(Path file) {
		return file.resolveSibling(file.getFileName() + ".tmp");
	}

	private static Path backupTemporary(Path file) {
		return file.resolveSibling(file.getFileName() + ".bak.tmp");
	}

	/**
	 * Final shutdown saves continue past isolated file errors and respect bounded
	 * writer backpressure.
	 */
	public void saveAndClose(Map<UUID, PlayerProgress> states) throws IOException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		IOException failure = null;
		try {
			// Shutdown may wait: retry bounded-queue rejections so no cached player's last
			// snapshot is dropped.
			for (var entry : states.entrySet()) {
				while (true) {
					try {
						save(entry.getKey(), entry.getValue()).get(Math.max(1, deadline - System.nanoTime()),
								TimeUnit.NANOSECONDS);
						break;
					} catch (ExecutionException e) {
						if (!(e.getCause() instanceof RejectedExecutionException)) {
							var error = new IOException("Final save failed for " + entry.getKey(), e.getCause());
							if (failure == null)
								failure = error;
							else
								failure.addSuppressed(error);
							break;
						}
						if (System.nanoTime() >= deadline)
							throw new IOException("Final save queue deadline exceeded", e.getCause());
						Thread.sleep(5);
					} catch (TimeoutException e) {
						throw new IOException("Final save deadline exceeded", e);
					}
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			var error = new IOException("Interrupted final save", e);
			if (failure == null)
				failure = error;
			else
				failure.addSuppressed(error);
		} catch (IOException e) {
			if (failure == null)
				failure = e;
			else
				failure.addSuppressed(e);
		}
		try {
			close();
		} catch (IOException e) {
			if (failure == null)
				failure = e;
			else
				failure.addSuppressed(e);
		}
		if (failure != null)
			throw failure;
	}

	@Override
	public void close() throws IOException {
		writer.shutdown();
		try {
			if (!writer.awaitTermination(30, TimeUnit.SECONDS))
				throw new IOException("Progress writer did not stop within 30 seconds");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while closing progress writer", e);
		}
	}
}
