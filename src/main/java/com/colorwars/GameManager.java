package com.colorwars;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/**
 * 게임의 모든 상태(색 순환, 우두머리-부하 관계, 체력 규칙 등)를 관리한다.
 *
 * 용어 정리:
 *  - "리더(leader)"   : 아직 아무에게도 정복당하지 않은, 자기 색을 대표하는 플레이어
 *  - "부하(minion)"    : 다른 플레이어에게 죽어서 그 사람의 팀이 된 플레이어
 *  - "팀(leaderId)"    : 부하는 항상 어떤 리더에게 소속되며, 그 리더의 색을 그대로 사용한다.
 *
 * 규칙 요약:
 *  - 리더 A는 색 순환에서 "다음 리더"의 팀 전체(리더+부하)만 공격할 수 있다.
 *  - 리더 A는 자기 자신의 부하도 공격할 수 있다 (부하가 리더를 공격하는 것은 불가).
 *  - 리더가 정복당하면 그 리더가 데리고 있던 부하는 전부 새 정복자의 팀으로 승계된다.
 *  - 부하가 남을 죽여도, 그 전리품(새 부하)은 부하 본인이 아니라 "최종 우두머리(리더)"의 팀이 된다.
 */
public class GameManager {

    private final ColorWarsPlugin plugin;

    // 게임 진행 여부
    private boolean running = false;

    // 색 순환 순서 (리더 UUID만 들어있음, 인덱스 순서가 곧 색 순환 순서)
    private final List<UUID> leaderOrder = new ArrayList<>();

    // 리더 UUID -> 배정된 색
    private final Map<UUID, ChatColor> colorOf = new HashMap<>();

    // 부하 UUID -> 소속 리더 UUID (리더는 이 맵에 들어가지 않음)
    private final Map<UUID, UUID> ownerOf = new HashMap<>();

    // 리더 UUID -> 그 리더가 보유한 부하 UUID 집합
    private final Map<UUID, Set<UUID>> minionsOf = new HashMap<>();

    // 외부 요인 사망 후 "그 자리에서 행동불가" 상태 관리
    private final Map<UUID, Long> freezeUntilMillis = new HashMap<>();
    private final Map<UUID, org.bukkit.Location> freezeLocation = new HashMap<>();

    // 등록 대기열
    private final Set<UUID> registered = new LinkedHashSet<>();

    private static final ChatColor[] PALETTE = new ChatColor[]{
            ChatColor.RED, ChatColor.AQUA, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.LIGHT_PURPLE, ChatColor.GOLD, ChatColor.BLUE, ChatColor.WHITE,
            ChatColor.DARK_GREEN, ChatColor.DARK_AQUA, ChatColor.DARK_PURPLE, ChatColor.GRAY
    };

    // 게임 시작시 플레이어가 흩어져서 스폰될 x/z 범위 (-3000 ~ 3000)
    private static final int SPAWN_COORD_RANGE = 3000;
    private final Random random = new Random();

    public GameManager(ColorWarsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------
    // 등록 / 시작 / 종료
    // ---------------------------------------------------------------

    public boolean register(Player p) {
        if (running) return false;
        return registered.add(p.getUniqueId());
    }

    public boolean isRunning() {
        return running;
    }

    public String start() {
        if (running) return "이미 게임이 진행 중입니다.";
        List<UUID> players = new ArrayList<>(registered);
        if (players.size() < 2) return "최소 2명 이상 등록해야 시작할 수 있습니다. (현재 " + players.size() + "명)";
        if (players.size() > PALETTE.length) return "색상 팔레트가 부족합니다 (최대 " + PALETTE.length + "명).";

        Collections.shuffle(players);

        leaderOrder.clear();
        colorOf.clear();
        ownerOf.clear();
        minionsOf.clear();
        freezeUntilMillis.clear();
        freezeLocation.clear();

        for (int i = 0; i < players.size(); i++) {
            UUID id = players.get(i);
            leaderOrder.add(id);
            colorOf.put(id, PALETTE[i]);
            minionsOf.put(id, new HashSet<>());

            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setHealth(20.0);
                setMaxHealth(p, 20.0);
                updateDisplay(p);
                teleportRandomGround(p);
            }
        }

        running = true;
        registered.clear();
        return null; // null == 성공
    }

