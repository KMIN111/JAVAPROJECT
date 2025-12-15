package chat.server;

import chat.shared.Message;
import chat.shared.Message.WordItem;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 4인용 단어 맞추기 게임 상태와 라운드를 관리한다.
 * 서버가 단어 풀을 배포하고 점수를 계산해 STATE 메시지로 브로드캐스트한다.
 */
public class WordGame {

    private static final int MIN_WORDS = 25;
    private static final int MAX_WORDS = 30;
    private static final int ROUND_SECONDS = 30;
    private static final int BOARD_WIDTH = 520;
    private static final int BOARD_HEIGHT = 520;
    private static final int PADDING = 24;
    private static final int MAX_PLAYERS = 4;

    private final ChatRoom room;
    private final Set<String> players = new LinkedHashSet<>();
    private final Set<String> spectators = new HashSet<>();
    private final Map<String, Integer> scores = new LinkedHashMap<>();
    private final Map<String, WordEntry> activeWords = new LinkedHashMap<>();
    private final Random random = new Random();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "wordgame-timer"));
    private ScheduledFuture<?> roundFuture;
    private ScheduledFuture<?> tickFuture;
    private long roundEndMillis;
    private boolean running = false;
    private String lastWinner;
    private String infoText = "대기 중";
    private String hostPlayer;

    private List<String> wordPool = null;

    public WordGame(ChatRoom room) {
        this.room = room;
    }

    public synchronized void joinAsPlayer(String nickname) {
        if (players.contains(nickname)) return;
        if (players.size() >= MAX_PLAYERS) {
            spectators.add(nickname);
            return;
        }
        players.add(nickname);
        scores.putIfAbsent(nickname, 0);
        spectators.remove(nickname);
        if (hostPlayer == null) {
            hostPlayer = nickname; // 첫 입장자 방장
        }
        // 게임은 방장이 명시적으로 시작 버튼을 눌렀을 때만 시작
        broadcastState();
    }

    public synchronized void joinAsSpectator(String nickname) {
        if (players.contains(nickname)) return;
        spectators.add(nickname);
        broadcastState();
    }

    public synchronized void startRound(String requester) {
        if (!players.contains(requester)) {
            throw new IllegalStateException("플레이어만 라운드를 시작할 수 있습니다.");
        }
        if (hostPlayer != null && !hostPlayer.equals(requester)) {
            throw new IllegalStateException("방장만 시작할 수 있습니다.");
        }
        startRoundInternal(requester + " 님이 라운드를 시작했습니다.");
    }

    private void startRoundInternal(String reason) {
        cancelTimer();
        ensureWordPool();

        activeWords.clear();
        scores.replaceAll((k, v) -> 0); // 새 라운드는 점수 초기화

        List<String> copy = new ArrayList<>(wordPool);
        Collections.shuffle(copy, random);
        int count = MIN_WORDS + random.nextInt(MAX_WORDS - MIN_WORDS + 1);
        for (int i = 0; i < Math.min(count, copy.size()); i++) {
            String w = copy.get(i);
            WordEntry entry = new WordEntry(w,
                    PADDING + random.nextInt(Math.max(BOARD_WIDTH - PADDING * 2, 1)),
                    PADDING + random.nextInt(Math.max(BOARD_HEIGHT - PADDING * 2, 1)));
            activeWords.put(w.toLowerCase(), entry);
        }

        running = true;
        lastWinner = null;
        infoText = reason;
        roundEndMillis = Instant.now().toEpochMilli() + ROUND_SECONDS * 1000L;
        roundFuture = scheduler.schedule(this::finishRoundByTime, ROUND_SECONDS, TimeUnit.SECONDS);
        tickFuture = scheduler.scheduleAtFixedRate(this::tickBroadcast, 1, 1, TimeUnit.SECONDS);

        broadcastState();
    }

    public synchronized void submitWord(String nickname, String word) {
        if (!running) {
            throw new IllegalStateException("라운드가 진행 중이 아닙니다.");
        }
        if (!players.contains(nickname)) {
            throw new IllegalStateException("플레이어만 단어를 입력할 수 있습니다.");
        }
        if (word == null || word.isBlank()) {
            throw new IllegalStateException("단어를 입력해주세요.");
        }

        String key = word.trim().toLowerCase();
        WordEntry entry = activeWords.get(key);
        if (entry == null) {
            throw new IllegalStateException("해당 단어가 목록에 없습니다.");
        }
        if (entry.answeredBy != null) {
            throw new IllegalStateException("이미 맞춘 단어입니다.");
        }

        entry.answeredBy = nickname;
        scores.put(nickname, scores.getOrDefault(nickname, 0) + 10);

        boolean allDone = activeWords.values().stream().allMatch(e -> e.answeredBy != null);
        if (allDone) {
            finishRound("모든 단어를 맞췄습니다.");
        } else {
            broadcastState();
        }
    }

    public synchronized void onUserLeft(String nickname) {
        boolean removed = players.remove(nickname);
        spectators.remove(nickname);
        if (removed) {
            scores.remove(nickname);
            if (nickname.equals(hostPlayer)) {
                hostPlayer = players.stream().findFirst().orElse(null);
            }
            if (players.isEmpty()) {
                stopAndReset();
            } else {
                broadcastState();
            }
        } else {
            broadcastState();
        }
    }

    private void finishRoundByTime() {
        synchronized (this) {
            finishRound("시간 종료");
        }
    }

    private void finishRound(String reason) {
        running = false;
        cancelTimer();
        int max = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> winners = new ArrayList<>();
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            if (e.getValue() == max && max > 0) {
                winners.add(e.getKey());
            }
        }
        if (winners.isEmpty()) {
            lastWinner = null;
            infoText = reason + " - 승자 없음";
        } else {
            lastWinner = String.join(", ", winners);
            infoText = reason + " - 최고점: " + max;
        }
        broadcastState();
    }

    private void stopAndReset() {
        cancelTimer();
        running = false;
        activeWords.clear();
        scores.clear();
        infoText = "대기 중";
        lastWinner = null;
        hostPlayer = null;
        broadcastState();
    }

    private void cancelTimer() {
        if (roundFuture != null) {
            roundFuture.cancel(true);
            roundFuture = null;
        }
        if (tickFuture != null) {
            tickFuture.cancel(true);
            tickFuture = null;
        }
    }

    private void ensureWordPool() {
        if (wordPool != null && !wordPool.isEmpty()) return;
        List<String> loaded = new ArrayList<>();
        try (InputStream is = WordGame.class.getClassLoader().getResourceAsStream("words.txt")) {
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String t = line.trim();
                        if (!t.isEmpty()) loaded.add(t);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (loaded.isEmpty()) {
            Collections.addAll(loaded,
                    "apple","banana","orange","grape","melon",
                    "keyboard","monitor","mouse","window","coffee",
                    "planet","rocket","galaxy","river","mountain",
                    "pencil","notebook","school","garden","street",
                    "music","guitar","piano","drum","violin",
                    "spring","summer","autumn","winter","cloud",
                    "rain","snow","thunder","light","shadow");
        }
        this.wordPool = loaded;
    }

    private int remainingSeconds() {
        if (!running) return 0;
        long now = System.currentTimeMillis();
        long remain = roundEndMillis - now;
        if (remain < 0) return 0;
        return (int) Math.ceil(remain / 1000.0);
    }

    private void broadcastState() {
        room.broadcast(toStateMessage(), false);
    }

    private void tickBroadcast() {
        synchronized (this) {
            if (!running) {
                cancelTimer();
                return;
            }
            broadcastState();
        }
    }

    // 게임만 나가기 (방 유지)
    public synchronized void leaveGame(String nickname) {
        boolean removed = players.remove(nickname);
        spectators.remove(nickname);
        scores.remove(nickname);
        if (removed) {
            if (nickname.equals(hostPlayer)) {
                hostPlayer = players.stream().findFirst().orElse(null);
            }
            if (players.isEmpty()) {
                stopAndReset();
                return;
            }
        }
        broadcastState();
    }

    public synchronized Message toStateMessage() {
        List<WordItem> items = new ArrayList<>();
        for (WordEntry e : activeWords.values()) {
            items.add(new WordItem(e.text, e.x, e.y, e.answeredBy));
        }
        List<String> playerList = new ArrayList<>(players);
        List<String> spectatorList = new ArrayList<>(spectators);
        Map<String, Integer> scoresCopy = new LinkedHashMap<>();
        for (String p : players) {
            scoresCopy.put(p, scores.getOrDefault(p, 0));
        }
        return Message.wordState(
                room.getName(),
                items,
                playerList,
                spectatorList,
                scoresCopy,
                running,
                remainingSeconds(),
                lastWinner,
                infoText,
                hostPlayer
        );
    }

    private static class WordEntry {
        final String text;
        final int x;
        final int y;
        String answeredBy;

        WordEntry(String text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }
}

