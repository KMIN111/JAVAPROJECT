import chat.shared.Message;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * 오목 게임 뷰 + 간단한 컨트롤러.
 * 서버 상태 스냅샷(Message.GAME_EVENT/STATE)을 받아 보드를 갱신한다.
 */
public class OmokWindow extends JFrame {

    private final ChatClient client;
    private final String roomName;
    @SuppressWarnings("FieldCanBeLocal")
    private final Runnable onExit;

    private final OmokBoardPanel boardPanel;
    private final JLabel infoLabel;
    private final JButton resignButton;
    private final JButton restartButton;
    private final JButton exitButton;
    private final JLabel blackLabel;
    private final JLabel whiteLabel;
    private final DefaultListModel<String> spectatorModel;
    private final JList<String> spectatorList;
    private final Set<String> prevSpectators = new HashSet<>();

    private int myStone = 0; // 0 관전, 1 흑, 2 백
    private String myNickname;
    private String currentTurn;
    private boolean finished;
    private boolean lastFinishedNotified = false;
    private String lastResultReason = null;
    private String lastWinner = null;

    public OmokWindow(ChatClient client, String roomName, String nickname, Runnable onExit) {
        this.client = client;
        this.roomName = roomName;
        this.myNickname = nickname;
        this.onExit = onExit;

        setTitle("오목 - " + roomName);
        setLocationRelativeTo(null);

        boardPanel = new OmokBoardPanel(this::onCellClicked);
        infoLabel = new JLabel("대기 중", SwingConstants.CENTER);
        resignButton = new JButton("기권");
        restartButton = new JButton("다시하기");
        exitButton = new JButton("게임 나가기");
        blackLabel = new JLabel("흑: -");
        whiteLabel = new JLabel("백: -");
        spectatorModel = new DefaultListModel<>();
        spectatorList = new JList<>(spectatorModel);

        resignButton.addActionListener(e -> {
            if (!finished && myStone != 0) {
                try {
                    client.send(Message.gameResign(roomName));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "기권 전송 실패: " + ex.getMessage());
                }
            }
        });

