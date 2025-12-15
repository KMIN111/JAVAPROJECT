import chat.shared.Message;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;

public class ChatFrame extends JFrame {

    private final ChatClient client;
    private final String roomName;
    private final RoomListFrame parentList; // 나가기 시 목록으로 복귀

    private final ChatClient.Listener listener;

    private JTextPane chatPane;
    private JTextField inputField;
    private JButton sendButton, selectButton;

    private DefaultListModel<String> userListModel;
    private JList<String> userList;

    // 게임 UI
    private JButton gameButton;
    private OmokWindow omokWindow;
    private WordGameWindow wordWindow;
    private boolean requestedOmokWindow = false; // 내가 직접 참여/관전 버튼을 눌렀는지
    private boolean requestedWordWindow = false;
    private boolean active = true; // 창이 닫힌 뒤에는 게임 메시지 무시

    public ChatFrame(ChatClient client, String roomName, RoomListFrame parentList) {
        this.client = client;
        this.roomName = roomName;
        this.parentList = parentList;

        setTitle("채팅방 - " + roomName);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 500);
        setLocationRelativeTo(null);

        initUI();

        // 서버 메시지 리스너 등록
        listener = this::handleServerMessage;
        client.addListener(listener);
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 상단 타이틀 + 나가기 버튼
        JPanel topBar = new JPanel(new BorderLayout());
        JLabel title = new JLabel("방 이름: " + roomName);
        title.setFont(new Font("Dialog", Font.BOLD, 16));
        topBar.add(title, BorderLayout.WEST);

        JButton leaveBtn = new JButton("채팅방 나가기");
        leaveBtn.addActionListener(e -> dispose());
        topBar.add(leaveBtn, BorderLayout.EAST);
        main.add(topBar, BorderLayout.NORTH);

        // ===== 중앙: 채팅창 (왼쪽) + 유저 목록(오른쪽) =====
        JPanel center = new JPanel(new BorderLayout(8, 8));

        // 채팅창
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(Color.WHITE);

        JScrollPane chatScroll = new JScrollPane(chatPane);
        center.add(chatScroll, BorderLayout.CENTER);

        // 유저 목록 패널 (오른쪽)
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setPreferredSize(new Dimension(180, 0));
        userPanel.setBorder(BorderFactory.createTitledBorder("참여자"));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        userPanel.add(new JScrollPane(userList), BorderLayout.CENTER);

        center.add(userPanel, BorderLayout.EAST);

        main.add(center, BorderLayout.CENTER);

        // 이미지 선택, 채팅 전송
        JPanel bottom = new JPanel(new BorderLayout(5, 0));

        inputField = new JTextField();
        sendButton = new JButton("전송");
        selectButton = new JButton("선택하기");

        // 왼쪽에 입력창
        bottom.add(inputField, BorderLayout.CENTER);

        // 오른쪽에 선택하기, 전송
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        buttonPanel.add(selectButton);
        buttonPanel.add(sendButton);
        bottom.add(buttonPanel, BorderLayout.EAST);

        // 게임 컨트롤 영역
        JPanel gamePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        gameButton = new JButton("게임하기");
        gamePanel.add(gameButton);
        bottom.add(gamePanel, BorderLayout.NORTH);

        main.add(bottom, BorderLayout.SOUTH);

        // 이벤트 연결
        inputField.addActionListener(e -> sendCurrentMessage());
        sendButton.addActionListener(e -> sendCurrentMessage());

        // 이미지 선택 버튼
        selectButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            FileNameExtensionFilter filter = new FileNameExtensionFilter(
                    "JPG & GIF & PNG Images",
                    "jpg", "gif", "png");
            chooser.setFileFilter(filter);

