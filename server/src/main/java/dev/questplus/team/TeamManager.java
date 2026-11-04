package dev.questplus.team;

import dev.questplus.core.*;
import dev.questplus.persistence.JsonProgressStore;
import java.io.IOException;
import java.util.*;

/** Called only on the server thread. Changes are detached until their transaction is durable. */
public final class TeamManager {
    public record Change(Map<UUID,PlayerProgress> players,Map<UUID,TeamData> teams) {
        public Change { players = Map.copyOf(players); teams = Map.copyOf(teams); }
        public Set<UUID> affectedPlayers() { return players.keySet(); }
    }
    private final JsonProgressStore store;
    private final Map<UUID,PlayerProgress> playerCache;
    private final Map<UUID,TeamData> teams;
    private QuestRegistry registry;

    public TeamManager(JsonProgressStore store,QuestRegistry registry,Map<UUID,PlayerProgress> playerCache) throws IOException {
        this.store = Objects.requireNonNull(store); this.registry = Objects.requireNonNull(registry);
        this.playerCache = Objects.requireNonNull(playerCache); teams = new HashMap<>(store.loadTeams());
        Map<UUID,UUID> memberships = new HashMap<>();
        for (TeamData team : teams.values()) if (!team.disbanded) for (UUID member : team.members) {
            if (memberships.putIfAbsent(member,team.id) != null) throw new IOException("Player belongs to multiple teams: " + member);
            if (!team.id.equals(personal(member).teamId)) throw new IOException("Team/player membership mismatch for " + member);
        }
    }
    public void registry(QuestRegistry registry) { this.registry = Objects.requireNonNull(registry); }
    public TeamData get(UUID id) {
        TeamData team = teams.get(id);
        if (team == null || team.disbanded) throw new IllegalArgumentException("Unknown/disbanded team: " + id);
        return team;
    }
    public Optional<TeamData> teamOf(UUID player) throws IOException {
        UUID id = personal(player).teamId;
        if (id == null) return Optional.empty();
        TeamData team = get(id);
        if (!team.members.contains(player)) throw new IOException("Team/player membership mismatch for " + player);
        return Optional.of(team);
    }
    public Collection<TeamData> allTeams() { return teams.values().stream().filter(t -> !t.disbanded).toList(); }
    /** Read-only view of valid invitations for one player. Expired invitations are never advertised. */
    public List<TeamData> invitationsFor(UUID player,long now) {
        Objects.requireNonNull(player);
        checkTime(now);
        return teams.values().stream()
            .filter(t -> !t.disbanded && t.invitations.getOrDefault(player,0L) > now)
            .sorted(Comparator.comparing(t -> t.id.toString()))
            .toList();
    }
    public void apply(Change change) {
        change.players().values().forEach(PlayerProgress::validate); change.teams().values().forEach(TeamData::validate);
        playerCache.putAll(change.players()); teams.putAll(change.teams());
    }
    public Change create(UUID actor,String name) throws IOException {
        unteamed(actor);
        if (name == null || name.isBlank() || name.length() > 64) throw new IllegalArgumentException("Team name must contain 1..64 characters");
        TeamData team = new TeamData(); team.id = UUID.randomUUID(); team.owner = actor; team.name = name; team.members.add(actor);
        team.progress = collective(personal(actor));
        PlayerProgress player = copy(personal(actor)); player.teamId = team.id;
        return change(Map.of(actor,player),team);
    }
    public Change invite(UUID owner,UUID target,long now) throws IOException {
        TeamData team = copy(owned(owner)); unteamed(target); checkTime(now);
        team.invitations.entrySet().removeIf(e -> e.getValue() <= now);
        if (team.members.size() >= 64) throw new IllegalArgumentException("Team member limit reached");
        if (team.invitations.containsKey(target)) throw new IllegalArgumentException("Player is already invited");
        if (team.invitations.size() >= 64) throw new IllegalArgumentException("Team invitation limit reached");
        team.invitations.put(target,Math.addExact(now,300_000L));
        return change(Map.of(),team);
    }
    public Change join(UUID actor,UUID teamId,long now) throws IOException {
        unteamed(actor); checkTime(now); TeamData team = copy(get(teamId));
        Long expiry = team.invitations.get(actor);
        if (expiry == null || expiry <= now) throw new IllegalArgumentException("Invitation missing or expired");
        if (team.members.size() >= 64) throw new IllegalArgumentException("Team member limit reached");
        PlayerProgress player = copy(personal(actor));
        merge(team.progress,player); team.members.add(actor); team.invitations.remove(actor); player.teamId = team.id;
        return change(Map.of(actor,player),team);
    }
    public Change decline(UUID actor,UUID teamId) {
        TeamData team = copy(get(teamId));
        if (team.invitations.remove(actor) == null) throw new IllegalArgumentException("Invitation missing");
        return change(Map.of(),team);
    }
    public Change leave(UUID actor) throws IOException {
        TeamData team = copy(requiredTeam(actor));
        if (team.owner.equals(actor)) throw new IllegalArgumentException("Transfer ownership or disband before leaving");
        return separate(team,actor);
    }
    public Change kick(UUID owner,UUID target) throws IOException {
        TeamData team = copy(owned(owner));
        if (owner.equals(target)) throw new IllegalArgumentException("Owner cannot kick themselves");
        if (!team.members.contains(target)) throw new IllegalArgumentException("Player is not a team member");
        return separate(team,target);
    }
    public Change transfer(UUID owner,UUID target) throws IOException {
        TeamData team = copy(owned(owner));
        if (owner.equals(target) || !team.members.contains(target)) throw new IllegalArgumentException("Choose another team member as owner");
        team.owner = target; return change(Map.of(),team);
    }
    public Change disband(UUID owner) throws IOException {
        TeamData team = copy(owned(owner)); Map<UUID,PlayerProgress> players = new HashMap<>();
        for (UUID member : team.members) players.put(member,separated(team,personal(member)));
        team.members.clear(); team.invitations.clear(); team.disbanded = true;
        return change(players,team);
    }
    private Change separate(TeamData team,UUID actor) throws IOException {
        PlayerProgress player = separated(team,personal(actor)); team.members.remove(actor);
        return change(Map.of(actor,player),team);
    }
    private static PlayerProgress separated(TeamData team,PlayerProgress personal) {
        PlayerProgress copy = copy(personal); copy.quests = copy(team.progress).quests; copy.teamId = null;
        return copy;
    }
    private static PlayerProgress collective(PlayerProgress personal) {
        PlayerProgress progress = copy(personal); progress.claims.clear(); progress.entitlements.clear(); progress.legacyUnclaimed.clear(); progress.teamId = null;
        return progress;
    }
    private PlayerProgress personal(UUID player) throws IOException {
        PlayerProgress state = playerCache.get(player);
        if (state == null) { state = store.load(player); playerCache.put(player,state); }
        return state;
    }
    private void unteamed(UUID actor) throws IOException {
        if (personal(actor).teamId != null) throw new IllegalArgumentException("Player already belongs to a team");
    }
    private TeamData requiredTeam(UUID actor) throws IOException { return teamOf(actor).orElseThrow(() -> new IllegalArgumentException("Player has no team")); }
    private TeamData owned(UUID actor) throws IOException {
        TeamData team = requiredTeam(actor);
        if (!team.owner.equals(actor)) throw new IllegalArgumentException("Only the team owner may do this");
        return team;
    }
    private static void checkTime(long now) { if (now < 0 || now > Long.MAX_VALUE - 300_000L) throw new IllegalArgumentException("Invalid server time"); }
    private static Change change(Map<UUID,PlayerProgress> players,TeamData team) {
        team.validate(); players.values().forEach(PlayerProgress::validate); return new Change(players,Map.of(team.id,team));
    }
    private static PlayerProgress copy(PlayerProgress progress) { return JsonProgressStore.detachedCopy(progress); }
    private static TeamData copy(TeamData source) {
        TeamData team = new TeamData(); team.id = source.id; team.name = source.name; team.owner = source.owner;
        team.members.addAll(source.members); team.invitations.putAll(source.invitations); team.progress = copy(source.progress); team.disbanded = source.disbanded;
        return team;
    }
    private void merge(PlayerProgress destination,PlayerProgress source) {
        for (var entry : source.quests.entrySet()) {
            String id = entry.getKey(); var incoming = entry.getValue(); var existing = destination.quests.get(id);
            QuestDefinition definition = registry.find(id).orElse(null);
            if (existing == null) {
                PlayerProgress temporary = new PlayerProgress(); temporary.quests.put(id,incoming);
                var selected = copy(temporary).quests.get(id);
                if (definition != null && !incoming.terminal && !(definition.progression().equals("once") && incoming.completed) && complex(definition)) {
                    selected.counters.clear(); selected.tier = 0; selected.attemptId = null; selected.startedAt = 0; selected.waitingRestart = false;
                }
                destination.quests.put(id,selected); continue;
            }
            if (!Objects.equals(existing.fingerprint,incoming.fingerprint))
                throw new IllegalArgumentException("Cannot merge incompatible quest progress: " + id + "; progress retained");
            incoming.cumulative.forEach((key,value) -> existing.cumulative.merge(key,value,Math::max));
            existing.cycles = Math.max(existing.cycles,incoming.cycles); existing.tier = Math.max(existing.tier,incoming.tier);
            existing.reachedTiers.addAll(incoming.reachedTiers); existing.completed |= incoming.completed; existing.terminal |= incoming.terminal;
            if (definition == null || !complex(definition)) {
                incoming.counters.forEach((key,value) -> existing.counters.merge(key,value,Math::max));
                if (definition != null && definition.progression().equals("once") && QuestEngine.isSatisfied(definition,existing)) {
                    existing.completed = true; existing.terminal = true;
                }
            }
        }
    }
    private static boolean complex(QuestDefinition definition) {
        return !definition.progression().equals("once") || definition.objectiveMode().equals("sequence") || definition.timeLimitSeconds() > 0 || definition.noDeath();
    }
}
