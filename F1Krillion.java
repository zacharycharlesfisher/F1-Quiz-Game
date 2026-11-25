import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class F1Krillion extends JFrame {

    // Color Palette (F1 Carbon, Asphalt & Racing Red)
    private static final Color BG_ASPHALT = new Color(16, 18, 24);
    private static final Color CARD_BG = new Color(26, 29, 38, 232);
    private static final Color F1_RED = new Color(225, 6, 0);
    private static final Color TEXT_LIGHT = new Color(245, 247, 250);
    private static final Color TEXT_MUTED = new Color(156, 163, 175);
    private static final Color TIER_COMMON = new Color(156, 163, 175);     // Hard Tire
    private static final Color TIER_UNCOMMON = new Color(250, 204, 21);    // Medium Tire
    private static final Color TIER_RARE = new Color(168, 85, 247);        // Purple Sector
    private static final Color TIER_KRILLION = new Color(34, 197, 94);     // Grand Slam

    private static final int TIME_PER_PROMPT = 25;
    private static final int QUESTIONS_PER_RACE = 7;
    private static final int MAX_LAPS_PER_PROMPT = 10; // 7 * 10 = 70-Lap Grand Prix

    private List<Prompt> promptPool;
    private List<Prompt> activePrompts;
    private List<RoundResult> results;
    private int currentIndex = 0;
    private int totalLaps = 0;
    private int timeLeft = TIME_PER_PROMPT;

    private javax.swing.Timer countdownTimer;
    private javax.swing.Timer visualTimer;

    // UI Components
    private CardLayout cardLayout;
    private TrackBackgroundPanel mainPanel;

    private JLabel apiStatusLabel;
    private JLabel progressLabel;
    private JLabel lapsLabel;
    private JLabel timerLabel;
    private JProgressBar timerBar;
    private F1LightsPanel f1LightsPanel;
    private LapGaugePanel lapGaugePanel;
    private JLabel promptLabel;
    private JTextField answerField;
    private JButton submitButton;
    private JLabel feedbackLabel;
    private JButton didYouMeanButton;
    private JPanel summaryListPanel;
    private JLabel finalScoreLabel;

    public F1Krillion() {
        setTitle("F1 Krillion — Grand Prix Challenge");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(840, 600);
        setLocationRelativeTo(null);
        setResizable(false);

        cardLayout = new CardLayout();
        mainPanel = new TrackBackgroundPanel(cardLayout);

        mainPanel.add(createStartPanel(), "START");
        mainPanel.add(createGamePanel(), "GAME");
        mainPanel.add(createSummaryPanel(), "SUMMARY");

        add(mainPanel);
        promptPool = buildF1PromptPool();
        activePrompts = new ArrayList<>();
        results = new ArrayList<>();

        // Sync live F1 history from Jolpica API in the background
        syncJolpicaApiInBackground();

        // 30 FPS animation timer for speed lines & smooth lap gauge
        visualTimer = new javax.swing.Timer(33, e -> {
            mainPanel.stepAnimation();
            if (lapGaugePanel != null) {
                lapGaugePanel.setTargetLaps(totalLaps, QUESTIONS_PER_RACE * MAX_LAPS_PER_PROMPT);
            }
        });
        visualTimer.start();
    }

    private JPanel createStartPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(40, 60, 40, 60));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;

        JLabel title = new JLabel("F1 KRILLION", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 54));
        title.setForeground(F1_RED);
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 6, 0);
        panel.add(title, gbc);

        JLabel subtitle = new JLabel("THE FORMULA ONE GRAND PRIX CHALLENGE", SwingConstants.CENTER);
        subtitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        subtitle.setForeground(TIER_UNCOMMON);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 32, 0);
        panel.add(subtitle, gbc);

        JLabel desc = new JLabel("<html><div style='text-align: center; width: 490px;'>"
                + "7 random F1 prompts per race. 25 seconds each.<br><br>"
                + "Obvious answers earn fewer laps.<br>"
                + "Rare deep cuts earn a <b>Grand Slam (+10 Laps)</b> toward a full <b>70-Lap Race</b>.<br><br>"
                + "<span style='color: #9ca3af;'>Wrong guesses don't end the round — keep guessing until you hit!</span>"
                + "</div></html>", SwingConstants.CENTER);
        desc.setFont(new Font("SansSerif", Font.PLAIN, 16));
        desc.setForeground(TEXT_LIGHT);
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 36, 0);
        panel.add(desc, gbc);

        JButton startBtn = createStyledButton("LIGHTS OUT", F1_RED);
        startBtn.addActionListener(e -> startGame());
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 18, 0);
        panel.add(startBtn, gbc);

        apiStatusLabel = new JLabel("Syncing full F1 history from Jolpica API...", SwingConstants.CENTER);
        apiStatusLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        apiStatusLabel.setForeground(TEXT_MUTED);
        gbc.gridy = 4;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(apiStatusLabel, gbc);

        return panel;
    }

    private JPanel createGamePanel() {
        JPanel panel = new JPanel(new BorderLayout(20, 18));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(25, 34, 30, 38));

        // Top HUD
        JPanel hud = new JPanel(new BorderLayout());
        hud.setOpaque(false);

        progressLabel = new JLabel("SECTOR 1 / 7");
        progressLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        progressLabel.setForeground(TEXT_MUTED);

        f1LightsPanel = new F1LightsPanel();

        lapsLabel = new JLabel("LAPS: 0 / 70");
        lapsLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        lapsLabel.setForeground(TIER_UNCOMMON);

        timerLabel = new JLabel("25s");
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        timerLabel.setForeground(TEXT_LIGHT);

        JPanel rightHud = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        rightHud.setOpaque(false);
        rightHud.add(lapsLabel);
        rightHud.add(timerLabel);

        hud.add(progressLabel, BorderLayout.WEST);
        hud.add(f1LightsPanel, BorderLayout.CENTER);
        hud.add(rightHud, BorderLayout.EAST);

        timerBar = new JProgressBar(0, TIME_PER_PROMPT);
        timerBar.setValue(TIME_PER_PROMPT);
        timerBar.setForeground(F1_RED);
        timerBar.setBackground(new Color(35, 38, 48));
        timerBar.setBorderPainted(false);
        timerBar.setPreferredSize(new Dimension(0, 8));

        JPanel topWrapper = new JPanel(new BorderLayout(0, 10));
        topWrapper.setOpaque(false);
        topWrapper.add(hud, BorderLayout.NORTH);
        topWrapper.add(timerBar, BorderLayout.SOUTH);

        // Left Visual Lap Gauge
        lapGaugePanel = new LapGaugePanel();

        // Center Prompt Card
        JPanel centerCard = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 20, 20));
                g2.setColor(new Color(225, 6, 0, 110));
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Double(1, 1, getWidth() - 2, getHeight() - 2, 20, 20));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        centerCard.setOpaque(false);
        centerCard.setLayout(new BoxLayout(centerCard, BoxLayout.Y_AXIS));
        centerCard.setBorder(new EmptyBorder(38, 35, 35, 35));

        promptLabel = new JLabel("Prompt goes here", SwingConstants.CENTER);
        promptLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        promptLabel.setForeground(TEXT_LIGHT);
        promptLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        answerField = new JTextField();
        answerField.setMaximumSize(new Dimension(480, 48));
        answerField.setFont(new Font("SansSerif", Font.PLAIN, 18));
        answerField.setBackground(BG_ASPHALT);
        answerField.setForeground(TEXT_LIGHT);
        answerField.setCaretColor(TEXT_LIGHT);
        setFieldBorder(TEXT_MUTED);
        answerField.addActionListener(e -> submitAnswer());

        submitButton = createStyledButton("LOCK IN ANSWER", F1_RED);
        submitButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        submitButton.addActionListener(e -> submitAnswer());

        feedbackLabel = new JLabel(" ");
        feedbackLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        feedbackLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        didYouMeanButton = new JButton("");
        didYouMeanButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        didYouMeanButton.setBackground(new Color(40, 44, 58));
        didYouMeanButton.setForeground(TIER_UNCOMMON);
        didYouMeanButton.setFocusPainted(false);
        didYouMeanButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TIER_UNCOMMON, 1),
                new EmptyBorder(6, 14, 6, 14)
        ));
        didYouMeanButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        didYouMeanButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        didYouMeanButton.setVisible(false);

        centerCard.add(promptLabel);
        centerCard.add(Box.createRigidArea(new Dimension(0, 30)));
        centerCard.add(answerField);
        centerCard.add(Box.createRigidArea(new Dimension(0, 18)));
        centerCard.add(submitButton);
        centerCard.add(Box.createRigidArea(new Dimension(0, 20)));
        centerCard.add(feedbackLabel);
        centerCard.add(Box.createRigidArea(new Dimension(0, 10)));
        centerCard.add(didYouMeanButton);

        panel.add(topWrapper, BorderLayout.NORTH);
        panel.add(lapGaugePanel, BorderLayout.WEST);
        panel.add(centerCard, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 15));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(25, 40, 25, 40));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel title = new JLabel("CHECKERED FLAG — RACE CLASSIFICATION");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(F1_RED);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        finalScoreLabel = new JLabel("TOTAL LAPS: 0 / 70");
        finalScoreLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        finalScoreLabel.setForeground(TIER_UNCOMMON);
        finalScoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(title);
        header.add(Box.createRigidArea(new Dimension(0, 6)));
        header.add(finalScoreLabel);

        summaryListPanel = new JPanel();
        summaryListPanel.setLayout(new BoxLayout(summaryListPanel, BoxLayout.Y_AXIS));
        summaryListPanel.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(summaryListPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        JButton playAgainBtn = createStyledButton("RACE AGAIN", F1_RED);
        playAgainBtn.addActionListener(e -> startGame());
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.add(playAgainBtn);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);

        return panel;
    }

    private void setFieldBorder(Color borderColor) {
        answerField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 2),
                new EmptyBorder(8, 14, 8, 14)
        ));
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 15));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(220, 46));
        btn.setMaximumSize(new Dimension(220, 46));
        return btn;
    }

    private void startGame() {
        currentIndex = 0;
        totalLaps = 0;
        results.clear();

        // Randomly select 7 questions from the larger pool
        List<Prompt> shuffled = new ArrayList<>(promptPool);
        Collections.shuffle(shuffled);
        activePrompts = new ArrayList<>(shuffled.subList(0, Math.min(QUESTIONS_PER_RACE, shuffled.size())));

        cardLayout.show(mainPanel, "GAME");
        loadPrompt();
    }

    private void loadPrompt() {
        if (currentIndex >= activePrompts.size()) {
            showSummary();
            return;
        }

        Prompt current = activePrompts.get(currentIndex);
        int maxLaps = activePrompts.size() * MAX_LAPS_PER_PROMPT;
        progressLabel.setText("SECTOR " + (currentIndex + 1) + " / " + activePrompts.size());
        lapsLabel.setText("LAPS: " + totalLaps + " / " + maxLaps);
        promptLabel.setText("<html><div style='text-align: center;'>" + current.question + "</div></html>");
        answerField.setText("");
        answerField.setEnabled(true);
        setFieldBorder(TEXT_MUTED);
        submitButton.setEnabled(true);
        feedbackLabel.setText(" ");
        didYouMeanButton.setVisible(false);
        answerField.requestFocusInWindow();

        timeLeft = TIME_PER_PROMPT;
        timerLabel.setText(timeLeft + "s");
        timerBar.setValue(timeLeft);
        f1LightsPanel.setLitCount(0);

        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        countdownTimer = new javax.swing.Timer(1000, e -> {
            timeLeft--;
            timerLabel.setText(timeLeft + "s");
            timerBar.setValue(timeLeft);

            if (timeLeft <= 5 && timeLeft > 0) {
                f1LightsPanel.setLitCount(6 - timeLeft);
            } else if (timeLeft <= 0) {
                f1LightsPanel.setLitCount(5);
                countdownTimer.stop();
                handleTimeout();
            }
        });
        countdownTimer.start();
    }

    private void submitAnswer() {
        if (!answerField.isEnabled()) return;

        String rawInput = answerField.getText().trim();
        if (rawInput.isEmpty()) return;

        Prompt current = activePrompts.get(currentIndex);
        EvalResult eval = current.evaluate(rawInput);

        if (eval.status == MatchStatus.ACCEPTED) {
            acceptAnswer(current, eval.match, eval.wasFuzzy);
        } else {
            setFieldBorder(F1_RED);
            feedbackLabel.setForeground(F1_RED);
            feedbackLabel.setText("Not recognized — keep guessing!");
            answerField.selectAll();
            answerField.requestFocusInWindow();

            if (eval.status == MatchStatus.SUGGESTION && eval.match != null) {
                AnswerMatch suggested = eval.match;
                didYouMeanButton.setText("Did you mean: " + suggested.displayName + "? (Click to use)");
                for (var al : didYouMeanButton.getActionListeners()) {
                    didYouMeanButton.removeActionListener(al);
                }
                didYouMeanButton.addActionListener(e -> {
                    if (answerField.isEnabled()) {
                        acceptAnswer(current, suggested, true);
                    }
                });
                didYouMeanButton.setVisible(true);
            } else {
                didYouMeanButton.setVisible(false);
            }
        }
    }

    private void acceptAnswer(Prompt current, AnswerMatch match, boolean wasFuzzy) {
        countdownTimer.stop();
        answerField.setEnabled(false);
        submitButton.setEnabled(false);
        didYouMeanButton.setVisible(false);
        setFieldBorder(match.tierColor);

        totalLaps += match.laps;
        lapsLabel.setText("LAPS: " + totalLaps + " / " + (activePrompts.size() * MAX_LAPS_PER_PROMPT));
        feedbackLabel.setForeground(match.tierColor);
        String fuzzyNote = wasFuzzy ? " • typo fixed" : "";
        feedbackLabel.setText("+" + match.laps + " LAPS  •  " + match.tierName + " (" + match.displayName + fuzzyNote + ")");
        results.add(new RoundResult(current.question, match.displayName, match.tierName, match.laps, match.tierColor, current.getSampleKrillion()));

        advanceAfterDelay();
    }

    private void handleTimeout() {
        answerField.setEnabled(false);
        submitButton.setEnabled(false);
        didYouMeanButton.setVisible(false);
        Prompt current = activePrompts.get(currentIndex);

        feedbackLabel.setForeground(F1_RED);
        feedbackLabel.setText("DNF (TIME'S UP)  •  +0 LAPS");
        results.add(new RoundResult(current.question, "No Answer", "DNF", 0, F1_RED, current.getSampleKrillion()));

        advanceAfterDelay();
    }

    private void advanceAfterDelay() {
        javax.swing.Timer delay = new javax.swing.Timer(1500, e -> {
            currentIndex++;
            loadPrompt();
        });
        delay.setRepeats(false);
        delay.start();
    }

    private void showSummary() {
        int maxLaps = activePrompts.size() * MAX_LAPS_PER_PROMPT;
        finalScoreLabel.setText("TOTAL LAPS: " + totalLaps + " / " + maxLaps);
        summaryListPanel.removeAll();

        for (RoundResult r : results) {
            JPanel row = new JPanel(new BorderLayout(10, 4));
            row.setBackground(new Color(26, 29, 38));
            row.setBorder(new EmptyBorder(10, 14, 10, 14));
            row.setMaximumSize(new Dimension(720, 75));

            JLabel qLabel = new JLabel(r.question);
            qLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            qLabel.setForeground(TEXT_LIGHT);

            JLabel detailLabel = new JLabel("Your pick: " + r.userAnswer + "   |   Grand Slam gem: " + r.krillionHint);
            detailLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            detailLabel.setForeground(TEXT_MUTED);

            JPanel left = new JPanel(new GridLayout(2, 1));
            left.setOpaque(false);
            left.add(qLabel);
            left.add(detailLabel);

            JLabel scoreLbl = new JLabel("+" + r.laps + " LAPS (" + r.tier + ")");
            scoreLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
            scoreLbl.setForeground(r.color);

            row.add(left, BorderLayout.CENTER);
            row.add(scoreLbl, BorderLayout.EAST);

            summaryListPanel.add(row);
            summaryListPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        }

        summaryListPanel.revalidate();
        summaryListPanel.repaint();
        cardLayout.show(mainPanel, "SUMMARY");
    }

    // --- Jolpica F1 API Background Sync ---

    private void syncJolpicaApiInBackground() {
        new Thread(() -> {
            try {
                Map<String, List<String[]>> data = new HashMap<>();
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(4))
                        .build();

                data.put("champions", fetchDrivers(client, "https://api.jolpi.ca/ergast/f1/driverstandings/1.json?limit=100"));
                data.put("constructors", fetchConstructors(client, "https://api.jolpi.ca/ergast/f1/constructors.json?limit=250"));
                data.put("circuits", fetchCircuits(client, "https://api.jolpi.ca/ergast/f1/circuits.json?limit=100"));
                data.put("countries", fetchCountries(client, "https://api.jolpi.ca/ergast/f1/circuits.json?limit=100"));
                data.put("monaco", fetchDrivers(client, "https://api.jolpi.ca/ergast/f1/circuits/monaco/results/1.json?limit=100"));
                data.put("monza", fetchDrivers(client, "https://api.jolpi.ca/ergast/f1/circuits/monza/results/1.json?limit=100"));
                data.put("silverstone", fetchDrivers(client, "https://api.jolpi.ca/ergast/f1/circuits/silverstone/results/1.json?limit=100"));
                data.put("ferrari", fetchDrivers(client, "https://api.jolpi.ca/ergast/f1/constructors/ferrari/drivers.json?limit=150"));
                data.put("mclaren", fetchDrivers(client, "https://api.jolpi.ca/ergast/f1/constructors/mclaren/drivers.json?limit=150"));
                data.put("red_bull", fetchDrivers(client, "https://api.jolpi.ca/ergast/f1/constructors/red_bull/drivers.json?limit=100"));
                data.put("mercedes", fetchDrivers(client, "https://api.jolpi.ca/ergast/f1/constructors/mercedes/drivers.json?limit=100"));

                SwingUtilities.invokeLater(() -> {
                    int addedCount = 0;
                    for (Prompt p : promptPool) {
                        if (p.apiKey != null && data.containsKey(p.apiKey)) {
                            for (String[] entry : data.get(p.apiKey)) {
                                if (entry.length >= 2) {
                                    if (p.addKrillionIfAbsent(entry[0], entry[1])) addedCount++;
                                } else if (entry.length == 1) {
                                    if (p.addKrillionIfAbsent(entry[0])) addedCount++;
                                }
                            }
                        }
                    }
                    if (addedCount > 0) {
                        apiStatusLabel.setForeground(TIER_KRILLION);
                        apiStatusLabel.setText("Live F1 Database synced (+" + addedCount + " historical entries loaded)");
                    } else {
                        apiStatusLabel.setText("Offline mode — using built-in F1 database");
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        apiStatusLabel.setText("Offline mode — using built-in F1 database")
                );
            }
        }).start();
    }

    private static String httpGet(HttpClient client, String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? res.body() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String[]> fetchDrivers(HttpClient client, String url) {
        List<String[]> list = new ArrayList<>();
        String json = httpGet(client, url);
        if (json.isEmpty()) return list;

        Pattern p = Pattern.compile("\"givenName\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"familyName\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String first = unescapeUnicode(m.group(1));
            String last = unescapeUnicode(m.group(2));
            list.add(new String[]{first + " " + last, last});
        }
        return list;
    }

    private static List<String[]> fetchConstructors(HttpClient client, String url) {
        List<String[]> list = new ArrayList<>();
        String json = httpGet(client, url);
        if (json.isEmpty()) return list;

        Pattern p = Pattern.compile("\"constructorId\"\\s*:\\s*\"[^\"]+\"[^}]*?\"name\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        while (m.find()) {
            list.add(new String[]{unescapeUnicode(m.group(1))});
        }
        return list;
    }

    private static List<String[]> fetchCircuits(HttpClient client, String url) {
        List<String[]> list = new ArrayList<>();
        String json = httpGet(client, url);
        if (json.isEmpty()) return list;

        Pattern p = Pattern.compile("\"circuitName\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"locality\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String name = unescapeUnicode(m.group(1));
            String locality = unescapeUnicode(m.group(2));
            list.add(new String[]{name, locality});
        }
        return list;
    }

    private static List<String[]> fetchCountries(HttpClient client, String url) {
        List<String[]> list = new ArrayList<>();
        String json = httpGet(client, url);
        if (json.isEmpty()) return list;

        Pattern p = Pattern.compile("\"country\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        while (m.find()) {
            list.add(new String[]{unescapeUnicode(m.group(1))});
        }
        return list;
    }

    private static String unescapeUnicode(String s) {
        Pattern p = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            char ch = (char) Integer.parseInt(m.group(1), 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(ch)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // --- Custom F1 Visual Components ---

    static class TrackBackgroundPanel extends JPanel {
        private final List<SpeedLine> lines = new ArrayList<>();
        private final Random rand = new Random();
        private int kerbOffset = 0;

        static class SpeedLine {
            float x, y, length, speed;
            SpeedLine(float x, float y, float length, float speed) {
                this.x = x; this.y = y; this.length = length; this.speed = speed;
            }
        }

        TrackBackgroundPanel(LayoutManager layout) {
            super(layout);
            for (int i = 0; i < 22; i++) {
                lines.add(new SpeedLine(rand.nextInt(840), rand.nextInt(600), 30 + rand.nextFloat() * 70, 5 + rand.nextFloat() * 8));
            }
        }

        void stepAnimation() {
            kerbOffset = (kerbOffset + 2) % 48;
            for (SpeedLine l : lines) {
                l.x -= l.speed;
                if (l.x + l.length < 0) {
                    l.x = getWidth() + rand.nextInt(80);
                    l.y = rand.nextInt(Math.max(1, getHeight()));
                }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setPaint(new GradientPaint(0, 0, new Color(22, 24, 32), 0, getHeight(), new Color(11, 12, 16)));
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setStroke(new BasicStroke(2f));
            for (SpeedLine l : lines) {
                g2.setColor(new Color(255, 255, 255, 18));
                g2.drawLine((int) l.x, (int) l.y, (int) (l.x + l.length), (int) l.y);
            }

            int kerbW = 24, kerbH = 6;
            for (int x = -48 + kerbOffset; x < getWidth() + 48; x += kerbW) {
                boolean isRed = ((x - kerbOffset) / kerbW) % 2 == 0;
                g2.setColor(isRed ? F1_RED : new Color(235, 235, 235));
                g2.fillRect(x, 0, kerbW, kerbH);
                g2.fillRect(x, getHeight() - kerbH, kerbW, kerbH);
            }
            g2.dispose();
        }
    }

    static class LapGaugePanel extends JPanel {
        private float displayLaps = 0;
        private int maxLaps = 70;

        LapGaugePanel() {
            setPreferredSize(new Dimension(68, 0));
            setOpaque(false);
        }

        void setTargetLaps(int target, int max) {
            this.maxLaps = Math.max(1, max);
            this.displayLaps += (target - displayLaps) * 0.14f;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int padTop = 22, padBot = 18;
            int barX = 32, barW = 12;
            int barH = getHeight() - padTop - padBot;

            g2.setColor(new Color(38, 42, 54, 220));
            g2.fillRoundRect(barX, padTop, barW, barH, 10, 10);

            float ratio = Math.min(1f, displayLaps / maxLaps);
            int fillH = (int) (barH * ratio);
            int fillY = padTop + barH - fillH;

            g2.setPaint(new GradientPaint(0, padTop + barH, F1_RED, 0, padTop, TIER_KRILLION));
            g2.fillRoundRect(barX, fillY, barW, fillH, 10, 10);

            for (int r = 0; r < 2; r++) {
                for (int c = 0; c < 4; c++) {
                    g2.setColor((r + c) % 2 == 0 ? Color.WHITE : Color.BLACK);
                    g2.fillRect(barX - 4 + c * 5, padTop - 14 + r * 5, 5, 5);
                }
            }

            g2.setColor(F1_RED);
            g2.fillOval(barX - 4, fillY - 8, 20, 20);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(barX - 4, fillY - 8, 20, 20);

            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.setColor(TEXT_MUTED);
            g2.drawString("L" + maxLaps, 2, padTop + 8);
            g2.drawString("L" + (maxLaps / 2), 2, padTop + barH / 2 + 4);
            g2.drawString("L0", 6, padTop + barH);

            g2.dispose();
        }
    }

    static class F1LightsPanel extends JPanel {
        private int litCount = 0;

        F1LightsPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(130, 24));
        }

        void setLitCount(int count) {
            this.litCount = count;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int lights = 5, size = 14, gap = 10;
            int totalW = lights * size + (lights - 1) * gap;
            int startX = (getWidth() - totalW) / 2;
            int y = (getHeight() - size) / 2;

            for (int i = 0; i < lights; i++) {
                int x = startX + i * (size + gap);
                g2.setColor(i < litCount ? F1_RED : new Color(50, 54, 66));
                g2.fillOval(x, y, size, size);
            }
            g2.dispose();
        }
    }

    // --- Data Structures ---

    enum MatchStatus { ACCEPTED, SUGGESTION, NONE }

    static class AnswerMatch {
        String displayName;
        String tierName;
        int laps;
        Color tierColor;

        AnswerMatch(String displayName, String tierName, int laps, Color tierColor) {
            this.displayName = displayName;
            this.tierName = tierName;
            this.laps = laps;
            this.tierColor = tierColor;
        }
    }

    static class EvalResult {
        final MatchStatus status;
        final AnswerMatch match;
        final boolean wasFuzzy;

        EvalResult(MatchStatus status, AnswerMatch match, boolean wasFuzzy) {
            this.status = status;
            this.match = match;
            this.wasFuzzy = wasFuzzy;
        }
    }

    static class RoundResult {
        String question, userAnswer, tier, krillionHint;
        int laps;
        Color color;

        RoundResult(String question, String userAnswer, String tier, int laps, Color color, String krillionHint) {
            this.question = question;
            this.userAnswer = userAnswer;
            this.tier = tier;
            this.laps = laps;
            this.color = color;
            this.krillionHint = krillionHint;
        }
    }

    static class Prompt {
        String question;
        String apiKey;
        Map<String, AnswerMatch> lookup = new HashMap<>();
        List<String> krillionAnswers = new ArrayList<>();

        Prompt(String question, String apiKey) {
            this.question = question;
            this.apiKey = apiKey;
        }

        void add(int laps, String tierName, Color color, String displayName, String... aliases) {
            AnswerMatch match = new AnswerMatch(displayName, tierName, laps, color);
            lookup.put(normalize(displayName), match);
            for (String alias : aliases) {
                lookup.put(normalize(alias), match);
            }
            if (laps == MAX_LAPS_PER_PROMPT) {
                krillionAnswers.add(displayName);
            }
        }

        boolean addKrillionIfAbsent(String displayName, String... aliases) {
            String key = normalize(displayName);
            if (key.isEmpty() || lookup.containsKey(key)) return false;

            AnswerMatch match = new AnswerMatch(displayName, "GRAND SLAM", MAX_LAPS_PER_PROMPT, TIER_KRILLION);
            lookup.put(key, match);
            for (String alias : aliases) {
                String ak = normalize(alias);
                if (!ak.isEmpty() && !lookup.containsKey(ak)) {
                    lookup.put(ak, match);
                }
            }
            krillionAnswers.add(displayName);
            return true;
        }

        void addCommon(String name, String... aliases) { add(2, "COMMON", TIER_COMMON, name, aliases); }
        void addUncommon(String name, String... aliases) { add(5, "UNCOMMON", TIER_UNCOMMON, name, aliases); }
        void addRare(String name, String... aliases) { add(8, "PURPLE SECTOR", TIER_RARE, name, aliases); }
        void addKrillion(String name, String... aliases) { add(10, "GRAND SLAM", TIER_KRILLION, name, aliases); }

        EvalResult evaluate(String input) {
            String clean = normalize(input);
            if (clean.isEmpty()) return new EvalResult(MatchStatus.NONE, null, false);

            // 1. Exact match
            AnswerMatch exact = lookup.get(clean);
            if (exact != null) {
                return new EvalResult(MatchStatus.ACCEPTED, exact, false);
            }

            // 2. Fuzzy match (auto-accept small typos + "Did you mean..." for looser matches)
            AnswerMatch bestAuto = null;
            int bestAutoDist = Integer.MAX_VALUE;

            AnswerMatch bestSuggestion = null;
            int bestSuggestionScore = Integer.MAX_VALUE;

            for (Map.Entry<String, AnswerMatch> entry : lookup.entrySet()) {
                String candidate = entry.getKey();
                int dist = levenshtein(clean, candidate);
                int autoLimit = getAutoAcceptTypos(candidate.length());

                if (autoLimit > 0 && dist <= autoLimit) {
                    if (dist < bestAutoDist || (dist == bestAutoDist && bestAuto != null && entry.getValue().laps > bestAuto.laps)) {
                        bestAutoDist = dist;
                        bestAuto = entry.getValue();
                    }
                    continue;
                }

                int suggestLimit = getSuggestionTypos(candidate.length());
                boolean isPartialMatch = clean.length() >= 4 && (candidate.startsWith(clean) || candidate.contains(clean));

                if (dist <= suggestLimit || isPartialMatch) {
                    int score = isPartialMatch ? Math.min(dist, 2) : dist;
                    if (score < bestSuggestionScore) {
                        bestSuggestionScore = score;
                        bestSuggestion = entry.getValue();
                    }
                }
            }

            if (bestAuto != null) {
                return new EvalResult(MatchStatus.ACCEPTED, bestAuto, true);
            }
            if (bestSuggestion != null) {
                return new EvalResult(MatchStatus.SUGGESTION, bestSuggestion, true);
            }
            return new EvalResult(MatchStatus.NONE, null, false);
        }

        private static int getAutoAcceptTypos(int length) {
            if (length <= 3) return 0;
            if (length <= 6) return 1;
            if (length <= 10) return 2;
            return 3;
        }

        private static int getSuggestionTypos(int length) {
            if (length <= 4) return 1;
            if (length <= 8) return 2;
            return 4;
        }

        private static int levenshtein(String a, String b) {
            int[] prev = new int[b.length() + 1];
            int[] curr = new int[b.length() + 1];

            for (int j = 0; j <= b.length(); j++) prev[j] = j;

            for (int i = 1; i <= a.length(); i++) {
                curr[0] = i;
                for (int j = 1; j <= b.length(); j++) {
                    int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                    curr[j] = Math.min(
                            Math.min(curr[j - 1] + 1, prev[j] + 1),
                            prev[j - 1] + cost
                    );
                }
                int[] temp = prev;
                prev = curr;
                curr = temp;
            }
            return prev[b.length()];
        }

        String getSampleKrillion() {
            if (krillionAnswers.isEmpty()) return "—";
            return krillionAnswers.get(new Random().nextInt(krillionAnswers.size()));
        }

        private static String normalize(String s) {
            String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            return noAccents.toLowerCase().replaceAll("[^a-z0-9]", "");
        }
    }

    // --- 12-Question F1 Pool ---

    private List<Prompt> buildF1PromptPool() {
        List<Prompt> list = new ArrayList<>();

        // 1. World Champions
        Prompt p1 = new Prompt("Name a Formula One World Champion", "champions");
        p1.addCommon("Lewis Hamilton", "Hamilton");
        p1.addCommon("Max Verstappen", "Verstappen");
        p1.addCommon("Michael Schumacher", "Schumacher");
        p1.addCommon("Ayrton Senna", "Senna");
        p1.addCommon("Sebastian Vettel", "Vettel");
        p1.addCommon("Fernando Alonso", "Alonso");
        p1.addUncommon("Niki Lauda", "Lauda");
        p1.addUncommon("Alain Prost", "Prost");
        p1.addUncommon("Kimi Raikkonen", "Raikkonen", "Kimi");
        p1.addUncommon("Jenson Button", "Button");
        p1.addUncommon("Nico Rosberg", "Rosberg");
        p1.addUncommon("Mika Hakkinen", "Hakkinen");
        p1.addUncommon("Nigel Mansell", "Mansell");
        p1.addRare("Jackie Stewart", "Stewart");
        p1.addRare("Jim Clark", "Clark");
        p1.addRare("Juan Manuel Fangio", "Fangio");
        p1.addRare("Emerson Fittipaldi", "Fittipaldi");
        p1.addRare("Mario Andretti", "Andretti");
        p1.addRare("James Hunt", "Hunt");
        p1.addRare("Jody Scheckter", "Scheckter");
        p1.addKrillion("Jochen Rindt", "Rindt");
        p1.addKrillion("Mike Hawthorn", "Hawthorn");
        p1.addKrillion("Phil Hill");
        p1.addKrillion("Denny Hulme", "Hulme");
        p1.addKrillion("Giuseppe Farina", "Farina", "Nino Farina");
        p1.addKrillion("John Surtees", "Surtees");
        list.add(p1);

        // 2. Constructors
        Prompt p2 = new Prompt("Name an F1 Constructor (past or present)", "constructors");
        p2.addCommon("Ferrari", "Scuderia Ferrari");
        p2.addCommon("Mercedes", "Mercedes AMG");
        p2.addCommon("Red Bull", "Red Bull Racing");
        p2.addCommon("McLaren");
        p2.addCommon("Williams");
        p2.addCommon("Aston Martin");
        p2.addCommon("Alpine");
        p2.addCommon("Haas");
        p2.addUncommon("Sauber", "Kick Sauber");
        p2.addUncommon("Renault");
        p2.addUncommon("Lotus");
        p2.addUncommon("Brawn GP", "Brawn");
        p2.addUncommon("Force India");
        p2.addUncommon("Toro Rosso", "AlphaTauri", "Racing Bulls");
        p2.addUncommon("Jordan");
        p2.addUncommon("Benetton");
        p2.addRare("Tyrrell");
        p2.addRare("Brabham");
        p2.addRare("Minardi");
        p2.addRare("Arrows");
        p2.addRare("Ligier");
        p2.addRare("Jaguar");
        p2.addRare("Toyota");
        p2.addKrillion("Andrea Moda");
        p2.addKrillion("Super Aguri");
        p2.addKrillion("Spyker");
        p2.addKrillion("Simtek");
        list.add(p2);

        // 3. Host Countries
        Prompt p3 = new Prompt("Name a country that has hosted an F1 Grand Prix", "countries");
        p3.addCommon("Italy");
        p3.addCommon("United Kingdom", "UK", "Britain", "England");
        p3.addCommon("Monaco");
        p3.addCommon("United States", "USA", "US");
        p3.addCommon("Spain");
        p3.addCommon("Australia");
        p3.addCommon("Japan");
        p3.addCommon("Brazil");
        p3.addCommon("Canada");
        p3.addCommon("Belgium");
        p3.addUncommon("Austria");
        p3.addUncommon("Netherlands", "Holland");
        p3.addUncommon("Hungary");
        p3.addUncommon("Singapore");
        p3.addUncommon("Bahrain");
        p3.addUncommon("Saudi Arabia");
        p3.addUncommon("Qatar");
        p3.addUncommon("UAE", "United Arab Emirates", "Abu Dhabi");
        p3.addUncommon("Mexico");
        p3.addUncommon("France");
        p3.addUncommon("Germany");
        p3.addUncommon("China");
        p3.addUncommon("Azerbaijan");
        p3.addRare("Portugal");
        p3.addRare("Turkey");
        p3.addRare("Malaysia");
        p3.addRare("South Africa");
        p3.addRare("Argentina");
        p3.addRare("India");
        p3.addRare("South Korea", "Korea");
        p3.addKrillion("Morocco");
        p3.addKrillion("Sweden");
        p3.addKrillion("Switzerland");
        list.add(p3);

        // 4. Monaco GP Winners
        Prompt p4 = new Prompt("Name a driver who has won the Monaco Grand Prix", "monaco");
        p4.addCommon("Ayrton Senna", "Senna");
        p4.addCommon("Lewis Hamilton", "Hamilton");
        p4.addCommon("Max Verstappen", "Verstappen");
        p4.addCommon("Charles Leclerc", "Leclerc");
        p4.addCommon("Michael Schumacher", "Schumacher");
        p4.addCommon("Daniel Ricciardo", "Ricciardo");
        p4.addCommon("Sergio Perez", "Perez", "Checo");
        p4.addUncommon("Fernando Alonso", "Alonso");
        p4.addUncommon("Sebastian Vettel", "Vettel");
        p4.addUncommon("Nico Rosberg", "Rosberg");
        p4.addUncommon("Kimi Raikkonen", "Raikkonen");
        p4.addUncommon("Jenson Button", "Button");
        p4.addUncommon("Mark Webber", "Webber");
        p4.addUncommon("Alain Prost", "Prost");
        p4.addRare("David Coulthard", "Coulthard");
        p4.addRare("Juan Pablo Montoya", "Montoya");
        p4.addRare("Jarno Trulli", "Trulli");
        p4.addRare("Olivier Panis", "Panis");
        p4.addKrillion("Jean-Pierre Beltoise", "Beltoise");
        p4.addKrillion("Maurice Trintignant", "Trintignant");
        list.add(p4);

        // 5. Engine Suppliers (Offline curated)
        Prompt p5 = new Prompt("Name an F1 Engine Supplier (past or present)", null);
        p5.addCommon("Ferrari");
        p5.addCommon("Mercedes");
        p5.addCommon("Honda");
        p5.addCommon("Renault");
        p5.addCommon("Ford");
        p5.addUncommon("Cosworth");
        p5.addUncommon("BMW");
        p5.addUncommon("Toyota");
        p5.addUncommon("Porsche");
        p5.addUncommon("Audi");
        p5.addUncommon("Red Bull Powertrains", "RBPT");
        p5.addRare("Yamaha");
        p5.addRare("Peugeot");
        p5.addRare("Judd");
        p5.addRare("Ilmor");
        p5.addRare("Lamborghini");
        p5.addRare("Alfa Romeo");
        p5.addRare("Mugen-Honda", "Mugen");
        p5.addKrillion("Hart", "Brian Hart");
        p5.addKrillion("Megatron");
        p5.addKrillion("Motori Moderni");
        p5.addKrillion("Repco");
        p5.addKrillion("Climax", "Coventry Climax");
        p5.addKrillion("Weslake");
        list.add(p5);

        // 6. Ferrari Drivers
        Prompt p6 = new Prompt("Name a driver who has raced for Ferrari in F1", "ferrari");
        p6.addCommon("Michael Schumacher", "Schumacher");
        p6.addCommon("Charles Leclerc", "Leclerc");
        p6.addCommon("Lewis Hamilton", "Hamilton");
        p6.addCommon("Carlos Sainz", "Sainz");
        p6.addCommon("Sebastian Vettel", "Vettel");
        p6.addCommon("Kimi Raikkonen", "Raikkonen");
        p6.addCommon("Fernando Alonso", "Alonso");
        p6.addCommon("Niki Lauda", "Lauda");
        p6.addUncommon("Felipe Massa", "Massa");
        p6.addUncommon("Rubens Barrichello", "Barrichello");
        p6.addUncommon("Gilles Villeneuve", "Villeneuve");
        p6.addUncommon("Alain Prost", "Prost");
        p6.addUncommon("Nigel Mansell", "Mansell");
        p6.addUncommon("Jean Alesi", "Alesi");
        p6.addUncommon("Gerhard Berger", "Berger");
        p6.addRare("Eddie Irvine", "Irvine");
        p6.addRare("Michele Alboreto", "Alboreto");
        p6.addRare("Didier Pironi", "Pironi");
        p6.addRare("Rene Arnoux", "Arnoux");
        p6.addKrillion("Ivan Capelli", "Capelli");
        p6.addKrillion("Luca Badoer", "Badoer");
        p6.addKrillion("Oliver Bearman", "Bearman");
        list.add(p6);

        // 7. F1 Circuits
        Prompt p7 = new Prompt("Name an F1 Circuit (past or present)", "circuits");
        p7.addCommon("Monza");
        p7.addCommon("Silverstone");
        p7.addCommon("Spa-Francorchamps", "Spa");
        p7.addCommon("Monaco", "Monte Carlo");
        p7.addCommon("Suzuka");
        p7.addCommon("Interlagos");
        p7.addCommon("Circuit of the Americas", "COTA", "Austin");
        p7.addCommon("Las Vegas");
        p7.addCommon("Miami");
        p7.addUncommon("Imola");
        p7.addUncommon("Red Bull Ring", "Spielberg");
        p7.addUncommon("Zandvoort");
        p7.addUncommon("Hungaroring");
        p7.addUncommon("Marina Bay", "Singapore");
        p7.addUncommon("Yas Marina", "Abu Dhabi");
        p7.addUncommon("Albert Park", "Melbourne");
        p7.addUncommon("Circuit Gilles Villeneuve", "Montreal");
        p7.addUncommon("Baku");
        p7.addUncommon("Jeddah");
        p7.addUncommon("Barcelona", "Catalunya");
        p7.addUncommon("Paul Ricard", "Circuit Paul Ricard", "Le Castellet");
        p7.addRare("Nurburgring");
        p7.addRare("Hockenheim", "Hockenheimring");
        p7.addRare("Sepang");
        p7.addRare("Istanbul Park");
        p7.addRare("Portimao", "Algarve");
        p7.addKrillion("Pescara");
        p7.addKrillion("AVUS");
        p7.addKrillion("Anderstorp");
        list.add(p7);

        // 8. McLaren Drivers
        Prompt p8 = new Prompt("Name a driver who has raced for McLaren in F1", "mclaren");
        p8.addCommon("Lando Norris", "Norris");
        p8.addCommon("Oscar Piastri", "Piastri");
        p8.addCommon("Lewis Hamilton", "Hamilton");
        p8.addCommon("Ayrton Senna", "Senna");
        p8.addCommon("Fernando Alonso", "Alonso");
        p8.addCommon("Daniel Ricciardo", "Ricciardo");
        p8.addUncommon("Alain Prost", "Prost");
        p8.addUncommon("Mika Hakkinen", "Hakkinen");
        p8.addUncommon("Kimi Raikkonen", "Raikkonen");
        p8.addUncommon("Jenson Button", "Button");
        p8.addUncommon("Niki Lauda", "Lauda");
        p8.addUncommon("Carlos Sainz", "Sainz");
        p8.addUncommon("David Coulthard", "Coulthard");
        p8.addRare("James Hunt", "Hunt");
        p8.addRare("Emerson Fittipaldi", "Fittipaldi");
        p8.addRare("Juan Pablo Montoya", "Montoya");
        p8.addRare("Heikki Kovalainen", "Kovalainen");
        p8.addKrillion("Stoffel Vandoorne", "Vandoorne");
        p8.addKrillion("Kevin Magnussen", "Magnussen");
        p8.addKrillion("Pedro de la Rosa", "de la Rosa");
        list.add(p8);

        // 9. Red Bull Racing Drivers
        Prompt p9 = new Prompt("Name a driver who has raced for Red Bull Racing", "red_bull");
        p9.addCommon("Max Verstappen", "Verstappen");
        p9.addCommon("Sebastian Vettel", "Vettel");
        p9.addCommon("Sergio Perez", "Perez", "Checo");
        p9.addCommon("Daniel Ricciardo", "Ricciardo");
        p9.addUncommon("Mark Webber", "Webber");
        p9.addUncommon("Pierre Gasly", "Gasly");
        p9.addUncommon("Alex Albon", "Albon", "Alexander Albon");
        p9.addUncommon("David Coulthard", "Coulthard");
        p9.addRare("Daniil Kvyat", "Kvyat");
        p9.addRare("Liam Lawson", "Lawson");
        p9.addRare("Yuki Tsunoda", "Tsunoda");
        p9.addKrillion("Christian Klien", "Klien");
        p9.addKrillion("Vitantonio Liuzzi", "Liuzzi");
        p9.addKrillion("Robert Doornbos", "Doornbos");
        list.add(p9);

        // 10. Mercedes Drivers
        Prompt p10 = new Prompt("Name a driver who has raced for Mercedes in F1", "mercedes");
        p10.addCommon("Lewis Hamilton", "Hamilton");
        p10.addCommon("George Russell", "Russell");
        p10.addCommon("Nico Rosberg", "Rosberg");
        p10.addCommon("Valtteri Bottas", "Bottas");
        p10.addCommon("Michael Schumacher", "Schumacher");
        p10.addUncommon("Kimi Antonelli", "Antonelli", "Andrea Kimi Antonelli");
        p10.addUncommon("Juan Manuel Fangio", "Fangio");
        p10.addRare("Stirling Moss", "Moss");
        p10.addKrillion("Karl Kling", "Kling");
        p10.addKrillion("Hans Herrmann", "Herrmann");
        p10.addKrillion("Piero Taruffi", "Taruffi");
        list.add(p10);

        // 11. Monza Winners
        Prompt p11 = new Prompt("Name a driver who has won at Monza (Italian GP)", "monza");
        p11.addCommon("Lewis Hamilton", "Hamilton");
        p11.addCommon("Michael Schumacher", "Schumacher");
        p11.addCommon("Max Verstappen", "Verstappen");
        p11.addCommon("Charles Leclerc", "Leclerc");
        p11.addCommon("Sebastian Vettel", "Vettel");
        p11.addUncommon("Fernando Alonso", "Alonso");
        p11.addUncommon("Daniel Ricciardo", "Ricciardo");
        p11.addUncommon("Pierre Gasly", "Gasly");
        p11.addUncommon("Ayrton Senna", "Senna");
        p11.addUncommon("Alain Prost", "Prost");
        p11.addRare("Rubens Barrichello", "Barrichello");
        p11.addRare("Juan Pablo Montoya", "Montoya");
        p11.addRare("Nico Rosberg", "Rosberg");
        p11.addKrillion("Johnny Herbert", "Herbert");
        p11.addKrillion("Peter Gethin", "Gethin");
        list.add(p11);

        // 12. Silverstone Winners
        Prompt p12 = new Prompt("Name a driver who has won at Silverstone (British GP)", "silverstone");
        p12.addCommon("Lewis Hamilton", "Hamilton");
        p12.addCommon("Max Verstappen", "Verstappen");
        p12.addCommon("Michael Schumacher", "Schumacher");
        p12.addCommon("Carlos Sainz", "Sainz");
        p12.addCommon("Sebastian Vettel", "Vettel");
        p12.addUncommon("Fernando Alonso", "Alonso");
        p12.addUncommon("Nigel Mansell", "Mansell");
        p12.addUncommon("Alain Prost", "Prost");
        p12.addUncommon("Nico Rosberg", "Rosberg");
        p12.addUncommon("Mark Webber", "Webber");
        p12.addRare("David Coulthard", "Coulthard");
        p12.addRare("Johnny Herbert", "Herbert");
        p12.addRare("Damon Hill");
        p12.addKrillion("Peter Revson", "Revson");
        p12.addKrillion("Jo Siffert", "Siffert");
        list.add(p12);

        return list;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            F1Krillion game = new F1Krillion();
            game.setVisible(true);
        });
    }
}