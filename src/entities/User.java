package ProgettoFinale.entities;

//classe utente

public class User {
	private String username;
	private String password;
	
	//volatile garantisce che sia visibile correttamente tra tutti i thread del server
	private transient volatile boolean loggedIn;	//non verrà ssalvato nel file json
	
	public User(String username, String password) {
		this.username = username;
		this.password = password;
		this.loggedIn = false;
	}
	
	public String getUsername() {
		return username;
	}
	
	public String getPassword() {
		return password;
	}
	
	public boolean isLoggedIn() {
		return loggedIn;
	}
	
	public void setPassword(String p) {
		this.password = p;
	}
	
	public void setLoggedIn(boolean l) {
		this.loggedIn = l;
	}
}
