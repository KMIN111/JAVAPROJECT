import chat.shared.Message;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * 게임 선택 다이얼로그
 * 게임 이미지와 이름을 카드 형태로 표시하고, 클릭하면 해당 게임 선택
 */
public class GameSelectionDialog extends JDialog {

    // 게임 정보 (이름, 타입, 이미지 경로)
    private static final GameInfo[] GAMES = {
            new GameInfo("오목", Message.GameType.OMOK, null),
            new GameInfo("단어 맞추기", Message.GameType.WORD, null),
            // 추후 게임 추가 시 여기에 추가
            // new GameInfo("틱택토", Message.GameType.TICTACTOE, null),
    };

    private Message.GameType selectedGame = null;
    private final Consumer<Message.GameType> onGameSelected;

    public GameSelectionDialog(JFrame parent, Consumer<Message.GameType> onGameSelected) {
        super(parent, "게임 선택", true);  // 모달
        this.onGameSelected = onGameSelected;

        setSize(400, 300);
        setLocationRelativeTo(parent);
        setResizable(false);

        initUI();
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(new EmptyBorder(20, 20, 20, 20));
        main.setBackground(new Color(245, 245, 245));

        // 타이틀
        JLabel titleLabel = new JLabel("참여할 게임을 선택하세요", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Dialog", Font.BOLD, 18));
        titleLabel.setBorder(new EmptyBorder(0, 0, 15, 0));
        main.add(titleLabel, BorderLayout.NORTH);

        // 게임 카드들
        JPanel cardPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        cardPanel.setOpaque(false);

        for (GameInfo game : GAMES) {
            JPanel card = createGameCard(game);
            cardPanel.add(card);
        }

        main.add(cardPanel, BorderLayout.CENTER);

        // 취소 버튼
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setOpaque(false);

        JButton cancelBtn = new JButton("취소");
        cancelBtn.setPreferredSize(new Dimension(100, 35));
        cancelBtn.addActionListener(e -> dispose());
        bottomPanel.add(cancelBtn);

        main.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(main);
    }

    private JPanel createGameCard(GameInfo game) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                new EmptyBorder(15, 20, 15, 20)
        ));
        card.setPreferredSize(new Dimension(120, 140));
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 게임 아이콘 (이미지가 없으면 기본 아이콘)
        JLabel iconLabel = new JLabel();
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        if (game.imagePath != null) {
            ImageIcon icon = new ImageIcon(game.imagePath);
            Image scaled = icon.getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH);
            iconLabel.setIcon(new ImageIcon(scaled));
        } else {
            // 기본 게임 아이콘 (텍스트로 대체)
            iconLabel.setText(getGameEmoji(game.type));
            iconLabel.setFont(new Font("Dialog", Font.PLAIN, 40));
        }

        // 게임 이름
        JLabel nameLabel = new JLabel(game.name);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setFont(new Font("Dialog", Font.BOLD, 14));
        nameLabel.setBorder(new EmptyBorder(10, 0, 0, 0));

        card.add(Box.createVerticalGlue());
        card.add(iconLabel);
        card.add(nameLabel);
        card.add(Box.createVerticalGlue());

        // 호버 효과 & 클릭 이벤트
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBackground(new Color(230, 245, 255));
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(100, 150, 255), 2),
                        new EmptyBorder(14, 19, 14, 19)
                ));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBackground(Color.WHITE);
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                        new EmptyBorder(15, 20, 15, 20)
                ));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                selectedGame = game.type;
                dispose();
                if (onGameSelected != null) {
                    onGameSelected.accept(selectedGame);
                }
            }
        });

        return card;
    }

    private String getGameEmoji(Message.GameType type) {
        return switch (type) {
            case OMOK -> "⚫";
            case WORD -> "⌨";
            // 추후 게임 추가 시
            // case TICTACTOE -> "❌";
        };
    }

    public Message.GameType getSelectedGame() {
        return selectedGame;
    }

    // 게임 정보 클래스
    private static class GameInfo {
        String name;
        Message.GameType type;
        String imagePath;

        GameInfo(String name, Message.GameType type, String imagePath) {
            this.name = name;
            this.type = type;
            this.imagePath = imagePath;
        }
    }
}
