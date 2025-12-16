import chat.shared.Message;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

public class ChatFrame extends JFrame {

    // 테마 색상 (LoginPage와 동일)
    private static final Color PRIMARY = new Color(34, 197, 94);
    private static final Color PRIMARY_HOVER = new Color(22, 163, 74);
    private static final Color BG_COLOR = new Color(240, 253, 244);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color TEXT_PRIMARY = new Color(22, 30, 46);
    private static final Color INPUT_BORDER = new Color(187, 247, 208);
    private static final Color INPUT_FOCUS = new Color(34, 197, 94);
    private static final Color MY_BUBBLE = new Color(34, 197, 94);
    private static final Color OTHER_BUBBLE = new Color(229, 231, 235);
    private static final Color SYSTEM_COLOR = new Color(107, 114, 128);

    private final ChatClient client;
    private final String roomName;
    private final RoomListFrame parentList;

    private final ChatClient.Listener listener;

    private JPanel chatPanel;
    private JScrollPane chatScroll;
    private JTextField inputField;
    private JButton sendButton, selectButton;

    private DefaultListModel<String> userListModel;
    private JList<String> userList;

    // 게임 UI
    private JButton gameButton;
    private OmokWindow omokWindow;
    private WordGameWindow wordWindow;
    private boolean requestedOmokWindow = false;
    private boolean requestedWordWindow = false;
    private boolean active = true;

    public ChatFrame(ChatClient client, String roomName, RoomListFrame parentList) {
        this.client = client;
        this.roomName = roomName;
        this.parentList = parentList;

        setTitle("채팅방 - " + roomName);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        initUI();

        listener = this::handleServerMessage;
        client.addListener(listener);
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(0, 0));
        main.setBackground(BG_COLOR);

        // 상단 헤더
        JPanel header = createHeader();
        main.add(header, BorderLayout.NORTH);

        // 중앙: 채팅 영역 + 유저 목록
        JPanel center = new JPanel(new BorderLayout(8, 0));
        center.setBackground(BG_COLOR);
        center.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 채팅 영역 (버블 형태)
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(CARD_BG);
        chatPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        chatScroll = new JScrollPane(chatPanel);
        chatScroll.setBorder(createRoundedBorder());
        chatScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        center.add(chatScroll, BorderLayout.CENTER);

        // 유저 목록 패널
        JPanel userPanel = createUserPanel();
        center.add(userPanel, BorderLayout.EAST);

        main.add(center, BorderLayout.CENTER);

        // 하단 입력 영역
        JPanel bottom = createBottomPanel();
        main.add(bottom, BorderLayout.SOUTH);

