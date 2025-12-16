package chat.server;

import chat.shared.Message;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    // 전체 접속 클라이언트
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    // 방 이름 → ChatRoom
    private final Map<String, ChatRoom> rooms = new ConcurrentHashMap<>();

    public void addClient(ClientHandler client) {
        clients.add(client);
        System.out.println("[Server] 클라이언트 등록: " + client.getNickname());
        // 새로 접속한 클라이언트에게 현재 방 목록 보내주기
        sendRoomListTo(client);
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("[Server] 클라이언트 제거: " + client.getNickname());
    }

    public ChatRoom getOrCreateRoom(String roomName) {
        return rooms.computeIfAbsent(roomName, ChatRoom::new);
    }

    //public Collection<String> getRoomNames() {
       // return rooms.keySet();
    //}

    public ChatRoom getRoom(String roomName) {
        if (roomName == null) return null;
        return rooms.get(roomName);
    }

    public void removeEmptyRoom(String roomName) {
        ChatRoom room = rooms.get(roomName);
        if (room != null && room.isEmpty()) {
            rooms.remove(roomName);
            System.out.println("[Server] 빈 방 삭제: " + roomName);
            broadcastRoomListToAll(); // 방이 사라졌으니 전체에게 목록 갱신 푸시
        }
    }

    // 방 강제 삭제 (참여자가 있어도 삭제)
    public boolean deleteRoom(String roomName) {
        ChatRoom room = rooms.get(roomName);
        if (room == null) {
            return false;
        }

        // 방에 있는 모든 참여자에게 방 삭제 알림
        room.broadcast(Message.system("채팅방이 삭제되었습니다."), true);

        rooms.remove(roomName);
        System.out.println("[Server] 방 삭제: " + roomName);
        broadcastRoomListToAll();
        return true;
    }

    public void broadcastRoomListToAll() {
        List<String> names = List.copyOf(rooms.keySet());
        Message m = Message.roomList(names);
        for (ClientHandler ch : clients) {
            ch.send(m);
        }
    }

    public void sendRoomListTo(ClientHandler client) {
        List<String> names = List.copyOf(rooms.keySet());
        client.send(Message.roomList(names));
    }

    //전체 공지
    public void broadcastSystem(String text) {
        Message m = Message.system(text);
        for (ClientHandler ch : clients) {
            ch.send(m);
        }
    }
}
