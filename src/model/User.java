package model;

import java.time.LocalDateTime;

public class User {
	private int id;
	private String username;
	private String password;
	private String phoneNumber;
	private String gender;
	private LocalDateTime createAt;

	public User() {
	}

	public User(int id, String username, String password, String phoneNumber, String gender, LocalDateTime createAt) {
		this.id = id;
		this.username = username;
		this.password = password;
		this.phoneNumber = phoneNumber;
		this.gender = gender;
		this.createAt = createAt;
	}

	// Getter và Setter
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getGender() {
		return gender;
	}

	public void setGender(String gender) {
		this.gender = gender;
	}

	public LocalDateTime getCreateAt() {
		return createAt;
	}

	public void setCreateAt(LocalDateTime createAt) {
		this.createAt = createAt;
	}

	@Override
	public String toString() {
		return "User{" + "id=" + id + ", username='" + username + '\'' + ", phoneNumber='" + phoneNumber + '\''
				+ ", gender='" + gender + '\'' + ", createAt=" + createAt + '}';
	}
}
