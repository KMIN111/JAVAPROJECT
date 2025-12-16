package chat.server;

import chat.shared.Message;

import javax.swing.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler extends Thread {

    private final Socket socket;
    private final ChatServer server;

    private ObjectInputStream in;
    private ObjectOutputStream out;

    private String nickname;

    // 변경: 방 하나가 아니라, 여러 방 동시 참여 지원
    private final Set<ChatRoom> joinedRooms = ConcurrentHashMap.newKeySet();

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getNickname() {
        return nickname;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            Object obj = in.readObject();
            if (!(obj instanceof Message first) ||
                    first.getType() != Message.Type.LOGIN ||
                    first.getSender() == null ||
                    first.getSender().isBlank()) {

                send(Message.error("첫 메시지는 LOGIN 이어야 합니다."));
                close();
                return;
            }

            this.nickname = first.getSender().trim();
            server.addClient(this);
            send(Message.system("환영합니다, " + nickname + "님!"));

            while (true) {
                Object o = in.readObject();
                if (!(o instanceof Message msg)) {
                    continue;
                }
                handleMessage(msg);
            }

        } catch (Exception e) {
            System.out.println("[ClientHandler] 종료(" + nickname + "): " + e.getMessage());
        } finally {
            // 변경: 참여 중인 모든 방에서 빠져나오기
            for (ChatRoom room : joinedRooms) {
                room.leave(this);
                // 빈 방 자동 삭제 비활성화 - 수동 삭제만 허용
            }
            joinedRooms.clear();

            server.removeClient(this);
            close();
        }
    }

    private void handleMessage(Message msg) {
        switch (msg.getType()) {
            case ROOM_LIST:
                server.sendRoomListTo(this);
                break;

            case CREATE_ROOM:
                handleCreateRoom(msg);
                break;

            case DELETE_ROOM:
                handleDeleteRoom(msg);
                break;

            case JOIN_ROOM:
                handleJoinRoom(msg);
                break;

            case CHAT:
                handleChat(msg);
                break;

            case IMAGE:
                handleSendImage(msg);
                break;

            case FILE:
                handleSendFile(msg);
                break;

            case GAME_EVENT:
                handleGameEvent(msg);
                break;

            case LEAVE_ROOM:
                handleLeaveRoom(msg);

            default:
                send(Message.error("지원하지 않는 타입: " + msg.getType()));
        }
    }

    private void handleGameEvent(Message msg) {
        String roomName = msg.getRoom();
        if (roomName == null || roomName.isBlank()) {
            send(Message.gameError(null, "방 정보가 없습니다."));
            return;
        }
        ChatRoom room = server.getRoom(roomName.trim());
        if (room == null) {
            send(Message.gameError(roomName, "존재하지 않는 방입니다."));
            return;
        }
        Message.GameType gameType = msg.getGameType() == null ? Message.GameType.OMOK : msg.getGameType();
        if (gameType == Message.GameType.WORD) {
            handleWordGame(room, msg);
            return;
        }
        handleOmokGame(room, msg);
    }

    private void handleOmokGame(ChatRoom room, Message msg) {
        String roomName = room.getName();
        OmokGame game = room.getOrCreateGame();

        try {
            switch (msg.getGameAction()) {
                case REQUEST_JOIN -> {
                    // 자동으로 플레이어/관전자 결정
                    game.tryJoin(nickname);
                    room.broadcast(game.toStateMessage(), false);
                }
                case REQUEST_JOIN_PLAYER -> {
                    game.joinAsPlayer(nickname);
                    room.broadcast(game.toStateMessage(), false);
                }
               
                case MOVE -> {
                    game.placeStone(nickname, msg.getX(), msg.getY());
                    room.broadcast(game.toStateMessage(), false);
                }
                case RESIGN -> {
                    game.resign(nickname);
                    room.broadcast(game.toStateMessage(), false);
                }
                case RESTART -> {
                    game.restart(nickname);
                    room.broadcast(game.toStateMessage(), false);
                }
                case LEAVE_GAME -> {
                    game.leaveGame(nickname);
                    room.broadcast(game.toStateMessage(), false);
                }
                default -> send(Message.gameError(roomName, "지원하지 않는 게임 액션입니다."));
            }
        } catch (Exception e) {
            send(Message.gameError(roomName, e.getMessage()));
        }
    }

    private void handleWordGame(ChatRoom room, Message msg) {
        String roomName = room.getName();
        WordGame game = room.getOrCreateWordGame();
        try {
            switch (msg.getGameAction()) {
                case REQUEST_JOIN, REQUEST_JOIN_PLAYER -> {
                    game.joinAsPlayer(nickname);
                }
                case SUBMIT_WORD -> game.submitWord(nickname, msg.getText());
                case START_WORD_ROUND -> game.startRound(nickname);
                case LEAVE_GAME -> game.leaveGame(nickname);
                default -> {
                    send(Message.wordError(roomName, "지원하지 않는 단어 게임 액션입니다."));
                    return;
                }
            }
            room.broadcast(game.toStateMessage(), false);
        } catch (Exception e) {
            send(Message.wordError(roomName, e.getMessage()));
        }
    }

    private void handleLeaveRoom(Message msg) {
        String roomName = msg.getRoom();
        if (roomName == null || roomName.isBlank()) {
            send(Message.error("방 이름이 비어 있습니다."));
            return;
        }

        ChatRoom room = server.getRoom(roomName.trim());
        if (room == null) {
            send(Message.error("존재하지 않는 방입니다: " + roomName));
            return;
        }

        if (joinedRooms.remove(room)) {
            room.leave(this); // 참가자 목록에서 제거 + 브로드캐스트
            // 빈 방 자동 삭제 비활성화 - 수동 삭제만 허용
        }
    }

    private void handleCreateRoom(Message msg) {
        String roomName = msg.getRoom();
        if (roomName == null || roomName.isBlank()) {
            send(Message.error("방 이름을 입력하세요."));
            return;
        }

        ChatRoom room = server.getOrCreateRoom(roomName.trim());
        System.out.println("[Server] 방 생성/존재 확인: " + room.getName());

        // 방 목록 변경 → 전체에게 갱신
        server.broadcastRoomListToAll();
    }

    private void handleDeleteRoom(Message msg) {
        String roomName = msg.getRoom();
        if (roomName == null || roomName.isBlank()) {
            send(Message.error("방 이름이 비어 있습니다."));
            return;
        }

        boolean deleted = server.deleteRoom(roomName.trim());
        if (deleted) {
            send(Message.system("'" + roomName + "' 방이 삭제되었습니다."));
        } else {
            send(Message.error("존재하지 않는 방입니다: " + roomName));
        }
    }

    // 변경: JOIN 시 기존 방을 나가지 않고, 여러 방에 동시에 참여 가능하도록
    private void handleJoinRoom(Message msg) {
        String roomName = msg.getRoom();
        if (roomName == null || roomName.isBlank()) {
            send(Message.error("방 이름이 비어 있습니다."));
            return;
        }

        ChatRoom room = server.getOrCreateRoom(roomName.trim());

        if (joinedRooms.contains(room)) {
            send(Message.system("이미 '" + room.getName() + "' 방에 입장해 있습니다."));
            return;
        }

        joinedRooms.add(room);
        room.join(this);
    }

    // 변경: currentRoom 대신 msg.getRoom() 기준으로 방 찾기
    private void handleChat(Message msg) {
        String roomName = msg.getRoom();
        if (roomName == null || roomName.isBlank()) {
            send(Message.error("채팅을 보낼 방 정보가 없습니다."));
            return;
        }

        ChatRoom room = server.getRoom(roomName.trim());
        if (room == null) {
            send(Message.error("존재하지 않는 방입니다: " + roomName));
            return;
        }

        if (!joinedRooms.contains(room)) {
            send(Message.error("해당 방에 입장한 후에 채팅을 보낼 수 있습니다: " + room.getName()));
            return;
        }

        String text = msg.getText();
        if (text == null || text.isBlank()) return;

        room.broadcast(Message.chat(
                room.getName(),
                nickname,
                text
        ), true);
    }

    // 이미지도 동일하게 msg.getRoom() 기준으로 처리
    private void handleSendImage(Message msg) {
        String roomName = msg.getRoom();
        if (roomName == null || roomName.isBlank()) {
            send(Message.error("이미지를 보낼 방 정보가 없습니다."));
            return;
        }

        ChatRoom room = server.getRoom(roomName.trim());
        if (room == null) {
            send(Message.error("존재하지 않는 방입니다: " + roomName));
            return;
        }

        if (!joinedRooms.contains(room)) {
            send(Message.error("해당 방에 입장한 후에 이미지를 보낼 수 있습니다: " + room.getName()));
            return;
        }

        ImageIcon img = msg.getImage();
        if (img == null) {
            return;
        }

        room.broadcastImage(nickname, img, true);
    }

    // 파일 전송 처리
    private void handleSendFile(Message msg) {
        String roomName = msg.getRoom();
        if (roomName == null || roomName.isBlank()) {
            send(Message.error("파일을 보낼 방 정보가 없습니다."));
            return;
        }

        ChatRoom room = server.getRoom(roomName.trim());
        if (room == null) {
            send(Message.error("존재하지 않는 방입니다: " + roomName));
            return;
        }

        if (!joinedRooms.contains(room)) {
            send(Message.error("해당 방에 입장한 후에 파일을 보낼 수 있습니다: " + room.getName()));
            return;
        }

        String fileName = msg.getFileName();
        byte[] fileData = msg.getFileData();
        if (fileName == null || fileData == null) {
            send(Message.error("파일 정보가 올바르지 않습니다."));
            return;
        }

        room.broadcastFile(nickname, fileName, fileData, true);
    }

    public void send(Message msg) {
        try {
            if (out != null) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (Exception e) {
            System.out.println("[ClientHandler] send 실패(" + nickname + "): " + e.getMessage());
        }
    }

    private void close() {
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}
