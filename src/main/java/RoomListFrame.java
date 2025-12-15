import chat.shared.Message;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class RoomListFrame extends JFrame {

    private final ChatClient client;
    private final String host;
    private final int port;

    private DefaultListModel<String> roomListModel;
    private JList<String> roomList;
    private JLabel profileLabel;

    public RoomListFrame(ChatClient client, String host, int port) {
        this.client = client;
        this.host = host;
        this.port = port;

        setTitle("Talk & Play - 방 목록");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null);
        setResizable(false);

        initUI();

        // 서버 메시지 수신 리스너 등록
        client.addListener(this::handleServerMessage);

        // 접속하자마자 한 번 방 목록 요청
        requestRoomList();
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(0, 10));
        main.setBorder(new EmptyBorder(10, 10, 10, 10));
        main.setBackground(new Color(240, 253, 244));

        // 상단 프로필
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        profileLabel = new JLabel();
        profileLabel.setFont(new Font("Dialog", Font.BOLD, 14));
        profileLabel.setForeground(new Color(22, 30, 46));

        String profileText = String.format(
                "닉네임: %s   |   서버: %s:%d",
                client.getNickname(), host, port
        );
        profileLabel.setText(profileText);

        top.add(profileLabel, BorderLayout.WEST);

        main.add(top, BorderLayout.NORTH);

        // 중앙 방 목록
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scroll = new JScrollPane(roomList);
        main.add(scroll, BorderLayout.CENTER);

        // 하단 버튼 영역
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);

        JButton createBtn = new JButton("방 만들기");
        createBtn.addActionListener(e -> createRoom());

        JButton joinBtn = new JButton("입장하기");
        joinBtn.addActionListener(e -> joinSelectedRoom());

        bottom.add(createBtn);
        bottom.add(joinBtn);

        main.add(bottom, BorderLayout.SOUTH);

        setContentPane(main);
    }

    // 서버 메세지 처리
    private void handleServerMessage(Message m) {
        // 방 목록 관련 메시지만 처리하고, SYSTEM/ERROR 등은 여기서 무시
        if (m.getType() == Message.Type.ROOM_LIST) {
            SwingUtilities.invokeLater(() -> updateRoomList(m));
        }
    }

    private void updateRoomList(Message m) {
        roomListModel.clear();
        if (m.getRooms() != null) {
            for (String r : m.getRooms()) {
                roomListModel.addElement(r);
            }
        }
    }

    // 서버에 요청 보내기
    public void requestRoomList() {
        try {
            client.send(new Message(Message.Type.ROOM_LIST));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "방 목록 요청 실패: " + e.getMessage(),
                    "오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createRoom() {
        String name = JOptionPane.showInputDialog(
                this, "방 이름을 입력하세요:", "방 만들기",
                JOptionPane.PLAIN_MESSAGE
        );
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "방 이름을 입력해주세요.", "입력 오류",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            client.send(Message.createRoom(name));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "방 생성 실패: " + e.getMessage(),
                    "오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void joinSelectedRoom() {
        String selected = roomList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                    "입장할 방을 선택해주세요.",
                    "안내", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            ChatFrame chat = new ChatFrame(client, selected, this);
            chat.setVisible(true);
            // 방 목록 숨기기
            this.setVisible(false);

            client.send(Message.joinRoom(selected));


        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "방 입장 실패: " + e.getMessage(),
                    "오류", JOptionPane.ERROR_MESSAGE);
        }
    }


    @Override
    public void dispose() {
        client.removeListener(this::handleServerMessage);
        super.dispose();
    }
}
