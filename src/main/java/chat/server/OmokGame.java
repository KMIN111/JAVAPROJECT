package chat.server;

import chat.shared.Message;

import java.util.HashSet;
import java.util.Set;


public class OmokGame {

    public static final int BOARD_SIZE = 15;

    private final ChatRoom room;

    private final int[][] board = new int[BOARD_SIZE][BOARD_SIZE]; // 0: 빈칸, 1: 흑, 2: 백

    private String blackPlayer; // 흑 플레이어 닉네임
    private String whitePlayer; // 백 플레이어 닉네임
    private String currentTurn; // 닉네임 기준
    private boolean finished; // 게임 종료 여부
    private String winner; // 게임 승자
    private String resultReason; // 게임 결과 (승리, 무승부, 기권)

    private final Set<String> spectators = new HashSet<>(); // 게임 관전자 목록

    public OmokGame(ChatRoom room) {
        this.room = room;
    }

    //한 번에 한 스레드만 들어올 수 있게 잠금
    public synchronized void joinAsPlayer(String nickname) {
        if (finished) reset();

        // 이미 플레이어라면 그대로 유지
        if (nickname.equals(blackPlayer) || nickname.equals(whitePlayer)) {
            return;
        }

        // 슬롯 배정
        if (blackPlayer == null) {
            blackPlayer = nickname;
        } else if (whitePlayer == null) {
            whitePlayer = nickname;
        } else {// 게임 미시작, 종료 상태 및 2명이 게임에 참여중일 때 수를 두지 못하게 예외 처리
            throw new IllegalStateException("이미 두 플레이어가 참여 중입니다.");
        }

        spectators.remove(nickname); //관전자 목록에 있으면 제거

        // 두 명이 모두 채워지면 게임 시작
        if (blackPlayer != null && whitePlayer != null && currentTurn == null) {
            currentTurn = blackPlayer; // 흑 선
            finished = false;
            winner = null;
            resultReason = null;
        }
    }

    // 자동 플레이어/관전자 결정 메서드
    public synchronized void tryJoin(String nickname) {
        if (finished) reset();

        // 이미 플레이어인 경우 그대로 유지
        if (nickname.equals(blackPlayer) || nickname.equals(whitePlayer)) {
            return;
        }

        // 빈 슬롯이 있으면 플레이어로
        if (blackPlayer == null) {
            blackPlayer = nickname;
            spectators.remove(nickname);
            if (whitePlayer != null && currentTurn == null) {
                currentTurn = blackPlayer;
            }
            return;
        } else if (whitePlayer == null) {
            whitePlayer = nickname;
            spectators.remove(nickname);
            if (blackPlayer != null && currentTurn == null) {
                currentTurn = blackPlayer;
            }
            return;
        }

        // 슬롯이 다 찼으면 관전자로 (자동 관전)
        spectators.add(nickname);
    }

    // 게임 기권 메서드
    public synchronized void resign(String nickname) {
        if (finished) return;
        if (!nickname.equals(blackPlayer) && !nickname.equals(whitePlayer)) {
            return;
        }
        finished = true;
        winner = nickname.equals(blackPlayer) ? whitePlayer : blackPlayer;
        //승리이유 기권
        resultReason = "RESIGN";
    }

    // 게임 다시하기 - 플레이어 유지, 보드 초기화
    public synchronized void restart(String nickname) {
        // 플레이어만 다시하기 가능
        if (!nickname.equals(blackPlayer) && !nickname.equals(whitePlayer)) {
            throw new IllegalStateException("플레이어만 다시하기를 요청할 수 있습니다.");
        }
        // 게임이 종료된 상태에서만 다시하기 가능
        if (!finished) {
            throw new IllegalStateException("게임이 진행 중입니다.");
        }
        clearBoard();
        finished = false;
        winner = null;
        resultReason = null;
        currentTurn = blackPlayer; // 흑 선
    }

    // 게임만 나가기 (방 유지) - 나간 사용자만 제거
    public synchronized void leaveGame(String nickname) {
        boolean isBlack = nickname.equals(blackPlayer);
        boolean isWhite = nickname.equals(whitePlayer);

        if (isBlack || isWhite) {
            if (isBlack) blackPlayer = null;
            if (isWhite) whitePlayer = null;

            clearBoard();
            finished = false;
            winner = null;
            resultReason = null;
            currentTurn = null;
        }
        spectators.remove(nickname); //관전자 목록에 있으면 제거
    }
    
