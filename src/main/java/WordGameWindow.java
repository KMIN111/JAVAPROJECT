import chat.shared.Message;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 단어 맞추기 게임 화면.
 * 서버 STATE 메시지를 받아 단어 배치, 점수, 타이머를 동기화한다.
 */
public class WordGameWindow extends JFrame {

    private final ChatClient client;
    private final String roomName;
    private final String nickname;
    private final Runnable onExit;

    private final WordBoardPanel boardPanel = new WordBoardPanel();
    private final DefaultListModel<String> playerModel = new DefaultListModel<>();
    private final DefaultListModel<String> spectatorModel = new DefaultListModel<>();
    private final JTextField inputField = new JTextField();
    private final JLabel timerLabel = new JLabel("남은 시간: 0초");
    private final JLabel infoLabel = new JLabel("대기 중");
    private final JButton joinButton = new JButton("단어 맞추기");
    private final JButton startButton = new JButton("새 라운드 시작");
    private final JButton submitButton = new JButton("입력");
    private final JButton exitButton = new JButton("게임 나가기");

    private boolean lastFinishedNotified = false;
    private String lastWinner = null;

    public WordGameWindow(ChatClient client, String roomName, String nickname, Runnable onExit) {
        this.client = client;
        this.roomName = roomName;
        this.nickname = nickname;
        this.onExit = onExit;

        setTitle("단어 맞추기 - " + roomName);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        initUI();
        pack();
        setLocationRelativeTo(null);
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 상단 컨트롤
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(joinButton);
        top.add(startButton);
        top.add(timerLabel);
        root.add(top, BorderLayout.NORTH);

        // 중앙: 단어 패널 + 우측 정보
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(boardPanel, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.setPreferredSize(new Dimension(200, 0));

        JLabel playersTitle = new JLabel("참가자 점수");
        playersTitle.setFont(playersTitle.getFont().deriveFont(Font.BOLD));
        right.add(playersTitle, BorderLayout.NORTH);

        JList<String> playerList = new JList<>(playerModel);
        playerList.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        right.add(new JScrollPane(playerList), BorderLayout.CENTER);

        JPanel spectPanel = new JPanel(new BorderLayout());
        spectPanel.setBorder(BorderFactory.createTitledBorder("관전자"));
        JList<String> spectatorList = new JList<>(spectatorModel);
        spectPanel.add(new JScrollPane(spectatorList), BorderLayout.CENTER);
        right.add(spectPanel, BorderLayout.SOUTH);

        center.add(right, BorderLayout.EAST);
        root.add(center, BorderLayout.CENTER);

        // 하단: 입력 + 안내 + 종료 버튼
        JPanel bottom = new JPanel(new BorderLayout(6, 4));
        bottom.add(infoLabel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(submitButton, BorderLayout.EAST);
        bottom.add(inputPanel, BorderLayout.CENTER);

        JPanel bottomRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomRight.add(exitButton);
        bottom.add(bottomRight, BorderLayout.SOUTH);

        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        // 액션 바인딩
        joinButton.addActionListener(this::onJoin);
        startButton.addActionListener(this::onStartRound);
        submitButton.addActionListener(this::onSubmitWord);
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    onSubmitWord(null);
                }
            }
        });
        exitButton.addActionListener(e -> {
            try {
                client.send(Message.gameLeave(roomName));
            } catch (Exception ex) {
                // ignore send failure
            }
            dispose();
            if (onExit != null) onExit.run();
        });
    }

    private void onJoin(ActionEvent e) {
        try {
            client.send(Message.wordJoin(roomName));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "참여 요청 실패: " + ex.getMessage());
        }
    }

    private void onStartRound(ActionEvent e) {
        try {
            client.send(Message.wordStartRound(roomName));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "라운드 시작 실패: " + ex.getMessage());
        }
    }

    private void onSubmitWord(ActionEvent e) {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        try {
            client.send(Message.wordSubmit(roomName, text));
            inputField.setText("");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "단어 전송 실패: " + ex.getMessage());
        }
    }

    public void applyState(Message m) {
        List<Message.WordItem> items = m.getWordItems();
        if (items == null) items = Collections.emptyList();
        boardPanel.setItems(items);

        // 점수/플레이어 목록
        playerModel.clear();
        Map<String, Integer> scores = m.getScores();
        String host = m.getHostPlayer();
        boolean isHost = host != null && host.equals(nickname);
        if (scores != null) {
            scores.forEach((name, score) -> {
                String label = name + " : " + score + "점";
                if (name.equals(host)) label += " (방장)";
                if (name.equals(nickname)) label += " (나)";
                playerModel.addElement(label);
            });
        } else if (m.getPlayers() != null) {
            for (String p : m.getPlayers()) {
                String label = p + " : 0점";
                if (p.equals(host)) label += " (방장)";
                if (p.equals(nickname)) label += " (나)";
                playerModel.addElement(label);
            }
        }

        spectatorModel.clear();
        if (m.getSpectators() != null) {
            for (String s : m.getSpectators()) spectatorModel.addElement(s);
        }

        timerLabel.setText("남은 시간: " + m.getRemainingSeconds() + "초");
        infoLabel.setText(m.getInfoText() == null ? "" : m.getInfoText());

        // 방장만 시작 버튼 활성화
        startButton.setEnabled(isHost && !m.isRunning());
        joinButton.setEnabled(!isHost); // 방장은 이미 참여중이므로 비활성화

        // 결과 알림
        if (!m.isRunning() && m.getWinner() != null) {
            if (!lastFinishedNotified || !m.getWinner().equals(lastWinner)) {
                lastFinishedNotified = true;
                lastWinner = m.getWinner();
                JOptionPane.showMessageDialog(this,
                        m.getWinner() + " 님이 게임의 승리자 입니다",
                        "결과",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } else if (m.isRunning()) {
            lastFinishedNotified = false;
            lastWinner = null;
        }

        repaint();
    }

    private static class WordBoardPanel extends JPanel {
        private static final Dimension PREF = new Dimension(560, 560);
        private List<Message.WordItem> items = new ArrayList<>();

        WordBoardPanel() {
            setPreferredSize(PREF);
            setBackground(new Color(245, 245, 255));
        }

        void setItems(List<Message.WordItem> items) {
            this.items = new ArrayList<>(items);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            for (Message.WordItem item : items) {
                int x = item.getX();
                int y = item.getY();
                boolean answered = item.getAnsweredBy() != null;
                g2.setColor(answered ? Color.GRAY : Color.BLUE);
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, answered ? 14f : 16f));
                g2.drawString(item.getText(), x, y);
                if (answered) {
                    g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
                    g2.drawString("by " + item.getAnsweredBy(), x, y + 14);
                }
            }
            g2.dispose();
        }
    }
}

