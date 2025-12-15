package chat.shared;

import javax.swing.*;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class Message implements Serializable {

    public enum Type {
        LOGIN,       // 클라이언트 → 서버 (닉네임 전달)
        ROOM_LIST,   // 서버 → 클라이언트 (방 목록 전체)
        CREATE_ROOM, // 클라이언트 → 서버 (방 만들기 요청)
        JOIN_ROOM,   // 클라이언트 → 서버 (방 입장 요청)
        CHAT,        // 양방향 (채팅 메시지)
        SYSTEM,      // 서버 → 클라이언트 (공지, 안내 등)
        ERROR,       // 서버 → 클라이언트 (에러 안내)
        GAME_EVENT,  // 미니게임(오목) 이벤트
        USER_LIST,   // 방 참가자 목록
        IMAGE,       // 이미지 전달
        LEAVE_ROOM   // 클라이언트 ->  서버?
    }

    public enum GameAction {
        REQUEST_JOIN,       // 통합된 참여 요청 (자동 플레이어/관전 결정)
        REQUEST_JOIN_PLAYER, // 게임 플레이어 참여 요청
        STATE, // 게임 상태 동기화
        MOVE, // 게임 이동
        RESULT, // 게임 결과
        RESIGN, // 게임 기권
        SUBMIT_WORD, // 게임 단어 제출
        START_WORD_ROUND, // 게임 라운드 시작
        LEAVE_GAME, // 게임만 나가기 (방 유지)
        ERROR // 게임 오류
    }

    public enum GameType {
        OMOK,
        WORD
    }

    private Type type;
    private String room;    // 어느 방에 속한 건지
    private String sender;  // 보낸 사람 닉네임
    private String text;       // 메시지 내용
    private List<String> rooms; // ROOM_LIST 용
    private List<String> users; //USER_LIST용
    private ImageIcon image;   // 이미지 전달

    // 오목 게임 관련필드드
    private GameAction gameAction; // 게임 액션
    private GameType gameType; // 게임 타입
    private int x; // 바둑알 이동 x좌표
    private int y; // 바둑알 이동 y좌표
    private int[][] board; // 바둑판
    private String blackPlayer; // 흑 플레이어 닉네임
    private String whitePlayer; // 백 플레이어 닉네임
    private String currentTurn; // 현재 턴
    private boolean finished; // 게임 종료 여부
    private String winner; // 게임 승자
    private String resultReason; // 게임 결과 (승리리, 무승부, 기권권)
    private List<String> spectators; // 게임 관전자 목록

    // 단어 맞추기 게임필드드
    private List<WordItem> wordItems; // 단어 맞추기 게임 단어목록
    private List<String> players; // 단어 맞추기 게임 플레이어 목록
    private Map<String, Integer> scores; // 단어 맞추기 게임 점수
    private int remainingSeconds; // 단어 맞추기 게임 남은 시간
    private boolean running; // 단어 맞추기 게임 실행 여부
    private String infoText; // 단어 맞추기 게임 정보 텍스트
    private String hostPlayer; // 단어 맞추기 게임 호스트 플레이어 닉네임

    public Message(Type type) {
        this.type = type;
    }

    //채팅 룸 관련 생성자들

    // 로그인 생성자
    public static Message login(String nickname) {
        Message m = new Message(Type.LOGIN);
        m.sender = nickname;
        return m;
    }

    // 방 생성 생성자
    public static Message createRoom(String roomName) {
        Message m = new Message(Type.CREATE_ROOM);
        m.room = roomName;
        return m;
    }

    // 방 입장 생성자
    public static Message joinRoom(String roomName) {
        Message m = new Message(Type.JOIN_ROOM);
        m.room = roomName;
        return m;
    }
    // 방 퇴장 생성자
    public static Message leaveRoom(String roomName, String name) {
        Message m = new Message(Type.LEAVE_ROOM);
        m.sender = name;
        m.room = roomName;
        return m;
    }

    // 채팅 생성자
    public static Message chat(String room, String sender, String text) {
        Message m = new Message(Type.CHAT);
        m.room = room;
        m.sender = sender;
        m.text = text;
        return m;
    }

    // 방 목록 생성자
    public static Message roomList(List<String> rooms) {
        Message m = new Message(Type.ROOM_LIST);
        m.rooms = rooms;
        return m;
    }

    // 채팅방 시스템 메시지 생성자
    public static Message system(String text) {
        Message m = new Message(Type.SYSTEM);
        m.text = text;
        return m;
    }

    // 채팅방 오류 메시지 생성자
    public static Message error(String text) {
        Message m = new Message(Type.ERROR);
        m.text = text;
        return m;
    }

    // 방 시스템 메시지 생성자
    public static Message systemForRoom(String room, String text) {
        Message m = new Message(Type.SYSTEM);
        m.room = room;
        m.text = text;
        return m;
    }

    // 유저 목록 생성자
    public static Message userList(String room, List<String> users) {
        Message m = new Message(Type.USER_LIST);
        m.room = room;
        m.users = users;
        return m;
    }

    // 이미지 전달 생성자
    public static Message sendImage(String room, String sender, ImageIcon image) {
        Message m = new Message(Type.IMAGE);
        m.room = room;
        m.sender = sender;
        m.image = image;
        return m;
    }

    // 오목 게임 생성자
    public static Message gameJoin(String room, GameType gameType) {
        Message m = new Message(Type.GAME_EVENT);
        m.room = room;
        m.gameType = gameType;
        m.gameAction = GameAction.REQUEST_JOIN;
        return m;
    }
    // 오목 게임 플레이어 참여 생성자
    public static Message gameJoinPlayer(String room) {
        Message m = new Message(Type.GAME_EVENT);
        m.room = room;
        m.gameType = GameType.OMOK;
        m.gameAction = GameAction.REQUEST_JOIN_PLAYER;
        return m;
    }


    // 바둑알 이동 생성자
    public static Message gameMove(String room, int x, int y) {
        Message m = new Message(Type.GAME_EVENT);
        m.room = room;
        m.gameType = GameType.OMOK;
        m.gameAction = GameAction.MOVE;
        m.x = x;
        m.y = y;
        return m;
    }

    // 오목 게임 기권 생성자
    public static Message gameResign(String room) {
        Message m = new Message(Type.GAME_EVENT);
        m.room = room;
        m.gameType = GameType.OMOK;
        m.gameAction = GameAction.RESIGN;
        return m;
    }

    // 게임만 나가기 (방은 유지)
    public static Message gameLeave(String room) {
        Message m = new Message(Type.GAME_EVENT);
        m.room = room;
        m.gameAction = GameAction.LEAVE_GAME;
        return m;
    }

    // 오목 게임 상태 동기화 생성자
    public static Message gameState(String room,
                                    int[][] board,
                                    String blackPlayer,
                                    String whitePlayer,
                                    String currentTurn,
                                    boolean finished,
                                    String winner,
                                    String resultReason,
                                    List<String> spectators) {
        Message m = new Message(Type.GAME_EVENT);
        m.room = room;
        m.gameType = GameType.OMOK;
        m.gameAction = GameAction.STATE;
        m.board = board;
        m.blackPlayer = blackPlayer;
        m.whitePlayer = whitePlayer;
        m.currentTurn = currentTurn;
        m.finished = finished;
        m.winner = winner;
        m.resultReason = resultReason;
        m.spectators = spectators;
        return m;
    }

    // 단어 맞추기 게임 생성자들
    public static Message wordJoin(String room) {
        Message m = new Message(Type.GAME_EVENT);
        m.room = room;
        m.gameType = GameType.WORD;
        m.gameAction = GameAction.REQUEST_JOIN;
        return m;
    }

  
    // 단어 맞추기 게임 단어 제출 생성자
    public static Message wordSubmit(String room, String word) {
        Message m = new Message(Type.GAME_EVENT);
        m.room = room;
        m.gameType = GameType.WORD;
        m.gameAction = GameAction.SUBMIT_WORD;
        m.text = word;
        return m;
    }

    // 단어 맞추기 게임 라운드 시작 생성자
    public static Message wordStartRound(String room) {
        Message m = new Message(Type.GAME_EVENT);
        m.room = room;
        m.gameType = GameType.WORD;
        m.gameAction = GameAction.START_WORD_ROUND;
        return m;
    }

    // 단어 맞추기 게임 상태 동기화 생성자
    public static Message wordState(String room,
                                    List<WordItem> items,
                                    List<String> players,
                                    List<String> spectators,
                                    Map<String, Integer> scores,
                                    boolean running,
                                    int remainingSeconds,
                                    String winner,
                                    String infoText,
                                    String hostPlayer) {
        Message m = new Message(Type.GAME_EVENT);
        m.room = room;
        m.gameType = GameType.WORD;
        m.gameAction = GameAction.STATE;
        m.wordItems = items;
        m.players = players;
        m.spectators = spectators;
        m.scores = scores;
        m.running = running;
        m.remainingSeconds = remainingSeconds;
        m.winner = winner;
        m.infoText = infoText;
        m.hostPlayer = hostPlayer;
        m.finished = !running;
        return m;
    }

    // 단어 맞추기 게임 오류 생성자
    public static Message wordError(String room, String text) {
        Message m = new Message(Type.GAME_EVENT);
        m.room = room;
        m.gameType = GameType.WORD;
        m.gameAction = GameAction.ERROR;
        m.text = text;
        return m;
    }

    // 오목 게임 오류 생성자
    public static Message gameError(String room, String text) {
        Message m = new Message(Type.GAME_EVENT);
        m.room = room;
        m.gameAction = GameAction.ERROR;
        m.text = text;
        return m;
    }

    // getter
    public Type getType() { return type; }
    public String getRoom() { return room; }
    public String getSender() { return sender; }
    public String getText() { return text; }
    public List<String> getRooms() { return rooms; }
    public List<String> getUsers() { return users; }
    public ImageIcon getImage() { return image; }

    // 오목 게임 getter
    public GameAction getGameAction() { return gameAction; }
    public GameType getGameType() { return gameType; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int[][] getBoard() { return board; }
    public String getBlackPlayer() { return blackPlayer; }
    public String getWhitePlayer() { return whitePlayer; }
    public String getCurrentTurn() { return currentTurn; }
    public boolean isFinished() { return finished; }
    public String getWinner() { return winner; }
    public String getResultReason() { return resultReason; }
    public List<String> getSpectators() { return spectators; }

    // 단어 맞추기 게임 getter
    public List<WordItem> getWordItems() { return wordItems; }
    public List<String> getPlayers() { return players; }
    public Map<String, Integer> getScores() { return scores; }
    public int getRemainingSeconds() { return remainingSeconds; }
    public boolean isRunning() { return running; }
    public String getInfoText() { return infoText; }
    public String getHostPlayer() { return hostPlayer; }

    // 단어 맞추기 게임 단어 아이템 getter
    public static class WordItem implements Serializable {
        private final String text;
        private final int x;
        private final int y;
        private final String answeredBy;

        public WordItem(String text, int x, int y, String answeredBy) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.answeredBy = answeredBy;
        }

        public String getText() { return text; }
        public int getX() { return x; }
        public int getY() { return y; }
        public String getAnsweredBy() { return answeredBy; }
    }
}