    public void stop() {
        running = false;
        for (UUID id : new HashSet<>(colorOf.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                setMaxHealth(p, 20.0);
                clearTeam(p);
            }
        }
        for (UUID id : new HashSet<>(ownerOf.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                setMaxHealth(p, 20.0);
                clearTeam(p);
            }
        }
        leaderOrder.clear();
        colorOf.clear();
        ownerOf.clear();
        minionsOf.clear();
        freezeUntilMillis.clear();
        freezeLocation.clear();
    }

    /**
     * x,z를 -3000~3000 범위에서 무작위로 뽑고, 그 지점의 가장 높은 블록 바로 위(지상)로
     * 순간이동시킨다. 동굴/바다 속이 아니라 항상 지표면에 착지하도록 getHighestBlockYAt 사용.
     */
    private void teleportRandomGround(Player p) {
        World world = Bukkit.getWorlds().get(0); // 서버 기본(메인) 월드 기준
        int x = random.nextInt(SPAWN_COORD_RANGE * 2 + 1) - SPAWN_COORD_RANGE;
        int z = random.nextInt(SPAWN_COORD_RANGE * 2 + 1) - SPAWN_COORD_RANGE;
        int y = world.getHighestBlockYAt(x, z) + 1;
        Location loc = new Location(world, x + 0.5, y, z + 0.5);
        p.teleport(loc);
    }

    // ---------------------------------------------------------------
    // 조회 헬퍼
    // ---------------------------------------------------------------

    public boolean isParticipant(UUID id) {
        return colorOf.containsKey(id) || ownerOf.containsKey(id);
    }

    public boolean isLeader(UUID id) {
        return colorOf.containsKey(id);
    }

    public boolean isMinion(UUID id) {
        return ownerOf.containsKey(id);
    }

    /** 이 플레이어가 속한 팀의 리더(우두머리) UUID. 본인이 리더면 본인 UUID. */
    public UUID getLeader(UUID id) {
        if (colorOf.containsKey(id)) return id;
        return ownerOf.get(id);
    }

    public ChatColor getColor(UUID id) {
        UUID leader = getLeader(id);
        return leader == null ? null : colorOf.get(leader);
    }

    public UUID getOwner(UUID minionId) {
        return ownerOf.get(minionId);
    }

    private UUID nextLeader(UUID leader) {
        int idx = leaderOrder.indexOf(leader);
        if (idx == -1 || leaderOrder.size() < 2) return null;
        return leaderOrder.get((idx + 1) % leaderOrder.size());
    }

    /** 화살표가 가리켜야 할 "다음 색" 팀 대표 플레이어(리더)를 반환. 접속 중인 사람만. */
    public Player getNextColorTargetLeader(UUID selfId) {
        UUID myLeader = getLeader(selfId);
        if (myLeader == null) return null;
        UUID next = nextLeader(myLeader);
        if (next == null) return null;
        return Bukkit.getPlayer(next);
    }

    /**
     * attacker가 victim을 공격할 수 있는지 여부.
     * - 같은 팀: 오직 attacker가 "리더" 본인일 때만 자기 부하를 때릴 수 있음.
     * - 다른 팀: attacker 팀이 victim 팀의 "이전 팀"(즉 victim 팀 == attacker의 다음 팀)일 때만 가능.
     */
    public boolean canAttack(UUID attackerId, UUID victimId) {
        if (!isParticipant(attackerId) || !isParticipant(victimId)) return true; // 게임 참가자가 아니면 규칙 미적용
        UUID leaderA = getLeader(attackerId);
        UUID leaderV = getLeader(victimId);
        if (leaderA.equals(leaderV)) {
            return isLeader(attackerId); // 리더만 자기 부하를 때릴 수 있음
        }
        UUID next = nextLeader(leaderA);
        return next != null && next.equals(leaderV);
    }

    // ---------------------------------------------------------------
    // 정복(부하화)
    // ---------------------------------------------------------------

