package dev.questplus.clientmodel;

import dev.questplus.core.QuestDefinition;
import java.util.*;

/**
 * Immutable, screen-independent placement. Coordinates refer to node centres.
 */
public final class QuestLayout {
	public static final double NODE_SIZE = 32, HORIZONTAL_GAP = 64, VERTICAL_GAP = 72;

	public record Point(double x, double y) {
	}

	public record Edge(String from, String to) {
	}

	private final Map<String, Point> points;
	private final List<Edge> edges;
	private final Map<Cell, List<String>> cells;

	private record Cell(long x, long y) {
	}

	private QuestLayout(Map<String, Point> points, List<Edge> edges, Map<Cell, List<String>> cells) {
		this.points = Collections.unmodifiableMap(new LinkedHashMap<>(points));
		this.edges = List.copyOf(edges);
		this.cells = cells;
	}

	public Map<String, Point> points() {
		return points;
	}

	public List<Edge> edges() {
		return edges;
	}

	public static QuestLayout compute(Collection<QuestDefinition> definitions) {
		Map<String, QuestDefinition> quests = new TreeMap<>();
		for (var quest : definitions)
			if (quests.putIfAbsent(quest.id(), quest) != null)
				throw new IllegalArgumentException("Duplicate quest: " + quest.id());
		Map<String, List<String>> parents = new HashMap<>(), children = new HashMap<>();
		Map<String, Integer> pending = new HashMap<>(), ranks = new HashMap<>();
		List<Edge> edges = new ArrayList<>();
		for (var q : quests.values()) {
			Set<String> refs = new TreeSet<>();
			if (q.requires() != null)
				q.requires().questReferences(refs);
			refs.retainAll(quests.keySet());
			parents.put(q.id(), List.copyOf(refs));
			pending.put(q.id(), refs.size());
			for (String ref : refs) {
				children.computeIfAbsent(ref, ignored -> new ArrayList<>()).add(q.id());
				edges.add(new Edge(ref, q.id()));
			}
		}
		PriorityQueue<String> queue = new PriorityQueue<>();
		pending.forEach((id, count) -> {
			if (count == 0)
				queue.add(id);
		});
		Map<Integer, List<String>> layers = new TreeMap<>();
		int visited = 0;
		while (!queue.isEmpty()) {
			String id = queue.remove();
			visited++;
			int rank = ranks.getOrDefault(id, 0);
			layers.computeIfAbsent(rank, ignored -> new ArrayList<>()).add(id);
			for (String child : children.getOrDefault(id, List.of())) {
				ranks.merge(child, rank + 1, Math::max);
				if (pending.merge(child, -1, Integer::sum) == 0)
					queue.add(child);
			}
		}
		if (visited != quests.size())
			throw new IllegalArgumentException("Quest dependencies contain a cycle");

		Map<String, Point> result = new LinkedHashMap<>();
		Map<Cell, List<String>> cells = new HashMap<>();
		// Custom positions remain exact. Authors may deliberately overlap them.
		quests.values().forEach(q -> {
			if (q.layout() != null) {
				Point point = new Point(q.layout().x(), q.layout().y());
				if (!Double.isFinite(point.x()) || !Double.isFinite(point.y()))
					throw new IllegalArgumentException("Invalid layout: " + q.id());
				result.put(q.id(), point);
				add(cells, q.id(), point);
			}
		});
		Map<String, Double> order = new HashMap<>();
		for (var layer : layers.entrySet()) {
			List<String> ids = layer.getValue();
			ids.sort(Comparator.<String>comparingDouble(id -> {
				var refs = parents.get(id);
				return refs.isEmpty() ? 0
						: refs.stream().mapToDouble(ref -> order.getOrDefault(ref, 0.0)).average().orElse(0);
			}).thenComparing(Comparator.naturalOrder()));
			for (int i = 0; i < ids.size(); i++)
				order.put(ids.get(i), (double) i);
			for (int i = 0; i < ids.size(); i++) {
				String id = ids.get(i);
				if (result.containsKey(id))
					continue;
				double x = (i - (ids.size() - 1) / 2.0) * HORIZONTAL_GAP, y = layer.getKey() * VERTICAL_GAP;
				Point point = new Point(x, y);
				// Move right to the first free grid slot. Explicit coordinates always win.
				while (overlaps(cells, result, point))
					point = new Point(point.x() + HORIZONTAL_GAP, y);
				result.put(id, point);
				add(cells, id, point);
			}
		}
		return new QuestLayout(result, edges, cells);
	}

	private static long cell(double value) {
		return (long) Math.floor(value / HORIZONTAL_GAP);
	}

	private static void add(Map<Cell, List<String>> cells, String id, Point point) {
		cells.computeIfAbsent(new Cell(cell(point.x()), cell(point.y())), ignored -> new ArrayList<>()).add(id);
	}

	private static boolean overlaps(Map<Cell, List<String>> cells, Map<String, Point> points, Point point) {
		long cx = cell(point.x()), cy = cell(point.y());
		for (long dx = -1; dx <= 1; dx++)
			for (long dy = -1; dy <= 1; dy++) {
				if ((dx < 0 && cx == Long.MIN_VALUE) || (dx > 0 && cx == Long.MAX_VALUE)
						|| (dy < 0 && cy == Long.MIN_VALUE) || (dy > 0 && cy == Long.MAX_VALUE))
					continue;
				for (String id : cells.getOrDefault(new Cell(cx + dx, cy + dy), List.of())) {
					Point other = points.get(id);
					if (Math.abs(other.x() - point.x()) < NODE_SIZE && Math.abs(other.y() - point.y()) < NODE_SIZE)
						return true;
				}
			}
		return false;
	}

	/**
	 * Returns nodes intersecting a world-coordinate rectangle, in stable insertion
	 * order.
	 */
	public List<String> visible(double left, double top, double right, double bottom) {
		if (!Double.isFinite(left) || !Double.isFinite(top) || !Double.isFinite(right) || !Double.isFinite(bottom)
				|| left > right || top > bottom)
			return List.of();
		long minX = cell(left - NODE_SIZE / 2), maxX = cell(right + NODE_SIZE / 2);
		long minY = cell(top - NODE_SIZE / 2), maxY = cell(bottom + NODE_SIZE / 2);
		Set<String> candidates = new HashSet<>();
		double area = ((double) maxX - minX + 1) * ((double) maxY - minY + 1);
		if (area > Math.max(64, cells.size() * 4.0))
			candidates.addAll(points.keySet());
		else
			for (long x = minX;; x++) {
				for (long y = minY;; y++) {
					candidates.addAll(cells.getOrDefault(new Cell(x, y), List.of()));
					if (y == maxY)
						break;
				}
				if (x == maxX)
					break;
			}
		return candidates.stream().filter(id -> {
			Point p = points.get(id);
			return p.x() + NODE_SIZE / 2 >= left && p.x() - NODE_SIZE / 2 <= right && p.y() + NODE_SIZE / 2 >= top
					&& p.y() - NODE_SIZE / 2 <= bottom;
		}).sorted().toList();
	}

	public Optional<String> hit(double x, double y) {
		return visible(x, y, x, y).stream().findFirst();
	}
}
