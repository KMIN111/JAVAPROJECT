import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LoginPage extends JFrame {

    private static final Color PRIMARY = new Color(34, 197, 94);        // 메인 초록 (#22C55E)
    private static final Color PRIMARY_HOVER = new Color(22, 163, 74);  // 진한 초록 (#16A34A)
    private static final Color BG_COLOR = new Color(240, 253, 244);     // 아주 연한 민트/연두 (#F0FDF4)
    private static final Color CARD_BG = Color.WHITE;                   // 카드 배경 흰색
    private static final Color TEXT_PRIMARY = new Color(22, 30, 46);    // 진한 남색 텍스트
    private static final Color INPUT_BORDER = new Color(187, 247, 208); // 연한 초록 테두리
    private static final Color INPUT_FOCUS = new Color(34, 197, 94);    // 포커스 초록

    private JTextField tfUserName;
    private JTextField tfHost;
    private JTextField tfPort;
    private JButton loginBtn;

    public LoginPage() {
        setTitle("Talk&Play");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 650);
        setLocationRelativeTo(null); // 화면 가운데
        setResizable(false); // 화면 크기 조정 불가능

        initUI();

        setVisible(true);
    }

    private void initUI() {
        // 메인 배경 패널
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG_COLOR);
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // 가운데 카드 패널
        JPanel card = createCard();

        mainPanel.add(card, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    // 로그인 카드 UI 구성
    private JPanel createCard() {
        String[] config = readServerConfig();
        String host = config[0];
        String port = config[1];

        JPanel card = new RoundedPanel(24);
        card.setBackground(CARD_BG);
        card.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 10, 0);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;

        // 타이틀
        JLabel title = new JLabel("Talk & Play");
        title.setFont(getTitleFont());
        title.setForeground(TEXT_PRIMARY);
        card.add(title, gbc);

        // 서브타이틀
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 35, 0);
        JLabel subtitle = new JLabel("멀티룸 채팅 & 미니게임");
        subtitle.setFont(getSmallFont());
        subtitle.setForeground(new Color(55, 65, 81));
        card.add(subtitle, gbc);

        // 입력 영역 (닉네임 / 호스트 / 포트)
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 20, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        tfUserName = createTextField();
        card.add(createInputGroup("닉네임", tfUserName), gbc);

        gbc.gridy++;
        tfHost = createTextField();
        tfHost.setText(host); // 기본값 예시
        card.add(createInputGroup("서버 주소", tfHost), gbc);

        gbc.gridy++;
        tfPort = createTextField();
        tfPort.setText(port); // 기본값 예시
        card.add(createInputGroup("포트", tfPort), gbc);


        // 로그인 버튼
        gbc.gridy++;
        gbc.insets = new Insets(25, 0, 15, 0);
        gbc.fill = GridBagConstraints.NONE;
        loginBtn = createLoginButton();
        card.add(loginBtn, gbc);

        return card;
    }

    // 라벨 + 텍스트필드 묶는 그룹
    private JPanel createInputGroup(String labelText, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(350, 80));
        panel.setPreferredSize(new Dimension(350, 80));

        JLabel label = new JLabel(labelText);
        label.setFont(getLabelFont());
        label.setForeground(TEXT_PRIMARY);
        label.setBorder(new EmptyBorder(0, 0, 6, 0)); // 라벨 아래 여백
        label.setHorizontalAlignment(SwingConstants.LEFT);

        // 위에는 라벨, 가운데는 텍스트 필드
        panel.add(label, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);

        return panel;
    }


    // 텍스트 필드 공통 스타일
    private JTextField createTextField() {
        JTextField tf = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                if (!isOpaque() && getBorder() instanceof RoundedBorder) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };

        tf.setFont(getInputFont());
        tf.setForeground(TEXT_PRIMARY);
        tf.setBackground(Color.WHITE);
        tf.setBorder(new RoundedBorder(12, INPUT_BORDER, INPUT_FOCUS));
        tf.setPreferredSize(new Dimension(350, 44));
        tf.setMaximumSize(new Dimension(350, 44));
        tf.setOpaque(false);
        tf.setCaretColor(TEXT_PRIMARY);

        return tf;
    }

    // 로그인 버튼
    private JButton createLoginButton() {
        JButton btn = new JButton("입장하기") {
            private boolean hover = false;

            {
                setFocusPainted(false);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setCursor(new Cursor(Cursor.HAND_CURSOR));

                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        if (isEnabled()) {
                            hover = true;
                            repaint();
                        }
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

                if (isEnabled()) {
                    g2.setColor(hover ? PRIMARY_HOVER : PRIMARY);
                } else {
                    g2.setColor(new Color(209, 213, 219)); // 비활성 회색
                }

                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();

                super.paintComponent(g);
            }
        };

        btn.setFont(getButtonFont());
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(350, 48));
        btn.setMaximumSize(new Dimension(350, 48));

        btn.setEnabled(true);

        // 클릭 시 동작
        btn.addActionListener(e -> onLogin());

        return btn;
    }

    // 로그인 버튼 눌렸을 때
    private void onLogin() {
        String name = tfUserName.getText().trim();
        String h = tfHost.getText().trim();
        String p = tfPort.getText().trim();

        // 닉네임 검사
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "닉네임을 입력해주세요.",
                    "입력 오류",
                    JOptionPane.WARNING_MESSAGE
            );
            tfUserName.requestFocus();
            return;
        }
        // 서버 주소 검사
        if (h.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "서버 주소를 입력해주세요",
                    "입력 오류",
                    JOptionPane.WARNING_MESSAGE
            );
            tfHost.requestFocus();
            return;
        }
        // 포트 번호 검사
        int port;
        try {
            port = Integer.parseInt(p);
            if (port <= 0 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "유효한 포트 번호(1~65535)를 입력해주세요.",
                    "입력 오류",
                    JOptionPane.WARNING_MESSAGE
            );
            tfPort.requestFocus();
            return;
        }

        try {
            ChatClient client = new ChatClient();
            client.connect(h, port, name);   // 소켓 연결 + 닉네임 전송

            RoomListFrame rooms = new RoomListFrame(client, h, port);
            rooms.setVisible(true);
            dispose();  // 로그인 창 닫기

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "서버 연결 실패:\n" + ex.getMessage(),
                    "연결 오류",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }


    private Font getTitleFont() {
        return new Font("Dialog", Font.BOLD, 32);
    }

    private Font getLabelFont() {
        return new Font("Dialog", Font.BOLD, 13);
    }

    private Font getInputFont() {
        return new Font("Dialog", Font.PLAIN, 14);
    }

    private Font getButtonFont() {
        return new Font("Dialog", Font.BOLD, 15);
    }

    private Font getSmallFont() {
        return new Font("Dialog", Font.PLAIN, 11);
    }

    static class RoundedPanel extends JPanel {
        private final int radius;

        RoundedPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class RoundedBorder extends EmptyBorder {
        private final int radius;
        private final Color normalColor;
        private final Color focusColor;

        RoundedBorder(int radius, Color normalColor, Color focusColor) {
            super(10, 14, 10, 14); // 내부 여백
            this.radius = radius;
            this.normalColor = normalColor;
            this.focusColor = focusColor;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            boolean focused = (c instanceof JTextField) && ((JTextField) c).hasFocus();
            g2.setColor(focused ? focusColor : normalColor);
            g2.setStroke(new BasicStroke(focused ? 2f : 1f));

            g2.drawRoundRect(x + 1, y + 1, width - 3, height - 3, radius, radius);
            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginPage::new);
    }

    private static String[] readServerConfig() {
        String host = "localhost"; // 기본값
        int port = 6000;         // 기본값

        try (
                InputStream is = LoginPage.class.getClassLoader().getResourceAsStream("server.txt");
        ) {
            if (is == null) {
                System.err.println("경고: server.txt 를 클래스패스에서 찾을 수 없습니다. 기본값 (localhost:6000)을 사용합니다.");
                return new String[]{host, String.valueOf(port)};
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {

                String line1 = br.readLine();   // 첫 번째 줄: IP
                String line2 = br.readLine();   // 두 번째 줄: 포트

                if (line1 == null || line2 == null) {
                    System.err.println("경고: server.txt 형식이 잘못되었습니다. (두 줄 필요). 기본값 (localhost:6000)을 사용합니다.");
                } else {
                    host = line1.trim();
                    port = Integer.parseInt(line2.trim());
                }
            }

        } catch (IOException e) {
            System.err.println("경고: server.txt 를 읽는 중 IO 오류가 발생했습니다. 기본값 (localhost:6000)을 사용합니다." + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("경고: server.txt 두 번째 줄(포트 번호)이 숫자가 아닙니다. 기본값 (localhost:6000)을 사용합니다." + e.getMessage());
        }
        return new String[]{host, String.valueOf(port)};
    }
}