        restartButton.addActionListener(e -> {
            if (finished && myStone != 0) {
                try {
                    client.send(Message.gameRestart(roomName));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "다시하기 전송 실패: " + ex.getMessage());
                }
            }
        });

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.add(infoLabel, BorderLayout.CENTER);
        JPanel bottomButtons = new JPanel(new GridLayout(1, 3, 5, 0));
        bottomButtons.add(resignButton);
        bottomButtons.add(restartButton);
        bottomButtons.add(exitButton);
        bottom.add(bottomButtons, BorderLayout.EAST);

        exitButton.addActionListener(e -> {
            try {
                client.send(Message.gameLeave(roomName));
            } catch (Exception ex) {
                // 무시하고 창만 닫음
            }
            dispose();
            if (this.onExit != null) this.onExit.run();
        });

        JPanel side = new JPanel(new BorderLayout(8, 8));
        side.setPreferredSize(new Dimension(150, 0)); // 사이드 패널 너비 고정
        JPanel playersPanel = new JPanel(new GridLayout(2, 1));
        playersPanel.add(blackLabel);
        playersPanel.add(whiteLabel);
        side.add(playersPanel, BorderLayout.NORTH);

        spectatorList.setBorder(BorderFactory.createTitledBorder("관전자"));
        side.add(new JScrollPane(spectatorList), BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(boardPanel, BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        pack();
        // 보드가 줄어들지 않도록 최소 크기 확보 (보드 가로 + 사이드 약간)
        int minWidth = boardPanel.getPreferredSize().width + 180;
        int minHeight = boardPanel.getPreferredSize().height + 120;
        setMinimumSize(new Dimension(minWidth, minHeight));

        // 초기 버튼 상태 설정
        resignButton.setEnabled(false);
        restartButton.setEnabled(false);
    }

    private void onCellClicked(int x, int y) {
        if (finished) return;
        if (myStone == 0) return; // 관전
        if (!myNickname.equals(currentTurn)) return;
        try {
            client.send(Message.gameMove(roomName, x, y));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "수 전송 실패: " + e.getMessage());
        }
    }

    /**
     * 서버에서 받은 STATE 메시지를 화면과 로컬 상태에 반영한다.
     */
    public void applyState(Message m) {
        int[][] board = m.getBoard();
        boardPanel.setBoard(board);

        // 내 색상 계산
        if (m.getBlackPlayer() != null && m.getBlackPlayer().equals(myNickname)) {
            myStone = 1;
        } else if (m.getWhitePlayer() != null && m.getWhitePlayer().equals(myNickname)) {
            myStone = 2;
        } else {
            myStone = 0;
        }

        currentTurn = m.getCurrentTurn();
        finished = m.isFinished();
        blackLabel.setText("흑: " + (m.getBlackPlayer() == null ? "-" : m.getBlackPlayer()));
        whiteLabel.setText("백: " + (m.getWhitePlayer() == null ? "-" : m.getWhitePlayer()));

        spectatorModel.clear();
        if (m.getSpectators() != null) {
            for (String s : m.getSpectators()) {
                spectatorModel.addElement(s);
            }
        }

        // 새로 들어온 관전자 팝업 (본인은 제외)
        if (m.getSpectators() != null) {
            Set<String> current = new HashSet<>(m.getSpectators());
            current.remove(myNickname); // 본인 제외
            Set<String> newlyJoined = new HashSet<>(current);
            newlyJoined.removeAll(prevSpectators);
            if (!newlyJoined.isEmpty() && this.isVisible()) {
                String names = String.join(", ", newlyJoined);
                showInfo(names + " 님이 관전을 시작했습니다.");
            }
            prevSpectators.clear();
            prevSpectators.addAll(current);
        } else {
            prevSpectators.clear();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("흑: ").append(m.getBlackPlayer() == null ? "-" : m.getBlackPlayer());
        sb.append(" / 백: ").append(m.getWhitePlayer() == null ? "-" : m.getWhitePlayer());
        sb.append(" | 현재 턴: ").append(currentTurn == null ? "-" : currentTurn);
        if (finished) {
            sb.append(" | 결과: ");
            if (m.getWinner() == null) sb.append("무승부");
            else sb.append(m.getWinner()).append(" 승");
            if (m.getResultReason() != null) sb.append(" (").append(m.getResultReason()).append(")");
        }

        infoLabel.setText(sb.toString());
        boardPanel.setEnabled(!finished);
        boardPanel.repaint();

        // 버튼 상태 업데이트
        resignButton.setEnabled(!finished && myStone != 0);
        restartButton.setEnabled(finished && myStone != 0);

        maybeNotifyResult(m);
    }

    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "오목 안내", JOptionPane.INFORMATION_MESSAGE);
    }

    private void maybeNotifyResult(Message m) {
        if (!m.isFinished()) {
            lastFinishedNotified = false;
            lastResultReason = null;
            lastWinner = null;
            return;
        }

        // 이미 같은 결과를 알림했다면 스킵
        if (lastFinishedNotified &&
                ((lastWinner == null && m.getWinner() == null) ||
                        (lastWinner != null && lastWinner.equals(m.getWinner()))) &&
                ((lastResultReason == null && m.getResultReason() == null) ||
                        (lastResultReason != null && lastResultReason.equals(m.getResultReason())))) {
            return;
        }

        lastFinishedNotified = true;
        lastWinner = m.getWinner();
        lastResultReason = m.getResultReason();

        String msg;
        if (m.getWinner() == null) {
            msg = "무승부입니다.";
        } else {
            String reason = (m.getResultReason() != null && !m.getResultReason().isBlank())
                    ? " (" + m.getResultReason() + ")"
                    : "";
            msg = m.getWinner() + " 님이 승리했습니다" + reason;
        }

        JOptionPane.showMessageDialog(this, msg, "오목 결과", JOptionPane.INFORMATION_MESSAGE);
    }

    // 단순한 바둑판 패널
    private static class OmokBoardPanel extends JPanel {
        private static final int CELL = 32;
        private static final int PAD = 24;
        private int[][] board = new int[15][15];
        private final CellClickListener listener;

        OmokBoardPanel(CellClickListener listener) {
            this.listener = listener;
            setPreferredSize(new Dimension(PAD * 2 + CELL * 14, PAD * 2 + CELL * 14));
            setBackground(new Color(245, 222, 179));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int x = Math.round((e.getX() - PAD) / (float) CELL);
                    int y = Math.round((e.getY() - PAD) / (float) CELL);
                    if (x < 0 || y < 0 || x >= 15 || y >= 15) return;
                    OmokBoardPanel.this.listener.onClick(x, y);
                }
            });
        }

        public void setBoard(int[][] board) {
            this.board = board;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 격자
            g2.setColor(Color.DARK_GRAY);
            for (int i = 0; i < 15; i++) {
                int pos = PAD + i * CELL;
                g2.drawLine(PAD, pos, PAD + CELL * 14, pos);
                g2.drawLine(pos, PAD, pos, PAD + CELL * 14);
            }

            // 돌
            for (int x = 0; x < 15; x++) {
                for (int y = 0; y < 15; y++) {
                    int stone = board[x][y];
                    if (stone == 0) continue;
                    int cx = PAD + x * CELL;
                    int cy = PAD + y * CELL;
                    int r = CELL - 6;
                    g2.setColor(stone == 1 ? Color.BLACK : Color.WHITE);
                    g2.fillOval(cx - r / 2, cy - r / 2, r, r);
                    g2.setColor(Color.GRAY);
                    g2.drawOval(cx - r / 2, cy - r / 2, r, r);
                }
            }
        }
    }

    @FunctionalInterface
    interface CellClickListener {
        void onClick(int x, int y);
    }
}

