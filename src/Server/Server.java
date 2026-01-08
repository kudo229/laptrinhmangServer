package Server;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.JPanel;
import common.Protocol;
import config.ConnectDatabase;
import view.ServerView;

public class Server implements Runnable {

	private final int PORT = 2209;
	private ServerView view;
	private ServerSocket server;
	private static List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
	private static Map<Integer, ClientHandler> clientsById = new ConcurrentHashMap<>();

	public Server(ServerView view) {
		this.view = view;
	}

	@Override
	public void run() {
		try {
			server = new ServerSocket(PORT);
			view.addMessage("[INFO] Server started on port " + PORT);

			while (true) {
				Socket socket = server.accept();
				new Thread(() -> handleClient(socket)).start();
			}
		} catch (IOException e) {
			view.addMessage("[ERROR] Server Error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void handleClient(Socket socket) {
		String clientUsername = "Unknown";
		try {
			DataInputStream dis = new DataInputStream(socket.getInputStream());
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

			String firstToken = dis.readUTF();

			// --- XỬ LÝ ĐĂNG KÝ ---
			if ("REGISTER".equals(firstToken)) {
				String username = dis.readUTF();
				String password = dis.readUTF();
				String phone = dis.readUTF();
				String gender = dis.readUTF();
				String createAt = dis.readUTF();

				boolean ok = ConnectDatabase.registerUser(username, password, phone, gender, createAt);
				dos.writeUTF(ok ? "REGISTER_SUCCESS" : "REGISTER_FAILED");
				dos.flush();
				view.addMessage("[REGISTER] " + username + (ok ? " thành công." : " thất bại."));
				socket.close();
				return;
			}

			// --- XỬ LÝ ĐĂNG NHẬP ---
			String username = firstToken;
			String password = dis.readUTF();

			if (!ConnectDatabase.checkLogin(username, password)) {
				dos.writeUTF("LOGIN_FAILED");
				dos.flush();
				socket.close();
				return;
			}

			int userId = ConnectDatabase.idUser(username, password);
			clientUsername = username; 
			
			dos.writeUTF("LOGIN_SUCCESS");
			dos.flush();

			view.addMessage("[LOGIN] " + username + " đã kết nối từ " + socket.getInetAddress().getHostAddress());
			view.addParticipant(username, null); 

			ClientHandler client = new ClientHandler(userId, username, socket, dis, dos);
			clients.add(client);
			clientsById.put(userId, client);

			sendFriendList(client);
			sendUserGroups(client);

			// --- LUỒNG NHẬN LỆNH CHÍNH ---
			while (true) {
				String type = dis.readUTF();

				if (type.equals("CREATE_GROUP")) {
					handleCreateGroup(client, dis);
				} else if (type.equals("GET_GROUP_HISTORY")) {
					handleGetGroupHistory(client, dis);
				} else if (type.equals("GROUP_MSG")) {
					handleGroupMsg(client, dis);
				} 
				// ==== VIDEO & AUDIO CALL ====
				else if (type.equals(Protocol.CMD_VIDEO_CALL_REQUEST)) {
					handleVideoCallRequest(client, dis);
				} else if (type.equals(Protocol.CMD_VIDEO_CALL_ACCEPT)) {
					handleVideoCallAccept(client, dis);
				} else if (type.equals(Protocol.CMD_VIDEO_CALL_REJECT)) {
					handleVideoCallReject(client, dis);
				} else if (type.equals(Protocol.CMD_VIDEO_CALL_END)) {
					handleVideoCallEnd(client, dis);
				} else if (type.equals(Protocol.CMD_VIDEO_FRAME) || type.equals(Protocol.CMD_AUDIO_FRAME)) {
					forwardMediaFrame(client, dis, type);
				}
				// ==== FILE & TIN NHẮN ====
				else if (type.equals("SEND_FILE")) {
					int toUserId = dis.readInt();
					receiveAndSendFileToUser(client, toUserId);
				} else if (type.equals("SEND_GROUP_FILE")) {
					int groupId = dis.readInt();
					receiveAndSendFileToGroup(client, groupId);
				} else if (type.equals("DM")) {
					int toUserId = dis.readInt();
					String content = dis.readUTF();
					ConnectDatabase.saveMessage(client.userId, toUserId, content);
					sendDirectMessage(client, toUserId, content);
				} else if (type.equals("GET_HISTORY")) {
					handleGetPrivateHistory(client, dis);
				} else if (type.equals("LEAVE_GROUP")) {
					handleLeaveGroup(client, dis);
				} else {
					broadcastChat(client.username + ": " + type, "BROADCAST");
					view.addMessage("[CHAT ALL] " + client.username + ": " + type);
				}
			}

		} catch (IOException e) {
		} finally {
			removeClient(clientUsername);
			view.addMessage("[QUIT] " + clientUsername + " đã ngắt kết nối.");
		}
	}

	// --- CÁC HÀM HỖ TRỢ LOGIC ---

	private void sendUserGroups(ClientHandler client) throws IOException {
		List<String[]> groups = ConnectDatabase.getGroupsOfUser(client.userId);
		for (String[] g : groups) {
			client.dos.writeUTF("GROUP_CREATED");
			client.dos.writeInt(Integer.parseInt(g[0]));
			client.dos.writeUTF(g[1]);
			client.dos.flush();
		}
	}

	private void handleCreateGroup(ClientHandler client, DataInputStream dis) throws IOException {
		String groupName = dis.readUTF();
		int count = dis.readInt();
		List<Integer> members = new ArrayList<>();
		for (int i = 0; i < count; i++) members.add(dis.readInt());

		int gid = ConnectDatabase.createGroup(groupName, client.userId, members);
		members.add(client.userId);
		
		view.addMessage("[GROUP] " + client.username + " vừa tạo nhóm mới: " + groupName);

		for (int uid : members) {
			ClientHandler ch = clientsById.get(uid);
			if (ch != null) {
				ch.dos.writeUTF("GROUP_CREATED");
				ch.dos.writeInt(gid);
				ch.dos.writeUTF(groupName);
				ch.dos.flush();
			}
		}
	}

	private void handleGroupMsg(ClientHandler client, DataInputStream dis) throws IOException {
		int gid = dis.readInt();
		String content = dis.readUTF();
		ConnectDatabase.saveGroupMessage(gid, client.userId, content);

		String groupName = ConnectDatabase.getGroupName(gid);
		
		// LOG TIN NHẮN NHÓM
view.addMessage("[GROUP: " + groupName + "] " + client.username + ": " + content);

		List<Integer> memberIds = ConnectDatabase.getGroupMemberIds(gid);
		for (int uid : memberIds) {
			// ✅ SỬA: Kiểm tra uid khác với người gửi để không bị lặp tin nhắn
			if (uid != client.userId) { 
				ClientHandler ch = clientsById.get(uid);
				if (ch != null) {
					ch.dos.writeUTF("GROUP_MSG");
					ch.dos.writeInt(gid);
					ch.dos.writeUTF(groupName);
					ch.dos.writeUTF(client.username);
					ch.dos.writeUTF(content);
					ch.dos.flush();
				}
			}
		}
	}

	// --- VIDEO CALL LOGIC ---

	private void handleVideoCallRequest(ClientHandler client, DataInputStream dis) throws IOException {
		int toUserId = dis.readInt();
		client.videoTargetUserId = toUserId;
		ClientHandler receiver = clientsById.get(toUserId);
		if (receiver != null) {
			// LOG YÊU CẦU GỌI
			view.addMessage("[VIDEO CALL] " + client.username + " đang gọi video cho " + receiver.username);
			
			receiver.dos.writeUTF(Protocol.RESP_VIDEO_CALL_INCOMING);
			receiver.dos.writeInt(client.userId);
			receiver.dos.writeUTF(client.username);
			receiver.dos.flush();
		}
	}

	private void handleVideoCallAccept(ClientHandler client, DataInputStream dis) throws IOException {
		int callerId = dis.readInt();
		ClientHandler caller = clientsById.get(callerId);
		if (caller != null) {
			// LOG CHẤP NHẬN
			view.addMessage("[VIDEO CALL] " + client.username + " đã chấp nhận cuộc gọi từ " + caller.username);
			
			caller.dos.writeUTF(Protocol.RESP_VIDEO_CALL_ACCEPTED);
			caller.dos.writeInt(client.userId);
			caller.dos.writeUTF(client.username);
			caller.dos.flush();
			caller.videoTargetUserId = client.userId;
		}
		client.videoTargetUserId = callerId;
	}

	private void handleVideoCallReject(ClientHandler client, DataInputStream dis) throws IOException {
		int callerId = dis.readInt();
		ClientHandler caller = clientsById.get(callerId);
		if (caller != null) {
			// LOG TỪ CHỐI
			view.addMessage("[VIDEO CALL] " + client.username + " đã từ chối cuộc gọi từ " + caller.username);
			
			caller.dos.writeUTF(Protocol.RESP_VIDEO_CALL_REJECTED);
			caller.dos.writeUTF(client.username + " đã từ chối cuộc gọi.");
			caller.dos.flush();
		}
	}

	private void handleVideoCallEnd(ClientHandler client, DataInputStream dis) throws IOException {
		int partnerId = dis.readInt();
		ClientHandler partner = clientsById.get(partnerId);
		if (partner != null) {
			// LOG KẾT THÚC
			view.addMessage("[VIDEO CALL] Cuộc gọi giữa " + client.username + " và " + partner.username + " đã kết thúc.");
			
			partner.dos.writeUTF(Protocol.RESP_VIDEO_CALL_ENDED);
			partner.dos.flush();
			partner.videoTargetUserId = null;
		}
		client.videoTargetUserId = null;
	}

	private void forwardMediaFrame(ClientHandler client, DataInputStream dis, String type) throws IOException {
		int len = dis.readInt();
		byte[] bytes = new byte[len];
		dis.readFully(bytes);
		Integer toUserId = client.videoTargetUserId;
		if (toUserId != null) {
			ClientHandler receiver = clientsById.get(toUserId);
			if (receiver != null) {
				synchronized (receiver.dos) {
					receiver.dos.writeUTF(type);
					receiver.dos.writeInt(len);
					receiver.dos.write(bytes);
					receiver.dos.flush();
				}
			}
		}
	}

	// --- MESSAGE & FILE ---

	public void broadcastChat(String message, String protocolType) {
		synchronized (clients) {
			for (ClientHandler client : clients) {
				try {
					client.dos.writeUTF(protocolType);
					client.dos.writeUTF(message);
					client.dos.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void sendDirectMessage(ClientHandler sender, int toUserId, String content) throws IOException {
		ClientHandler receiver = clientsById.get(toUserId);
		if (receiver != null) {
			// LOG TIN NHẮN RIÊNG
			view.addMessage("[DM] " + sender.username + " -> " + receiver.username + ": " + content);
			
			receiver.dos.writeUTF("DM");
			receiver.dos.writeUTF(sender.username);
			receiver.dos.writeUTF(content);
			receiver.dos.flush();
		}
	}

	private void handleGetPrivateHistory(ClientHandler client, DataInputStream dis) throws IOException {
		int otherUserId = dis.readInt();
		List<String[]> rows = ConnectDatabase.getMessagesBetween(client.userId, otherUserId);
		client.dos.writeUTF("HISTORY");
		client.dos.writeInt(otherUserId);
		client.dos.writeInt(rows.size());
		for (String[] r : rows) {
			client.dos.writeUTF(r[0]); 
			client.dos.writeUTF(r[1]); 
			client.dos.writeUTF(r[2]); 
		}
		client.dos.flush();
	}

	private void handleGetGroupHistory(ClientHandler client, DataInputStream dis) throws IOException {
		int gid = dis.readInt();
		List<String[]> rows = ConnectDatabase.getGroupHistory(gid);
		client.dos.writeUTF("GROUP_HISTORY");
		client.dos.writeInt(gid);
		client.dos.writeInt(rows.size());
		for (String[] r : rows) {
			client.dos.writeUTF(r[0]);
			client.dos.writeUTF(r[1]);
			client.dos.writeUTF(r[2]);
		}
		client.dos.flush();
	}

	private void handleLeaveGroup(ClientHandler client, DataInputStream dis) throws IOException {
		int groupId = dis.readInt();
		boolean ok = ConnectDatabase.leaveGroup(client.userId, groupId);
		if (ok) {
			view.addMessage("[GROUP] " + client.username + " đã rời khỏi nhóm ID: " + groupId);
			
			client.dos.writeUTF("LEAVE_GROUP_SUCCESS");
			client.dos.writeInt(groupId);
			client.dos.flush();

			List<Integer> members = ConnectDatabase.getGroupMemberIds(groupId);
			for (int uid : members) {
				ClientHandler ch = clientsById.get(uid);
				if (ch != null) {
					ch.dos.writeUTF("GROUP_MEMBER_LEFT");
					ch.dos.writeInt(groupId);
					ch.dos.writeUTF(client.username);
					ch.dos.flush();
				}
			}
		}
	}

	private void receiveAndSendFileToUser(ClientHandler sender, int toUserId) throws IOException {
		String fileName = sender.dis.readUTF();
		long fileSize = sender.dis.readLong();
byte[] fileData = new byte[(int) fileSize];
		sender.dis.readFully(fileData);

		ClientHandler receiver = clientsById.get(toUserId);
		if (receiver != null) {
			// LOG GỬI FILE
			view.addMessage("[FILE] " + sender.username + " gửi file '" + fileName + "' cho " + receiver.username);
			
			receiver.dos.writeUTF("FILE");
			receiver.dos.writeUTF(fileName);
			receiver.dos.writeLong(fileSize);
			receiver.dos.write(fileData);
			receiver.dos.flush();
		}
	}

	private void receiveAndSendFileToGroup(ClientHandler sender, int groupId) throws IOException {
		String fileName = sender.dis.readUTF();
		long fileSize = sender.dis.readLong();
		byte[] fileData = new byte[(int) fileSize];
		sender.dis.readFully(fileData);

		// LOG GỬI FILE NHÓM
		view.addMessage("[GROUP FILE] " + sender.username + " gửi file '" + fileName + "' vào nhóm ID: " + groupId);

		List<Integer> memberIds = ConnectDatabase.getGroupMemberIds(groupId);
		for (int uid : memberIds) {
			// ✅ KHẮC PHỤC: Đã thêm dòng lấy ClientHandler ch từ clientsById
			ClientHandler ch = clientsById.get(uid); 
			if (ch != null && ch.userId != sender.userId) {
				ch.dos.writeUTF("FILE");
				ch.dos.writeUTF(fileName);
				ch.dos.writeLong(fileSize);
				ch.dos.write(fileData);
				ch.dos.flush();
			}
		}
	}

	private void sendFriendList(ClientHandler client) throws IOException {
		Map<Integer, String> allUsers = ConnectDatabase.getAllUsers();
		client.dos.writeInt(allUsers.size() - 1);
		for (Map.Entry<Integer, String> entry : allUsers.entrySet()) {
			if (entry.getKey() != client.userId) {
				client.dos.writeInt(entry.getKey());
				client.dos.writeUTF(entry.getValue());
			}
		}
		client.dos.flush();
	}

	public void removeClient(String username) {
		synchronized (clients) {
			Iterator<ClientHandler> it = clients.iterator();
			while (it.hasNext()) {
				ClientHandler ch = it.next();
				if (ch.username.equals(username)) {
					try {
						ch.socket.close();
					} catch (IOException e) {}
					clientsById.remove(ch.userId);
					it.remove();
					view.removeParticipant(username); 
					break;
				}
			}
		}
	}

	static class ClientHandler {
		int userId;
		String username;
		Socket socket;
		DataInputStream dis;
		DataOutputStream dos;
		Integer videoTargetUserId = null;

		public ClientHandler(int userId, String username, Socket socket, DataInputStream dis, DataOutputStream dos) {
			this.userId = userId;
			this.username = username;
			this.socket = socket;
			this.dis = dis;
			this.dos = dos;
		}
	}

	public static void main(String[] args) {
		javax.swing.SwingUtilities.invokeLater(() -> {
			ServerView view = new ServerView();
			view.setVisible(true);
			Server server = new Server(view);
			view.setServer(server);
			new Thread(server).start();
		});
	}
}