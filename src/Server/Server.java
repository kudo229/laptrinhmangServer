package Server;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

import javax.swing.JPanel;

import Server.Server.ClientHandler;
import common.Protocol;
import config.ConnectDatabase;
import view.ServerView;

public class Server implements Runnable {

	private final int PORT = 2209;
	private ServerView view;
	private ServerSocket server;
//    private ExecutorService pool;
	// Danh sách client, kiểu ChatServer
	private static List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
	// NEW: Bảng tra cứu theo userId
	private static Map<Integer, ClientHandler> clientsById = new ConcurrentHashMap<>();

	public Server(ServerView view) {
		this.view = view;
	}

	@Override
	public void run() {
		try {
			server = new ServerSocket(PORT);
//            pool = Executors.newCachedThreadPool();
			view.addMessage("Server started on port " + PORT);

			while (true) {
				Socket socket = server.accept();
				new Thread(() -> handleClient(socket)).start();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void handleClient(Socket socket) {
		try {
			DataInputStream dis = new DataInputStream(socket.getInputStream());
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

			// --- Đọc lệnh đầu tiên ---
			String firstToken = dis.readUTF();

			// Nếu là lệnh REGISTER -> xử lý đăng ký
			if ("REGISTER".equals(firstToken)) {
				String username = dis.readUTF();
				String password = dis.readUTF();
				String phone = dis.readUTF();
				String gender = dis.readUTF();
				String createAt = dis.readUTF();

				boolean ok = ConnectDatabase.registerUser(username, password, phone, gender, createAt);

				if (ok) {
					dos.writeUTF("REGISTER_SUCCESS");
					view.addMessage("Người đăng ký mới: " + username);
				} else {
					dos.writeUTF("REGISTER_FAILED");
					view.addMessage("Đăng ký thất bại: " + username);
				}
				dos.flush();
				socket.close(); // Đóng sau khi đăng ký xong
				return;
			}

			// --- Còn lại là luồng LOGIN như cũ ---
			String username = firstToken;
			String password = dis.readUTF();

			if (!ConnectDatabase.checkLogin(username, password)) {
				dos.writeUTF("LOGIN_FAILED");
				dos.flush();
				socket.close();
				return;
			}

			int userId = ConnectDatabase.idUser(username, password);
			dos.writeUTF("LOGIN_SUCCESS");
			dos.flush();
			view.addMessage(username + " đã đăng nhập thành công." + socket.getInetAddress().getHostAddress());

			view.addParticipant(username, view.participantPanel);
			ClientHandler client = new ClientHandler(userId, username, socket, dis, dos);
			clients.add(client);
			clientsById.put(userId, client);

			sendFriendList(client);

			// === Gửi danh sách nhóm mà user thuộc về ===
			List<String[]> groups = ConnectDatabase.getGroupsOfUser(userId);
			for (String[] g : groups) {
				int gid = Integer.parseInt(g[0]);
				String gname = g[1];

				try {
					dos.writeUTF("GROUP_CREATED");
					dos.writeInt(gid);
					dos.writeUTF(gname);
					dos.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

// nguyen bo sung them ca thieu

			while (true) {
				String type = dis.readUTF();

				if (type.equals("CREATE_GROUP")) {
					String groupName = dis.readUTF();
					int count = dis.readInt();
					java.util.List<Integer> members = new java.util.ArrayList<>();
					for (int i = 0; i < count; i++)
						members.add(dis.readInt());

					int gid = ConnectDatabase.createGroup(groupName, client.userId, members);

					// Gửi thông báo GROUP_CREATED cho tất cả thành viên nhóm
					String finalGroupName = groupName;
					java.util.List<Integer> allMembers = new java.util.ArrayList<>(members);
					allMembers.add(client.userId); // thêm cả người tạo nhóm

					for (int uid : allMembers) {
						ClientHandler ch = clientsById.get(uid);
						if (ch != null) {
							try {
								ch.dos.writeUTF("GROUP_CREATED");
								ch.dos.writeInt(gid);
								ch.dos.writeUTF(finalGroupName);
								ch.dos.flush();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				} else if (type.equals("GET_GROUP_HISTORY")) {
					int gid = dis.readInt();
					java.util.List<String[]> rows = ConnectDatabase.getGroupHistory(gid);
					dos.writeUTF("GROUP_HISTORY");
					dos.writeInt(gid);
					dos.writeInt(rows.size());
					for (String[] r : rows) {
						dos.writeUTF(r[0]); // senderName
						dos.writeUTF(r[1]); // content
						dos.writeUTF(r[2]); // ts
					}
					dos.flush();

				} else if (type.equals("GROUP_MSG")) {
					int gid = dis.readInt();
					String content = dis.readUTF();
					ConnectDatabase.saveGroupMessage(gid, client.userId, content);

					// phát cho các thành viên online trong nhóm (trừ người gửi)
					String groupName = ConnectDatabase.getGroupName(gid);
					java.util.List<Integer> memberIds = ConnectDatabase.getGroupMemberIds(gid);
					for (int uid : memberIds) {
						ClientHandler ch = clientsById.get(uid);
						if (ch != null) {
							try {
								ch.dos.writeUTF("GROUP_MSG");
								ch.dos.writeInt(gid);
								ch.dos.writeUTF(groupName);
								ch.dos.writeUTF(client.username); // senderName
								ch.dos.writeUTF(content);
								ch.dos.flush();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}

				} 
// nguyên thêm gọi video
				// ==== VIDEO CALL ====
				else if (type.equals(Protocol.CMD_VIDEO_CALL_REQUEST)) {
				    // client yêu cầu gọi video tới toUserId
				    int toUserId = dis.readInt();
				    client.videoTargetUserId = toUserId;

				    ClientHandler receiver = clientsById.get(toUserId);
				    if (receiver != null) {
				        receiver.dos.writeUTF(Protocol.RESP_VIDEO_CALL_INCOMING);
				        receiver.dos.writeInt(client.userId);      // ai gọi
				        receiver.dos.writeUTF(client.username);    // tên ai gọi
				        receiver.dos.flush();
				    }

				} else if (type.equals(Protocol.CMD_VIDEO_CALL_ACCEPT)) {
				    // người được gọi chấp nhận
				    int callerId = dis.readInt();                 // id người gọi
				    ClientHandler caller = clientsById.get(callerId);
				    if (caller != null) {
				        // báo cho caller biết đã được accept
				        caller.dos.writeUTF(Protocol.RESP_VIDEO_CALL_ACCEPTED);
				        caller.dos.writeInt(client.userId);       // calleeId
				        caller.dos.writeUTF(client.username);     // calleeName
				        caller.dos.flush();
				    }

				    // thiết lập quan hệ call 2 chiều
				    client.videoTargetUserId = callerId;
				    if (caller != null) caller.videoTargetUserId = client.userId;

				} else if (type.equals(Protocol.CMD_VIDEO_CALL_REJECT)) {
				    // người được gọi từ chối
				    int callerId = dis.readInt();
				    ClientHandler caller = clientsById.get(callerId);
				    if (caller != null) {
				        caller.dos.writeUTF(Protocol.RESP_VIDEO_CALL_REJECTED);
				        caller.dos.writeUTF(client.username + " đã từ chối cuộc gọi video.");
				        caller.dos.flush();
				    }

				} else if (type.equals(Protocol.CMD_VIDEO_CALL_END)) {
				    // một bên kết thúc
				    int partnerId = dis.readInt();
				    ClientHandler partner = clientsById.get(partnerId);
				    if (partner != null) {
				        partner.dos.writeUTF(Protocol.RESP_VIDEO_CALL_ENDED);
				        partner.dos.flush();
				    }

				    client.videoTargetUserId = null;
				    if (partner != null) partner.videoTargetUserId = null;

				} else if (type.equals(Protocol.CMD_VIDEO_FRAME)) {
				    // forward frame cho đối tác hiện tại
				    int len = dis.readInt();
				    byte[] bytes = new byte[len];
				    dis.readFully(bytes);

				    Integer toUserId = client.videoTargetUserId;
				    if (toUserId != null) {
				        ClientHandler receiver = clientsById.get(toUserId);
				        if (receiver != null) {
				            synchronized (receiver.dos) {
				                receiver.dos.writeUTF(Protocol.CMD_VIDEO_FRAME);
				                receiver.dos.writeInt(len);
				                receiver.dos.write(bytes);
				                receiver.dos.flush();
				            }
				        }
				    }
				}
// nguyen them Audio 
				
				else if (type.equals(Protocol.CMD_AUDIO_FRAME)) {
				    int len = dis.readInt();
				    byte[] bytes = new byte[len];
				    dis.readFully(bytes);

				    Integer toUserId = client.videoTargetUserId;
				    if (toUserId != null) {
				        ClientHandler receiver = clientsById.get(toUserId);
				        if (receiver != null) {
				            synchronized (receiver.dos) {
				                receiver.dos.writeUTF(Protocol.CMD_AUDIO_FRAME);
				                receiver.dos.writeInt(len);
				                receiver.dos.write(bytes);
				                receiver.dos.flush();
				            }
				        }
				    }
				}

				
				else if (type.equals("LEAVE_GROUP")) {
				    int groupId = dis.readInt();

				    boolean ok = ConnectDatabase.leaveGroup(client.userId, groupId);

				    if (ok) {
				        dos.writeUTF("LEAVE_GROUP_SUCCESS");
				        dos.writeInt(groupId);
				        dos.flush();

				        // Thông báo cho các thành viên còn lại
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
				    } else {
				        dos.writeUTF("LEAVE_GROUP_FAILED");
				        dos.flush();
				    }
				}

				
				else {
					if (type.equals("SEND_FILE")) {
						int toUserId = dis.readInt();
						receiveAndSendFileToUser(client, toUserId);
					} else if (type.equals("SEND_GROUP_FILE")) {
						int groupId = dis.readInt();
						receiveAndSendFileToGroup(client, groupId);
					} else if (type.equals("FILE")) {
						// Giữ lại nếu vẫn muốn hỗ trợ broadcast (không cần thiết)
						receiveAndBroadcastFile(client);
					}

					else if (type.equals("DM")) {
						int toUserId = dis.readInt();
						String content = dis.readUTF();

						// LƯU DB trước rồi mới chuyển tiếp
						ConnectDatabase.saveMessage(client.userId, toUserId, content);
						sendDirectMessage(client, toUserId, content);

						view.addMessage("(DM) " + client.username + " → " + toUserId + ": " + content);

					} else if (type.equals("GET_HISTORY")) {
						int otherUserId = dis.readInt();
						// LẤY LỊCH SỬ & TRẢ VỀ
						java.util.List<String[]> rows = ConnectDatabase.getMessagesBetween(client.userId, otherUserId);

						dos.writeUTF("HISTORY");
						dos.writeInt(otherUserId); // để client biết lịch sử này của ai
						dos.writeInt(rows.size()); // số dòng
						for (String[] r : rows) {
							dos.writeUTF(r[0]); // senderName
							dos.writeUTF(r[1]); // content
							dos.writeUTF(r[2]); // timestamp (string)
						}
						dos.flush();

					} else {
						broadcastChat(client.username + ": " + type, client);
						view.addMessage(client.username + ": " + type);
					}
				}

			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void startVideoForward(ClientHandler sender, int toUserId) {
	    new Thread(() -> {
	        try {
	            ClientHandler receiver = clientsById.get(toUserId);
	            if (receiver == null) {
	                sender.dos.writeUTF("DM");
	                sender.dos.writeUTF("Hệ thống");
	                sender.dos.writeUTF("Người nhận không trực tuyến.");
	                sender.dos.flush();
	                return;
	            }

	            // Thông báo cho người nhận mở khung video
	            receiver.dos.writeUTF("START_VIDEO_INCOMING");
	            receiver.dos.writeUTF(sender.username);
	            receiver.dos.flush();

	            System.out.println("📡 Chuyển tiếp video từ " + sender.username + " → " + receiver.username);

	            while (true) {
	                int len = sender.dis.readInt();
	                byte[] bytes = new byte[len];
	                sender.dis.readFully(bytes);

	                synchronized (receiver.dos) {
	                    receiver.dos.writeUTF("VIDEO_FRAME");
	                    receiver.dos.writeInt(len);
	                    receiver.dos.write(bytes);
	                    receiver.dos.flush();
	                }
	            }
	        } catch (IOException e) {
	            System.out.println("🔴 Dừng truyền video giữa " + sender.username);
	        }
	    }).start();
	}


	// nguyen them

	private void sendDirectMessage(ClientHandler sender, int toUserId, String content) {
		ClientHandler receiver = clientsById.get(toUserId);
		if (receiver != null) {
			try {
				// Gửi cho người nhận
				receiver.dos.writeUTF("DM");
				receiver.dos.writeUTF(sender.username); // ai gửi
				receiver.dos.writeUTF(content); // nội dung
				receiver.dos.flush();

	            // (tuỳ chọn) Echo cho người gửi để hiển thị ngay
	            sender.dos.writeUTF("DM");
	            sender.dos.writeUTF("Bạn → " + receiver.username);
	            sender.dos.writeUTF(content);
	            sender.dos.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			// (tuỳ chọn) báo lại cho sender nếu người nhận offline
			try {
				sender.dos.writeUTF("DM");
				sender.dos.writeUTF("Hệ thống");
				sender.dos.writeUTF("Người nhận hiện không trực tuyến.");
				sender.dos.flush();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	// Broadcast chat
	private void broadcastChat(String message, ClientHandler sender) {
	    synchronized (clients) {
	        for (ClientHandler client : clients) {
	            try {
	                client.dos.writeUTF("BROADCAST");
	                client.dos.writeUTF(message);
	                client.dos.flush();
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	        }
	    }
	}


	// Gửi file đến tất cả client khác
	private void receiveAndBroadcastFile(ClientHandler sender) {
		try {
			String fileName = sender.dis.readUTF();
			long fileSize = sender.dis.readLong();
			
			File tempFile = new File(fileName);
			try (FileOutputStream fos = new FileOutputStream(tempFile)) {
				byte[] buffer = new byte[4096];
				int bytesRead;
				long remaining = fileSize;
				while (remaining > 0
						&& (bytesRead = sender.dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
					fos.write(buffer, 0, bytesRead);
					remaining -= bytesRead;
				}
			}

			byte[] allBytes = Files.readAllBytes(tempFile.toPath());

			synchronized (clients) {
				for (ClientHandler client : clients) {
					if (client != sender) {
						client.dos.writeUTF("FILE");
						client.dos.writeUTF(fileName);
						client.dos.writeLong(allBytes.length);
						client.dos.write(allBytes);
						client.dos.flush();
					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// === Gửi file riêng cho 1 người dùng ===
	private void receiveAndSendFileToUser(ClientHandler sender, int toUserId) {
		try {
			String fileName = sender.dis.readUTF();
			long fileSize = sender.dis.readLong();

			File tempFile = new File(fileName);
			try (FileOutputStream fos = new FileOutputStream(tempFile)) {
				byte[] buffer = new byte[4096];
				int bytesRead;
				long remaining = fileSize;
				while (remaining > 0
						&& (bytesRead = sender.dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
					fos.write(buffer, 0, bytesRead);
					remaining -= bytesRead;
				}
			}

			// Gửi lại file cho người nhận
			ClientHandler receiver = clientsById.get(toUserId);
			if (receiver != null) {
				byte[] allBytes = Files.readAllBytes(tempFile.toPath());
				receiver.dos.writeUTF("FILE");
				receiver.dos.writeUTF(fileName);
				receiver.dos.writeLong(allBytes.length);
				receiver.dos.write(allBytes);
				receiver.dos.flush();

				sender.dos.writeUTF("DM");
				sender.dos.writeUTF("Đã gửi file '" + fileName + "' cho " + receiver.username);
				sender.dos.flush();

				System.out.println("Đã gửi file riêng: " + fileName + " → " + receiver.username);
			} else {
				sender.dos.writeUTF("DM");
				sender.dos.writeUTF("Người nhận hiện không trực tuyến.");
				sender.dos.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// === Gửi file cho tất cả thành viên trong nhóm ===
	private void receiveAndSendFileToGroup(ClientHandler sender, int groupId) {
		try {
			String fileName = sender.dis.readUTF();
			long fileSize = sender.dis.readLong();

			File tempFile = new File(fileName);
			try (FileOutputStream fos = new FileOutputStream(tempFile)) {
				byte[] buffer = new byte[4096];
				int bytesRead;
				long remaining = fileSize;
				while (remaining > 0
						&& (bytesRead = sender.dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
					fos.write(buffer, 0, bytesRead);
					remaining -= bytesRead;
				}
			}

			byte[] allBytes = Files.readAllBytes(tempFile.toPath());
			java.util.List<Integer> memberIds = ConnectDatabase.getGroupMemberIds(groupId);

			for (int uid : memberIds) {
				ClientHandler ch = clientsById.get(uid);
				if (ch != null && ch.userId != sender.userId) {
					ch.dos.writeUTF("FILE");
					ch.dos.writeUTF(fileName);
					ch.dos.writeLong(allBytes.length);
					ch.dos.write(allBytes);
					ch.dos.flush();
				}
			}

			System.out.println("Đã gửi file '" + fileName + "' cho nhóm ID " + groupId);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// nguyên thêm gọi video 

	// Gửi friend list cho 1 client
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

	// Xóa người tham gia khỏi cuộc trò chuyện trong giao diện và server
	public void removeParticipant(JPanel participantPanel, String username) {
		// Gọi phương thức server để xóa client
		removeClient(username);

		// Loại bỏ panel khỏi giao diện người tham gia
		participantPanel.removeAll();
		participantPanel.revalidate();
		participantPanel.repaint();
	}

	// Xóa client khỏi danh sách khi họ rời cuộc trò chuyện
	public void removeClient(String username) {
		synchronized (clients) {
			Iterator<ClientHandler> iterator = clients.iterator();
			while (iterator.hasNext()) {
				ClientHandler client = iterator.next();
				if (client.username.equals(username)) {
					try {
						// Gửi thông báo cho client bị xóa
						client.dos.writeUTF("You have been removed from the chat");
						client.dos.flush();

						// Đóng kết nối socket
						client.socket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}

					// Xóa client khỏi danh sách clients
					iterator.remove();
					clientsById.remove(client.userId);
					break;
				}
			}
		}
	}

	// Class quản lý client
	static class ClientHandler {
	    int userId;
	    String username;
	    Socket socket;
	    DataInputStream dis;
	    DataOutputStream dos;

	    // 👇 thêm
	    Integer videoTargetUserId = null;

	    public ClientHandler(int userId, String username, Socket socket,
	                         DataInputStream dis, DataOutputStream dos) {
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
