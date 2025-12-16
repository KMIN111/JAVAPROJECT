package chat.server;

import chat.shared.Message;

import javax.swing.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ChatRoom {

    private final String name;
    private final String roomName;
    private final Set<ClientHandler> participants = ConcurrentHashMap.newKeySet();

    private final List<Message> history = new CopyOnWriteArrayList<>();

    // 방 단위 오목 게임 세션
    private OmokGame currentGame;
    // 방 단위 단어 게임 세션
    private WordGame wordGame;

    public ChatRoom(String roomName) {
        this(roomName,roomName);
    }

    public ChatRoom(String name, String roomName) {
        this.name = name;
        this.roomName = roomName;
    }

    public String getName() {
        return name;
    }

    // 새로 들어온 클라이언트에게 과거 기록 전송
    public void sendHistoryTo(ClientHandler client) {
        for (Message m : history) {
            client.send(m);
        }
    }

    public void join(ClientHandler client) {
        participants.add(client);

        sendHistoryTo(client);

        broadcast(Message.systemForRoom(
                name,
                client.getNickname() + "님이 입장했습니다."
        ), true);

        // 3) 유저 목록 갱신
        broadcastUserList();

        System.out.println("[Room:" + name + "] join: " + client.getNickname());
    }

    public void leave(ClientHandler client) {
        if (participants.remove(client)) {
            // 오목
            OmokGame game = getCurrentGame();
            if (game != null) {
                boolean wasPlayer = game.isPlayer(client.getNickname());
                game.onUserLeft(client.getNickname());
                if (wasPlayer) {
                    broadcast(game.toStateMessage(), false);
                }
            }
            // 단어 맞추기
            WordGame wg = getCurrentWordGame();
            if (wg != null) {
                wg.onUserLeft(client.getNickname());
            }

            broadcast(Message.systemForRoom(
                    name,
                    client.getNickname() + "님이 퇴장했습니다."
            ), false);

            broadcastUserList();

            System.out.println("[Room:" + name + "] leave: " + client.getNickname());
        }
    }

    // saveHistory = true일 때만 history에 저장
    public void broadcast(Message msg, boolean saveHistory) {
        if (saveHistory) {
            history.add(msg);
        }
        for (ClientHandler ch : participants) {
            ch.send(msg);
        }
    }

    public void broadcastImage(String sender, ImageIcon icon, boolean saveHistory) {
        Message msg = Message.sendImage(roomName, sender, icon);
        broadcast(msg, saveHistory);
    }

    public void broadcastFile(String sender, String fileName, byte[] fileData, boolean saveHistory) {
        Message msg = Message.sendFile(roomName, sender, fileName, fileData);
        broadcast(msg, saveHistory);
    }

    // 기존 코드 호환용
    public void broadcast(Message msg) {
        broadcast(msg, false);
    }

    public boolean isEmpty() {
        return participants.isEmpty();
    }

    public void broadcastUserList() {
        List<String> users = participants.stream()
                .map(ClientHandler::getNickname)
                .sorted()
                .collect(Collectors.toList());

        Message m = Message.userList(name, users);
        for (ClientHandler ch : participants) {
            ch.send(m);
        }
    }

    // ===== 오목 게임 관리 =====
    public synchronized OmokGame getOrCreateGame() {
        if (currentGame == null || currentGame.isFinished() && !currentGame.hasPlayers()) {
            currentGame = new OmokGame(this);
        }
        return currentGame;
    }

    public synchronized OmokGame getCurrentGame() {
        return currentGame;
    }

    public synchronized WordGame getOrCreateWordGame() {
        if (wordGame == null) {
            wordGame = new WordGame(this);
        }
        return wordGame;
    }

    public synchronized WordGame getCurrentWordGame() {
        return wordGame;
    }
}
