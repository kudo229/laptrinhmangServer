package view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import Server.Server;

public class ServerView extends JFrame {
    private JTextArea logArea;
    private DefaultListModel<String> onlineListModel;
    private JList<String> onlineList;
    private JTextField msgField;
    private JButton btnSendAll;
    private Server server;

    public ServerView() {
        setTitle("Server Monitor - SBTC Messenger");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // --- Thiết lập giao diện màu sắc ---
        Color backgroundColor = new Color(245, 245, 245);
        Font mainFont = new Font("Segoe UI", Font.PLAIN, 14);
        Font titleFont = new Font("Segoe UI", Font.BOLD, 16);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(backgroundColor);
        setContentPane(mainPanel);

        // --- PHẦN GIỮA (Split Pane) ---
        // 1. Bên trái: Danh sách Online
        onlineListModel = new DefaultListModel<>();
        onlineList = new JList<>(onlineListModel);
        onlineList.setFont(mainFont);
        
        // Tạo Menu Chuột Phải (Popup Menu)
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem kickItem = new JMenuItem("Kick (Ngắt kết nối)");
        kickItem.setForeground(Color.RED);
        kickItem.setIcon(UIManager.getIcon("OptionPane.errorIcon")); // Icon cảnh báo nhỏ
        popupMenu.add(kickItem);

        // Lắng nghe sự kiện chuột trên danh sách
        onlineList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    // Xác định xem chuột nhấn vào dòng nào
                    int index = onlineList.locationToIndex(e.getPoint());
                    if (index != -1) {
                        onlineList.setSelectedIndex(index); // Tự động chọn dòng đó
                        popupMenu.show(onlineList, e.getX(), e.getY()); // Hiện menu
                    }
                }
            }
        });

        // Xử lý khi nhấn nút "Kick"
        kickItem.addActionListener(e -> {
            String selectedUser = onlineList.getSelectedValue();
            if (selectedUser != null) {
                int confirm = JOptionPane.showConfirmDialog(this, 
                    "Bạn có chắc muốn đuổi " + selectedUser + " ra khỏi server?", 
                    "Xác nhận Kick", JOptionPane.YES_NO_OPTION);
                
                if (confirm == JOptionPane.YES_OPTION) {
                    server.removeClient(selectedUser); // Gọi hàm xóa trong Server
                    addMessage("[KICKED] Admin đã đuổi người dùng: " + selectedUser);
                }
            }
        });

        // Custom giao diện dòng trong list
        onlineList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(new EmptyBorder(8, 10, 8, 10));
                if (!isSelected) {
                    label.setBackground(index % 2 == 0 ? Color.WHITE : new Color(248, 248, 248));
                }
                return label;
            }
        });

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(new TitledBorder(new LineBorder(Color.LIGHT_GRAY), "Clients Online", TitledBorder.LEFT, TitledBorder.TOP, titleFont));
        leftPanel.add(new JScrollPane(onlineList), BorderLayout.CENTER);

        // 2. Bên phải: Log hệ thống
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(Color.WHITE);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(new TitledBorder(new LineBorder(Color.LIGHT_GRAY), "Server Log", TitledBorder.LEFT, TitledBorder.TOP, titleFont));
        rightPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(250);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // --- PHẦN DƯỚI (Gửi thông báo) ---
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(5, 0, 0, 0));

        msgField = new JTextField();
        msgField.setFont(mainFont);
        btnSendAll = new JButton("Gửi Tất Cả");
        btnSendAll.setFocusPainted(false);

        bottomPanel.add(new JLabel("Thông báo hệ thống: "), BorderLayout.WEST);
        bottomPanel.add(msgField, BorderLayout.CENTER);
        bottomPanel.add(btnSendAll, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Sự kiện gửi thông báo
        ActionListener sendAction = e -> {
            String msg = msgField.getText().trim();
            if (!msg.isEmpty() && server != null) {
                server.broadcastChat("THÔNG BÁO TỪ ADMIN: " + msg, "BROADCAST");
                addMessage("[GLOBAL MSG] " + msg);
                msgField.setText("");
            }
        };
        btnSendAll.addActionListener(sendAction);
        msgField.addActionListener(sendAction);
    }

    // --- CÁC PHƯƠNG THỨC ĐỒNG BỘ VỚI SERVER.JAVA ---

    public void addMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void addParticipant(String username, JPanel unused) {
        SwingUtilities.invokeLater(() -> {
            if (!onlineListModel.contains(username)) {
                onlineListModel.addElement(username);
            }
        });
    }

    public void removeParticipant(String username) {
        SwingUtilities.invokeLater(() -> {
            onlineListModel.removeElement(username);
        });
    }

    public void setServer(Server server) {
        this.server = server;
    }
}