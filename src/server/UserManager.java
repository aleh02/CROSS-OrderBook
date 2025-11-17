package ProgettoFinale.server;

import ProgettoFinale.entities.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.concurrent.*;
import java.io.*;
import java.lang.reflect.Type;
import java.net.*;

/**
 * Gestisce registrazione, login e recupero dei dati utente
 * Usa ConcurrentHashMap per garantire sicurezza
 * nell'accesso da parte di più thread(ClientHandler)
 */

public class UserManager {
	private final ConcurrentHashMap<String, User> users;	//chiave username
	private final ConcurrentHashMap<String, UserNotificationInfo> notificationMap;
	
	private String usersFilename;
    private Gson gson;
	
	public UserManager(String usersFilename) {
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		this.notificationMap = new ConcurrentHashMap<>();
		this.usersFilename = usersFilename;
		this.users = loadUsers();	//carica gli utenti dal file all'avvio
	}
	
	public synchronized int registerUser(String username, String password) {
		if(password == null || password.isEmpty())
			return 101;	//Invalid Password
		//se username non presente inserisce nuovo utente e ritorna null, altrimenti ritorna l'utente esistente
		User existingUser = users.putIfAbsent(username, new User(username, password));	//operazione threadsafe
		
		if(existingUser != null)
			return 102;	//Username not available
		
		saveUsers();
		System.out.println("UserManager: Utente " + username + " registrato e salvato.");
		
        return 100; // OK
	}
	
	//Tenta di autenticare un utente e registra informazioni per notifiche UDP.
	public int loginUser(String username, String password, InetAddress clientIp, int clientUdpPort) {
		User user = users.get(username);
		
		//utente inesistente o password errata
		if(user == null || !user.getPassword().equals(password))
			return 101;	//Codice errore 101(mismatch)
		
		//utente già loggato
		synchronized(user) {	//per impostare isLoggedIn di user in modo atomico
			if(user.isLoggedIn())
				return 102;	//Codice errore 102(already logged)
			user.setLoggedIn(true);
		}
		
		// Se il login ha successo, salviamo dove notificarlo
		UserNotificationInfo info = new UserNotificationInfo(clientIp, clientUdpPort);
		notificationMap.put(username, info);
		
		return 100;	//OK
	}
	
	public void logoutUser(String username) {
		User user = users.get(username);
		if(user != null) {
			synchronized(user) {
				user.setLoggedIn(false);
			}
		}
        notificationMap.remove(username); //Rimuove utente dalla mappa delle notifiche
	}
	
	//recupera informazioni di notifica per un dato utente
	public UserNotificationInfo getNotificationInfo(String username) {
        return notificationMap.get(username);
    }
	
	//tenta di aggiornare la password di un utente
	public synchronized int updateCredentials(String username, String oldPassword, String newPassword) {
		User user = users.get(username);
		
        if (user == null || !user.getPassword().equals(oldPassword)) 
            return 102;	//utente inesistente o password vecchia errata

        if (newPassword == null || newPassword.isEmpty()) 
            return 101;	//nuova password non valida/vuota
         
        if (newPassword.equals(oldPassword)) 
            return 103;	//nuova password uguale alla vecchia
        
        synchronized(user) {
        	if(user.isLoggedIn())	//controllo stato di login in modo threadsafe
        		return 104;	//utente attualmente loggato
        	
        	user.setPassword(newPassword);	//OK
        }
        
        saveUsers();	//salva modifica su file
        System.out.println("UserManager: Password aggiornata per " + username);
        
        return 100;
	}
	
	//carica utenti da file
	private ConcurrentHashMap<String, User> loadUsers() {
		try(FileReader reader = new FileReader(usersFilename)) {
			Type mapType = new TypeToken<ConcurrentHashMap<String, User>>(){}.getType();
			ConcurrentHashMap<String, User> loadedUsers = gson.fromJson(reader, mapType);
			
			if(loadedUsers != null) {
				System.out.println("UserManager: Caricati " + loadedUsers.size() + " utenti.");
                //resetta stato di login di tutti gli utenti (nessuno è loggato al riavvio del server)
				for(User user : loadedUsers.values()) {
					user.setLoggedIn(false);
				}
				return loadedUsers;
			}
		} catch(IOException e) {
			System.err.println("UserManager: File " + usersFilename + " non trovato. Creerò un nuovo file.");
        } catch (Exception e) {
            System.err.println("UserManager: Errore nel caricamento " + usersFilename + ". " + e.getMessage());
        }
		
		return new ConcurrentHashMap<>(); //se file inesistente/vuoto ritorna mappa vuota
	}
	
	//salva utenti su file
	private synchronized void saveUsers() {
		try(FileWriter writer = new FileWriter(usersFilename)){
			//converte mappa 'users' in JSON
            gson.toJson(users, writer);
		} catch(IOException e) {
			System.err.println("UserManager: Errore nel salvataggio utenti!");
            e.printStackTrace();
		}
	}
}
