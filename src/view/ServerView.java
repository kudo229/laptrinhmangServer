package view;

import javax.swing.*;


import Server.Server;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.*;

public class ServerView extends JFrame {
    public JTextArea chatArea;
    public Server server;
    public JPanel participantPanel;
 
    public void setServer(Server server) {
        this.server = server;
    }

    public void addMessage(String message) {
        chatArea.append(message + "\n");
    }

    public JTextArea getChatArea() {
        return chatArea;
    }

    // Thêm một panel mới cho người tham gia cuộc trò chuyện
    public void addParticipant(String username, JPanel participantPanel) {
        JPanel newParticipantPanel = new JPanel();
        newParticipantPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        newParticipantPanel.setBackground(new Color(255, 255, 255));

        JLabel participantName = new JLabel(username);
        JButton removeButton = new JButton("Xóa");

        // Xử lý khi nhấn nút "Xóa"
        removeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Xóa người tham gia khỏi cuộc trò chuyện
                removeParticipant(newParticipantPanel, username);
            }
        });

        newParticipantPanel.add(participantName);
        newParticipantPanel.add(removeButton);
        participantPanel.add(newParticipantPanel);
        participantPanel.revalidate();
        participantPanel.repaint();
    }

    // Xóa người tham gia khỏi cuộc trò chuyện
    public void removeParticipant(JPanel participantPanel, String username) {
        // Xóa người dùng khỏi server
        server.removeClient(username);
        // Loại bỏ panel khỏi giao diện
        participantPanel.removeAll();
        participantPanel.revalidate();
        participantPanel.repaint();
    }

    public ServerView() {
        getContentPane().setBackground(new Color(0, 64, 128));
        getContentPane().setForeground(new Color(0, 64, 128));
        setTitle("Chat Server");
        setSize(600, 491);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setLayout(null);

        chatArea = new JTextArea();
        chatArea.setFont(new Font("Tahoma", Font.BOLD, 12));
        chatArea.setMargin(new Insets(10, 10, 10, 10));
        chatArea.setBounds(30, 57, 531, 221);
        getContentPane().add(chatArea);

        JLabel lblNewLabel = new JLabel("SBTC Server Messenger");
        lblNewLabel.setForeground(new Color(255, 255, 255));
        lblNewLabel.setFont(new Font("Tahoma", Font.BOLD, 20));
        lblNewLabel.setBounds(185, 10, 257, 37);
        getContentPane().add(lblNewLabel);

        // Panel chứa danh sách người tham gia
        participantPanel = new JPanel();
        participantPanel.setLayout(new BoxLayout(participantPanel, BoxLayout.Y_AXIS));
        JScrollPane participantScrollPane = new JScrollPane(participantPanel);
        participantScrollPane.setBounds(124, 288, 354, 142);
        getContentPane().add(participantScrollPane);
    }
}