            int ret = chooser.showOpenDialog(ChatFrame.this);
            if (ret != JFileChooser.APPROVE_OPTION) {
                JOptionPane.showMessageDialog(
                        ChatFrame.this,
                        "파일을 선택하지 않음",
                        "경고",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }
            inputField.setText(chooser.getSelectedFile().getAbsolutePath());
            try {
                sendImage();
            } catch (Exception ex) {
                appendSystem("[오류] 이미지 전송 실패: " + ex.getMessage());
            }
        });

        gameButton.addActionListener(e -> showGameSelectionDialog());

        setContentPane(main);
    }

    private void sendCurrentMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        try {
            client.send(Message.chat(roomName, client.getNickname(), text));
            inputField.setText("");
        } catch (Exception e) {
            appendSystem("[오류] 메시지 전송 실패: " + e.getMessage());
        }
    }

    private void sendImage() throws Exception {
        String filename = inputField.getText().strip();
        if (filename.isEmpty()) return;

        File file = new File(filename);
        if (!file.exists()) {
            appendSystem(">>파일이 존재하지 않습니다: " + filename);
            return;
        }

        ImageIcon icon = new ImageIcon(filename);
        client.send(Message.sendImage(roomName, client.getNickname(), icon));

        inputField.setText("");
    }

    // 서버에서 오는 메세지 타입별 처리
    private void handleServerMessage(Message m) {
        switch (m.getType()) {
            case CHAT -> {
                if (roomName.equals(m.getRoom())) {
                    appendChat(m.getSender() + ": " + m.getText());
                }
            }
            case SYSTEM -> {
                String room = m.getRoom();
                if (room == null || roomName.equals(room)) {
                    appendSystem(m.getText());
                }
            }
            case ERROR -> appendSystem("[오류] " + m.getText());
            case USER_LIST -> {
                if (roomName.equals(m.getRoom())) {
                    updateUserList(m);
                }
            }

            case IMAGE -> {
                if (roomName.equals(m.getRoom())) {
                    appendChat(m.getSender() + "님이 이미지를 보냈습니다."); // 텍스트 추가
                    appendImage(m.getImage());  ;
                }
            }

            default -> {
                if (m.getType() == Message.Type.GAME_EVENT && roomName.equals(m.getRoom())) {
                    handleGameMessage(m);
                }
            }
        }
    }

    private void handleGameMessage(Message m) {
        // EDT에서 안전하게 처리 (스레드 동기화)
        SwingUtilities.invokeLater(() -> {
            if (!active) return; // 이미 닫힌 창이면 무시
            if (m.getGameAction() == Message.GameAction.ERROR) {
                appendSystem("[게임] " + m.getText());
                return;
            }

            Message.GameType type = m.getGameType() == null ? Message.GameType.OMOK : m.getGameType();
            if (type == Message.GameType.WORD) {
                handleWordState(m);
                return;
            }

            // OMOK STATE
            if (m.getGameAction() == Message.GameAction.STATE) {
                boolean iAmPlayer = client.getNickname().equals(m.getBlackPlayer()) || client.getNickname().equals(m.getWhitePlayer());
                boolean shouldOpen = iAmPlayer || requestedOmokWindow;
                if (!shouldOpen) {
                    // 참여/관전 의사 없고 내가 플레이어도 아니면 창 자동 오픈하지 않음
                    return;
                }
                ensureOmokWindow();
                omokWindow.applyState(m);
                if (!omokWindow.isVisible()) {
                    omokWindow.setVisible(true);
                    // 게임 창이 열리면 채팅창 숨김
                    ChatFrame.this.setVisible(false);
                }
            }
        });
    }

    private void handleWordState(Message m) {
        boolean iAmPlayer = m.getPlayers() != null && m.getPlayers().contains(client.getNickname());
        boolean shouldOpen = iAmPlayer || requestedWordWindow;
        if (!shouldOpen) return;
        ensureWordWindow();
        wordWindow.applyState(m);
        if (!wordWindow.isVisible()) {
            wordWindow.setVisible(true);
            ChatFrame.this.setVisible(false);
        }
    }

    private void ensureOmokWindow() {
        // 이미 EDT에서 호출되므로 synchronized 불필요
        if (omokWindow == null) {
            omokWindow = new OmokWindow(client, roomName, client.getNickname(), this::onExitOmok);
        }
    }

    private void ensureWordWindow() {
        if (wordWindow == null) {
            wordWindow = new WordGameWindow(client, roomName, client.getNickname(), this::onExitWord);
        }
    }

    private void onExitOmok() {
        // 다시 버튼을 눌러야 창을 띄우도록 플래그 초기화
        requestedOmokWindow = false;
        // 게임 종료 후 채팅창 복귀
        ChatFrame.this.setVisible(true);
    }

    private void onExitWord() {
        requestedWordWindow = false;
        ChatFrame.this.setVisible(true);
    }

    private void showGameSelectionDialog() {
        GameSelectionDialog dialog = new GameSelectionDialog(this, this::requestGameJoin);
        dialog.setVisible(true);
    }

    private void requestGameJoin(Message.GameType gameType) {
        if (gameType == null) return;

        try {
            if (gameType == Message.GameType.OMOK) {
                requestedOmokWindow = true;
            } else if (gameType == Message.GameType.WORD) {
                requestedWordWindow = true;
            }
            client.send(Message.gameJoin(roomName, gameType));
        } catch (Exception e) {
            appendSystem("[게임] 참여 실패: " + e.getMessage());
        }
    }

    private void updateUserList(Message m) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            if (m.getUsers() != null) {
                for (String u : m.getUsers()) {
                    userListModel.addElement(u);
                }
            }
        });
    }

    // 채팅창에 채팅, 이미지 추가

    private void appendChat(String text) {
        appendLine(text, StyleConstants.ALIGN_LEFT, Color.BLACK, false);
    }

    private void appendSystem(String text) {
        appendLine("[System] " + text, StyleConstants.ALIGN_CENTER, Color.GRAY, true);
    }

    private void appendImage(ImageIcon icon){
        // 이미지 크기 조절 + t_display에 아이콘 삽입
        chatPane.setCaretPosition(chatPane.getDocument().getLength());

        if(icon.getIconWidth() > 400){
            Image img = icon.getImage();
            Image changeImg = img. getScaledInstance(400,-1,Image.SCALE_SMOOTH);
            icon = new ImageIcon(changeImg);
        }

        chatPane.insertIcon(icon);

        appendChat("");
        inputField.setText("");
    }

    private void appendLine(String text, int align, Color color, boolean italic) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = chatPane.getStyledDocument();

            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setAlignment(attrs, align);
            StyleConstants.setForeground(attrs, color);
            StyleConstants.setBold(attrs, !italic);
            StyleConstants.setItalic(attrs, italic);

            try {
                int start = doc.getLength();
                doc.insertString(start, text + "\n", attrs);
                doc.setParagraphAttributes(start, (text + "\n").length(), attrs, false);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }

            chatPane.setCaretPosition(doc.getLength());
        });
    }

    @Override
    public void dispose() {
        active = false;
        client.removeListener(listener);

        if (omokWindow != null) {
            omokWindow.dispose();
            omokWindow = null;
        }
        if (wordWindow != null) {
            wordWindow.dispose();
            wordWindow = null;
        }
        try {
            client.send(Message.leaveRoom(roomName, client.getNickname()));
        } catch (Exception e) {
            System.out.println("LEABE ROOM 전송실패" +  e.getMessage());
        }
        requestedOmokWindow = false;
        requestedWordWindow = false;
        // 목록 화면으로 복귀
        if (parentList != null) {
            parentList.setVisible(true);
            parentList.requestRoomList();
        }
        super.dispose();
    }
}