    // 오목판 초기화
    private void clearBoard() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                board[i][j] = 0;
            }
        }
    }

    // 오목판 수 두기
    public synchronized void placeStone(String nickname, int x, int y) {
        validateInRange(x, y);
        if (finished) {
            throw new IllegalStateException("이미 종료된 게임입니다.");
        }
        if (!nickname.equals(currentTurn)) {
            throw new IllegalStateException("지금은 " + currentTurn + "의 차례입니다.");
        }
        // 흑이면 1 백이면 2
        int stone = nickname.equals(blackPlayer) ? 1 : (nickname.equals(whitePlayer) ? 2 : 0);
        if (stone == 0) {
            throw new IllegalStateException("플레이어가 아닌 사용자는 수를 둘 수 없습니다.");
        }

        if (board[x][y] != 0) {
            throw new IllegalStateException("이미 돌이 놓인 자리입니다.");
        }

        board[x][y] = stone;

        if (checkWin(x, y, stone)) {
            finished = true;
            winner = nickname;
            resultReason = "WIN";
        } else if (isBoardFull()) {
            finished = true;
            winner = null;
            resultReason = "DRAW";
        } else {
            // 턴 전환
            currentTurn = (stone == 1) ? whitePlayer : blackPlayer;
        }
    }

    // 브로드 캐스트 시 복사본 전달 (서버 내부 보호)
    public synchronized Message toStateMessage() {
        // 보드 복사본 제공
        int[][] snapshot = new int[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            System.arraycopy(board[i], 0, snapshot[i], 0, BOARD_SIZE);
        }

        java.util.List<String> spectatorList = new java.util.ArrayList<>(spectators);

        return Message.gameState(
                room.getName(),
                snapshot,
                blackPlayer,
                whitePlayer,
                currentTurn,
                finished,
                winner,
                resultReason,
                spectatorList
        );
    }

    public synchronized void onUserLeft(String nickname) {
        // 플레이어가 나가면 결과 없이 게임 상태 초기화
        if (nickname.equals(blackPlayer) || nickname.equals(whitePlayer)) {
            reset();
            return;
        }
        // 관전자만 제거
        spectators.remove(nickname);
    }

    public synchronized boolean isPlayer(String nickname) {
        return nickname != null && (nickname.equals(blackPlayer) || nickname.equals(whitePlayer));
    }

    public synchronized boolean hasPlayers() {
        return blackPlayer != null || whitePlayer != null;
    }

    public synchronized boolean isFinished() {
        return finished;
    }

    private void validateInRange(int x, int y) {
        if (x < 0 || y < 0 || x >= BOARD_SIZE || y >= BOARD_SIZE) {
            throw new IllegalArgumentException("좌표가 범위를 벗어났습니다.");
        }
    }

    // 오목판 돌이 전부 차있는지 확인하는 메소드드
    private boolean isBoardFull() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == 0) return false;
            }
        }
        return true;
    }

    private boolean checkWin(int x, int y, int stone) {
        // 4개 방향(가로, 세로, 대각2)에서 연속 5개 확인
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int count = 1;
            count += countDir(x, y, d[0], d[1], stone);
            count += countDir(x, y, -d[0], -d[1], stone);
            if (count >= 5) return true;
        }
        return false;
    }

    // 오목판 돌이 연속 5개 있는지 확인하는 메소드
    private int countDir(int x, int y, int dx, int dy, int stone) {
        int cnt = 0;
        int cx = x + dx;
        int cy = y + dy;
        while (cx >= 0 && cy >= 0 && cx < BOARD_SIZE && cy < BOARD_SIZE && board[cx][cy] == stone) {
            cnt++;
            cx += dx;
            cy += dy;
        }
        return cnt;
    }

    // 게임 상태 초기화
    private void reset() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                board[i][j] = 0;
            }
        }
        finished = false;
        winner = null;
        resultReason = null;
        currentTurn = null;
        blackPlayer = null;
        whitePlayer = null;
        spectators.clear();
    }
}