    /**
     * victim이 killer에게 사망 -> victim은 killer가 속한 팀의 "리더"의 부하가 된다.
     * victim이 원래 리더였다면, victim이 데리고 있던 부하들도 전부 승계된다.
     * @return 게임 종료(승자 확정) 시 승자 UUID, 아니면 null
     */
    public UUID convertToMinion(UUID victimId, UUID killerId) {
        UUID newLeader = getLeader(killerId);
        if (newLeader == null) return null;

        // 자기 팀을 다시 자기 팀에 편입하는 경우는 무시 (이론상 발생 안 하지만 방어코드)
        if (newLeader.equals(victimId)) return null;

        if (isLeader(victimId)) {
            // victim이 데리고 있던 부하 전원을 newLeader에게 승계
            Set<UUID> subs = minionsOf.remove(victimId);
            if (subs != null) {
                for (UUID m : subs) {
                    ownerOf.put(m, newLeader);
                    minionsOf.get(newLeader).add(m);
                }
            }
            colorOf.remove(victimId);
            leaderOrder.remove(victimId);
        } else {
            UUID oldLeader = ownerOf.get(victimId);
            if (oldLeader != null) {
                Set<UUID> set = minionsOf.get(oldLeader);
                if (set != null) set.remove(victimId);
            }
        }

        ownerOf.put(victimId, newLeader);
        minionsOf.computeIfAbsent(newLeader, k -> new HashSet<>()).add(victimId);

        Player victim = Bukkit.getPlayer(victimId);
        if (victim != null) {
            setMaxHealth(victim, 10.0); // 항상 "기존 체력(20)의 절반" 기준으로 고정 (누적 반토막 방지)
            updateDisplay(victim);
        }
        for (UUID id : minionsOf.get(newLeader)) {
            // 승계된 부하들도 새 팀 색으로 표시 갱신
            Player p = Bukkit.getPlayer(id);
            if (p != null) updateDisplay(p);
        }

        if (leaderOrder.size() == 1) {
            running = false;
            return leaderOrder.get(0);
        }
        return null;
    }

    // ---------------------------------------------------------------
    // 외부요인 사망 -> 행동불가 처리
    // ---------------------------------------------------------------

    public void markFrozen(UUID id, org.bukkit.Location loc, long durationMillis) {
        freezeLocation.put(id, loc);
        freezeUntilMillis.put(id, System.currentTimeMillis() + durationMillis);
    }

    public org.bukkit.Location getFreezeLocation(UUID id) {
        return freezeLocation.get(id);
    }

    public boolean isFrozen(UUID id) {
        Long until = freezeUntilMillis.get(id);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            freezeUntilMillis.remove(id);
            freezeLocation.remove(id);
            return false;
        }
        return true;
    }

    public long getFreezeRemainingSeconds(UUID id) {
        Long until = freezeUntilMillis.get(id);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000L + 1);
    }

    // ---------------------------------------------------------------
    // 표시(팀 색상) / 유틸
    // ---------------------------------------------------------------

    public void updateDisplay(Player p) {
        ChatColor color = getColor(p.getUniqueId());
        if (color == null) return;
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "cw_" + getLeader(p.getUniqueId()).toString().substring(0, 8);
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
            team.setColor(color);
        }
        team.addEntry(p.getName());
    }

    private void clearTeam(Player p) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = board.getEntryTeam(p.getName());
        if (t != null) t.removeEntry(p.getName());
    }

    private void setMaxHealth(Player p, double value) {
        var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(value);
            if (p.getHealth() > value) p.setHealth(value);
        }
    }

    public String colorName(ChatColor c) {
        return switch (c) {
            case RED -> "빨강";
            case AQUA -> "하늘색";
            case GREEN -> "초록";
            case YELLOW -> "노랑";
            case LIGHT_PURPLE -> "분홍";
            case GOLD -> "금색";
            case BLUE -> "파랑";
            case WHITE -> "흰색";
            case DARK_GREEN -> "진초록";
            case DARK_AQUA -> "청록";
            case DARK_PURPLE -> "보라";
            case GRAY -> "회색";
            default -> c.name();
        };
    }

    public List<UUID> getLeaderOrder() {
        return Collections.unmodifiableList(leaderOrder);
    }

    public Map<UUID, ChatColor> getColorMap() {
        return Collections.unmodifiableMap(colorOf);
    }

    public Map<UUID, Set<UUID>> getMinionsMap() {
        return Collections.unmodifiableMap(minionsOf);
    }

    public ColorWarsPlugin getPlugin() {
        return plugin;
    }
}
