package config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConnectDatabase {
	public static Connection getConnection() {
		Connection con = null;
		try {
			// Load driver
			Class.forName("com.mysql.cj.jdbc.Driver");

			// Thay username, password và dbname
			String url = "jdbc:mysql://localhost:3306/chat_app?useSSL=false&serverTimezone=UTC";
			String user = "root"; // user MySQL của bạn
			String password = "12345"; // password MySQL của bạn

			// Kết nối
			con = DriverManager.getConnection(url, user, password);
			System.out.println("Kết nối thành công!");
		} catch (ClassNotFoundException e) {
			System.out.println("Driver MySQL không tìm thấy!");
			e.printStackTrace();
		} catch (SQLException e) {
			System.out.println("Lỗi kết nối DB!");
			e.printStackTrace();
		}
		return con;
	}

	public static boolean registerUser(String username, String password, String phone, String gender, String createAt) {
		String sqlCheck = "SELECT * FROM users WHERE username=?";
		String sqlInsert = "INSERT INTO users ( username, password, phonenumber, gender, createat) VALUES ( ?, ?, ?, ?, ?)";

		try (Connection con = getConnection()) {
			try (PreparedStatement ps = con.prepareStatement(sqlCheck)) {
				ps.setString(1, username);
				ResultSet rs = ps.executeQuery();
				if (rs.next()) {
					System.out.println("Tên đăng nhập đã tồn tại!");
					return false;
				}
			}

			try (PreparedStatement ps = con.prepareStatement(sqlInsert)) {
				ps.setString(1, username);
				ps.setString(2, password);
				ps.setString(3, phone);
				ps.setString(4, gender);
				ps.setString(5, createAt);
				int rows = ps.executeUpdate();
				return rows > 0;
			}

		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	// Lưu tin nhắn
	public static void saveMessage(int senderId, int receiverId, String content) {
		String sql = "INSERT INTO messages (sender_id, receiver_id, content) VALUES (?, ?, ?)";
		try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, senderId);
			ps.setInt(2, receiverId);
			ps.setString(3, content);
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// Lấy lịch sử giữa 2 user (trả về: senderName, content, sent_at)
	public static java.util.List<String[]> getMessagesBetween(int userA, int userB) {
		String sql = "SELECT u.username AS sender_name, m.content, DATE_FORMAT(m.sent_at,'%Y-%m-%d %H:%i:%s') AS ts "
				+ "FROM messages m JOIN users u ON u.id = m.sender_id "
				+ "WHERE (m.sender_id=? AND m.receiver_id=?) OR (m.sender_id=? AND m.receiver_id=?) "
				+ "ORDER BY m.sent_at ASC";
		java.util.List<String[]> rows = new java.util.ArrayList<>();
		try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, userA);
			ps.setInt(2, userB);
			ps.setInt(3, userB);
			ps.setInt(4, userA);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				rows.add(new String[] { rs.getString("sender_name"), rs.getString("content"), rs.getString("ts") });
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return rows;
	}

	public static boolean checkLogin(String username, String password) {
		String sql = "SELECT * FROM users WHERE username=? AND password=?";
		try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setString(1, username);
			ps.setString(2, password);

			ResultSet rs = ps.executeQuery();
			return rs.next(); // true nếu có user tồn tại

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public static int idUser(String username, String password) {
		String sql = "SELECT id FROM users WHERE username=? AND password=?";
		try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setString(1, username);
			ps.setString(2, password);

			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				return rs.getInt("id"); // trả về id nếu login đúng
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return -1; // -1 = login fail
	}

	public static List<String[]> getGroupsOfUser(int userId) {
		List<String[]> groups = new ArrayList<>();
		try (Connection conn = getConnection()) {
			PreparedStatement ps = conn.prepareStatement("SELECT g.id, g.name " + "FROM chat_groups g "
					+ "JOIN chat_group_members m ON g.id = m.group_id " + "WHERE m.user_id = ?");
			ps.setInt(1, userId);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				groups.add(new String[] { rs.getString("id"), rs.getString("name") });
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return groups;
	}

	public static int createGroup(String name, int creatorId, java.util.List<Integer> memberIds) {
		String sqlGroup = "INSERT INTO chat_groups(name, created_by) VALUES(?, ?)";
		String sqlMember = "INSERT INTO chat_group_members(group_id, user_id) VALUES(?, ?)";
		try (Connection con = getConnection()) {
			con.setAutoCommit(false);
			int groupId;
			try (PreparedStatement ps = con.prepareStatement(sqlGroup, java.sql.Statement.RETURN_GENERATED_KEYS)) {
				ps.setString(1, name);
				ps.setInt(2, creatorId);
				ps.executeUpdate();
				try (ResultSet rs = ps.getGeneratedKeys()) {
					rs.next();
					groupId = rs.getInt(1);
				}
			}
			java.util.Set<Integer> all = new java.util.HashSet<>(memberIds);
			all.add(creatorId);
			try (PreparedStatement ps = con.prepareStatement(sqlMember)) {
				for (int uid : all) {
					ps.setInt(1, groupId);
					ps.setInt(2, uid);
					ps.addBatch();
				}
				ps.executeBatch();
			}
			con.commit();
			con.setAutoCommit(true);
			return groupId;
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
	}

	public static java.util.List<String[]> getGroupHistory(int groupId) {
		String sql = "SELECT u.username AS sender_name, gm.content, DATE_FORMAT(gm.sent_at,'%Y-%m-%d %H:%i:%s') ts "
				+ "FROM chat_group_messages gm JOIN users u ON u.id=gm.sender_id "
				+ "WHERE gm.group_id=? ORDER BY gm.sent_at ASC";
		java.util.List<String[]> rows = new java.util.ArrayList<>();
		try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, groupId);
			ResultSet rs = ps.executeQuery();
			while (rs.next())
				rows.add(new String[] { rs.getString("sender_name"), rs.getString("content"), rs.getString("ts") });
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return rows;
	}

	public static java.util.List<Integer> getGroupMemberIds(int groupId) {
		String sql = "SELECT user_id FROM chat_group_members WHERE group_id=?";
		java.util.List<Integer> ids = new java.util.ArrayList<>();
		try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, groupId);
			ResultSet rs = ps.executeQuery();
			while (rs.next())
				ids.add(rs.getInt(1));
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return ids;
	}

	public static String getGroupName(int groupId) {
		String sql = "SELECT name FROM chat_groups WHERE id=?";
		try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, groupId);
			ResultSet rs = ps.executeQuery();
			if (rs.next())
				return rs.getString(1);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return "Group#" + groupId;
	}

	public static void saveGroupMessage(int groupId, int senderId, String content) {
		String sql = "INSERT INTO chat_group_messages(group_id, sender_id, content) VALUES (?, ?, ?)";
		try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, groupId);
			ps.setInt(2, senderId);
			ps.setString(3, content);
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static Map<Integer, String> getAllUsers() {
		Map<Integer, String> users = new HashMap<>();
		String sql = "SELECT id, username FROM users";

		try (Connection con = getConnection();
				PreparedStatement ps = con.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {

			while (rs.next()) {
				int id = rs.getInt("id");
				String name = rs.getString("username");
				users.put(id, name); // lưu id -> name
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return users;
	}
	
	// rời nhóm 
	
	
	public static boolean leaveGroup(int userId, int groupId) {
	    String sql = "DELETE FROM chat_group_members WHERE user_id = ? AND group_id = ?";
	    try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
	        ps.setInt(1, userId);
	        ps.setInt(2, groupId);
	        int rows = ps.executeUpdate();
	        return rows > 0; // true nếu xóa thành công
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }
	    return false;
	}

}