        setContentPane(main);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CARD_BG);
        header.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel title = new JLabel(roomName);
        title.setFont(new Font("Dialog", Font.BOLD, 18));
        title.setForeground(TEXT_PRIMARY);
        header.add(title, BorderLayout.WEST);

        JButton leaveBtn = createStyledButton("나가기", false);
        leaveBtn.setPreferredSize(new Dimension(80, 36));
        leaveBtn.addActionListener(e -> dispose());
        header.add(leaveBtn, BorderLayout.EAST);

        // 하단 구분선
        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.setBackground(CARD_BG);
        headerWrapper.add(header, BorderLayout.CENTER);
        JSeparator separator = new JSeparator();
        separator.setForeground(INPUT_BORDER);
        headerWrapper.add(separator, BorderLayout.SOUTH);

        return headerWrapper;
    }

    private JPanel createUserPanel() {
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setPreferredSize(new Dimension(160, 0));
        userPanel.setBackground(CARD_BG);
        userPanel.setBorder(BorderFactory.createCompoundBorder(
                createRoundedBorder(),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel userTitle = new JLabel("참여자");
        userTitle.setFont(new Font("Dialog", Font.BOLD, 14));
        userTitle.setForeground(TEXT_PRIMARY);
        userTitle.setBorder(new EmptyBorder(0, 0, 10, 0));
        userPanel.add(userTitle, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBackground(CARD_BG);
        userList.setForeground(TEXT_PRIMARY);
        userList.setFont(new Font("Dialog", Font.PLAIN, 13));
        userList.setCellRenderer(new UserListCellRenderer());

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setBorder(null);
        userPanel.add(userScroll, BorderLayout.CENTER);

        return userPanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setBackground(BG_COLOR);
        bottom.setBorder(new EmptyBorder(0, 10, 10, 10));

        // 게임 버튼
        JPanel gamePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        gamePanel.setBackground(BG_COLOR);
        gameButton = createStyledButton("게임하기", true);
        gameButton.setPreferredSize(new Dimension(100, 36));
        gameButton.addActionListener(e -> showGameSelectionDialog());
        gamePanel.add(gameButton);
        bottom.add(gamePanel, BorderLayout.NORTH);

        // 입력 영역
        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setBackground(CARD_BG);
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
                createRoundedBorder(),
                new EmptyBorder(8, 12, 8, 8)
        ));

        inputField = new JTextField();
        inputField.setBorder(null);
        inputField.setFont(new Font("Dialog", Font.PLAIN, 14));
        inputField.setForeground(TEXT_PRIMARY);
        inputField.setBackground(CARD_BG);
        inputField.addActionListener(e -> sendCurrentMessage());
        inputPanel.add(inputField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setBackground(CARD_BG);

        selectButton = createIconButton("📎");
        selectButton.addActionListener(e -> selectAndSendFile());
        buttonPanel.add(selectButton);

        sendButton = createStyledButton("전송", true);
        sendButton.setPreferredSize(new Dimension(70, 32));
        sendButton.addActionListener(e -> sendCurrentMessage());
        buttonPanel.add(sendButton);

        inputPanel.add(buttonPanel, BorderLayout.EAST);
        bottom.add(inputPanel, BorderLayout.CENTER);

        return bottom;
    }

    private JButton createStyledButton(String text, boolean isPrimary) {
        JButton btn = new JButton(text) {
            private boolean hover = false;

            {
                setFocusPainted(false);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setCursor(new Cursor(Cursor.HAND_CURSOR));

                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (isPrimary) {
                    g2.setColor(hover ? PRIMARY_HOVER : PRIMARY);
                } else {
                    g2.setColor(hover ? new Color(243, 244, 246) : CARD_BG);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

                if (!isPrimary) {
                    g2.setColor(INPUT_BORDER);
                    g2.setStroke(new BasicStroke(1));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                }

                g2.dispose();
                super.paintComponent(g);
            }
        };

        btn.setFont(new Font("Dialog", Font.BOLD, 13));
        btn.setForeground(isPrimary ? Color.WHITE : TEXT_PRIMARY);
        return btn;
    }

    private JButton createIconButton(String icon) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Dialog", Font.PLAIN, 18));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(36, 32));
        return btn;
    }

    private javax.swing.border.Border createRoundedBorder() {
        return BorderFactory.createLineBorder(INPUT_BORDER, 1, true);
    }

    // 메시지 버블 생성
    private JPanel createMessageBubble(String sender, String text, boolean isMe) {
        JPanel wrapper = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(3, 5, 3, 5));

        JPanel bubblePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isMe ? MY_BUBBLE : OTHER_BUBBLE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubblePanel.setLayout(new BoxLayout(bubblePanel, BoxLayout.Y_AXIS));
        bubblePanel.setOpaque(false);
        bubblePanel.setBorder(new EmptyBorder(8, 12, 8, 12));

        // 발신자 이름 (본인 메시지가 아닐 때만)
        if (!isMe) {
            JLabel nameLabel = new JLabel(sender);
            nameLabel.setFont(new Font("Dialog", Font.BOLD, 11));
            nameLabel.setForeground(new Color(75, 85, 99));
            nameLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubblePanel.add(nameLabel);
        }

        // 텍스트 길이에 따라 동적으로 너비 결정
        int maxWidth = 280;
        JLabel msgLabel = new JLabel("<html><body style='max-width:" + maxWidth + "px'>" + escapeHtml(text) + "</body></html>");
        msgLabel.setFont(new Font("Dialog", Font.PLAIN, 14));
        msgLabel.setForeground(isMe ? Color.WHITE : TEXT_PRIMARY);
        msgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubblePanel.add(msgLabel);

        wrapper.add(bubblePanel);

        // BoxLayout에서 높이가 늘어나지 않도록 설정
        Dimension prefSize = wrapper.getPreferredSize();
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, prefSize.height + 10));

        return wrapper;
    }

    // 시스템 메시지
    private JPanel createSystemMessage(String text) {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(5, 0, 5, 0));

        JLabel label = new JLabel(text);
        label.setFont(new Font("Dialog", Font.PLAIN, 12));
        label.setForeground(SYSTEM_COLOR);
        wrapper.add(label);

        Dimension prefSize = wrapper.getPreferredSize();
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, prefSize.height + 10));

        return wrapper;
    }

    // 이미지 버블
    private JPanel createImageBubble(String sender, ImageIcon icon, boolean isMe) {
        JPanel wrapper = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(3, 5, 3, 5));

        JPanel bubblePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isMe ? MY_BUBBLE : OTHER_BUBBLE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubblePanel.setLayout(new BoxLayout(bubblePanel, BoxLayout.Y_AXIS));
        bubblePanel.setOpaque(false);
        bubblePanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        if (!isMe) {
            JLabel nameLabel = new JLabel(sender);
            nameLabel.setFont(new Font("Dialog", Font.BOLD, 11));
            nameLabel.setForeground(new Color(75, 85, 99));
            nameLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubblePanel.add(nameLabel);
        }

        // 이미지 크기 조절
        if (icon.getIconWidth() > 300) {
            Image img = icon.getImage();
            Image scaledImg = img.getScaledInstance(300, -1, Image.SCALE_SMOOTH);
            icon = new ImageIcon(scaledImg);
        }

        JLabel imgLabel = new JLabel(icon);
        imgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubblePanel.add(imgLabel);

        wrapper.add(bubblePanel);

        Dimension prefSize = wrapper.getPreferredSize();
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, prefSize.height + 10));

        return wrapper;
    }

    // 파일 버블
    private JPanel createFileBubble(String sender, String fileName, byte[] fileData, long fileSize, boolean isMe) {
        JPanel wrapper = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(3, 5, 3, 5));

        JPanel bubblePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isMe ? MY_BUBBLE : OTHER_BUBBLE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubblePanel.setLayout(new BoxLayout(bubblePanel, BoxLayout.Y_AXIS));
        bubblePanel.setOpaque(false);
        bubblePanel.setBorder(new EmptyBorder(10, 14, 10, 14));

        if (!isMe) {
            JLabel nameLabel = new JLabel(sender);
            nameLabel.setFont(new Font("Dialog", Font.BOLD, 11));
            nameLabel.setForeground(new Color(75, 85, 99));
            nameLabel.setBorder(new EmptyBorder(0, 0, 6, 0));
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubblePanel.add(nameLabel);
        }

        // 파일 아이콘 결정
        String icon = getFileIcon(fileName);

        // 파일 정보 패널
        JPanel fileInfoPanel = new JPanel(new BorderLayout(8, 0));
        fileInfoPanel.setOpaque(false);
        fileInfoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Dialog", Font.PLAIN, 24));
        fileInfoPanel.add(iconLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel fileNameLabel = new JLabel(fileName);
        fileNameLabel.setFont(new Font("Dialog", Font.BOLD, 13));
        fileNameLabel.setForeground(isMe ? Color.WHITE : TEXT_PRIMARY);
        textPanel.add(fileNameLabel);

        JLabel fileSizeLabel = new JLabel(formatFileSize(fileSize));
        fileSizeLabel.setFont(new Font("Dialog", Font.PLAIN, 11));
        fileSizeLabel.setForeground(isMe ? new Color(220, 255, 220) : SYSTEM_COLOR);
        textPanel.add(fileSizeLabel);

        fileInfoPanel.add(textPanel, BorderLayout.CENTER);
        bubblePanel.add(fileInfoPanel);

        // 저장 버튼
        JButton saveBtn = new JButton("저장");
        saveBtn.setFont(new Font("Dialog", Font.BOLD, 11));
        saveBtn.setForeground(isMe ? PRIMARY : Color.WHITE);
        saveBtn.setBackground(isMe ? Color.WHITE : PRIMARY);
        saveBtn.setFocusPainted(false);
        saveBtn.setBorderPainted(false);
        saveBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        saveBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveBtn.setMargin(new Insets(4, 12, 4, 12));

        saveBtn.addActionListener(e -> saveFile(fileName, fileData));

        JPanel btnWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        btnWrapper.setOpaque(false);
        btnWrapper.setBorder(new EmptyBorder(8, 0, 0, 0));
        btnWrapper.add(saveBtn);
        btnWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubblePanel.add(btnWrapper);

        wrapper.add(bubblePanel);

        Dimension prefSize = wrapper.getPreferredSize();
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, prefSize.height + 10));

        return wrapper;
    }

    private String getFileIcon(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "📄";
        if (lower.endsWith(".txt") || lower.endsWith(".md")) return "📝";
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) return "🗜️";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "📘";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "📊";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "📙";
        if (lower.endsWith(".mp3") || lower.endsWith(".wav")) return "🎵";
        if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov")) return "🎬";
        if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js")) return "💻";
        return "📁";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private void saveFile(String fileName, byte[] fileData) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("파일 저장");
        chooser.setSelectedFile(new File(fileName));

        int ret = chooser.showSaveDialog(this);
        if (ret != JFileChooser.APPROVE_OPTION) return;

        try {
            File file = chooser.getSelectedFile();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(fileData);
            }
            appendBubble(createSystemMessage("파일 저장 완료: " + file.getName()));
        } catch (Exception ex) {
            appendBubble(createSystemMessage("파일 저장 실패: " + ex.getMessage()));
        }
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    private void appendBubble(JPanel bubble) {
        SwingUtilities.invokeLater(() -> {
            chatPanel.add(bubble);
            chatPanel.revalidate();
            chatPanel.repaint();

            // 스크롤을 맨 아래로
            SwingUtilities.invokeLater(() -> {
                JScrollBar vertical = chatScroll.getVerticalScrollBar();
                vertical.setValue(vertical.getMaximum());
            });
        });
    }

    private void sendCurrentMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        try {
            client.send(Message.chat(roomName, client.getNickname(), text));
            inputField.setText("");
        } catch (Exception e) {
            appendBubble(createSystemMessage("메시지 전송 실패: " + e.getMessage()));
        }
    }

    private void selectAndSendFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("파일 선택");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int ret = chooser.showOpenDialog(this);
        if (ret != JFileChooser.APPROVE_OPTION) return;

        try {
            File file = chooser.getSelectedFile();
            String fileName = file.getName().toLowerCase();

            // 파일 크기 제한 (10MB)
            if (file.length() > 10 * 1024 * 1024) {
                appendBubble(createSystemMessage("파일 크기는 10MB 이하만 전송 가능합니다."));
                return;
            }

            // 이미지 파일인 경우
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                    fileName.endsWith(".png") || fileName.endsWith(".gif")) {
                ImageIcon icon = new ImageIcon(file.getAbsolutePath());
                client.send(Message.sendImage(roomName, client.getNickname(), icon));
            } else {
                // 일반 파일인 경우
                byte[] fileData = Files.readAllBytes(file.toPath());
                client.send(Message.sendFile(roomName, client.getNickname(), file.getName(), fileData));
            }
        } catch (Exception ex) {
            appendBubble(createSystemMessage("파일 전송 실패: " + ex.getMessage()));
        }
    }

    private void handleServerMessage(Message m) {
        switch (m.getType()) {
            case CHAT -> {
                if (roomName.equals(m.getRoom())) {
                    boolean isMe = client.getNickname().equals(m.getSender());
                    appendBubble(createMessageBubble(m.getSender(), m.getText(), isMe));
                }
            }
            case SYSTEM -> {
                String room = m.getRoom();
                if (room == null || roomName.equals(room)) {
                    appendBubble(createSystemMessage(m.getText()));
                }
            }
            case ERROR -> appendBubble(createSystemMessage("[오류] " + m.getText()));
            case USER_LIST -> {
                if (roomName.equals(m.getRoom())) {
                    updateUserList(m);
                }
            }
            case IMAGE -> {
                if (roomName.equals(m.getRoom())) {
                    boolean isMe = client.getNickname().equals(m.getSender());
                    appendBubble(createImageBubble(m.getSender(), m.getImage(), isMe));
                }
            }
            case FILE -> {
                if (roomName.equals(m.getRoom())) {
                    boolean isMe = client.getNickname().equals(m.getSender());
                    appendBubble(createFileBubble(m.getSender(), m.getFileName(), m.getFileData(), m.getFileSize(), isMe));
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
        SwingUtilities.invokeLater(() -> {
            if (!active) return;
            if (m.getGameAction() == Message.GameAction.ERROR) {
                appendBubble(createSystemMessage("[게임] " + m.getText()));
                return;
            }

            Message.GameType type = m.getGameType() == null ? Message.GameType.OMOK : m.getGameType();
            if (type == Message.GameType.WORD) {
                handleWordState(m);
                return;
            }

            if (m.getGameAction() == Message.GameAction.STATE) {
                boolean iAmPlayer = client.getNickname().equals(m.getBlackPlayer()) || client.getNickname().equals(m.getWhitePlayer());
                boolean shouldOpen = iAmPlayer || requestedOmokWindow;
                if (!shouldOpen) return;
                ensureOmokWindow();
                omokWindow.applyState(m);
                if (!omokWindow.isVisible()) {
                    omokWindow.setVisible(true);
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
        }
    }

    private void ensureOmokWindow() {
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
        requestedOmokWindow = false;
    }

    private void onExitWord() {
        requestedWordWindow = false;
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
            appendBubble(createSystemMessage("[게임] 참여 실패: " + e.getMessage()));
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

    // 유저 목록 셀 렌더러
    private class UserListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(6, 8, 6, 8));
            label.setOpaque(true);

            if (isSelected) {
                label.setBackground(new Color(240, 253, 244));
                label.setForeground(PRIMARY);
            } else {
                label.setBackground(CARD_BG);
                label.setForeground(TEXT_PRIMARY);
            }

            // 본인 표시
            if (value != null && value.toString().equals(client.getNickname())) {
                label.setText(value + " (나)");
                label.setForeground(PRIMARY);
            }

            return label;
        }
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
            System.out.println("LEAVE ROOM 전송실패: " + e.getMessage());
        }
        requestedOmokWindow = false;
        requestedWordWindow = false;

        if (parentList != null) {
            parentList.setVisible(true);
            parentList.requestRoomList();
        }
        super.dispose();
    }
}
