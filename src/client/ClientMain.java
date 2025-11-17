package ProgettoFinale.client;

import java.io.*;
import java.net.*;
import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

import ProgettoFinale.entities.*;

public class ClientMain {
	private static final String CLIENT_CONFIG_FILE = "client.properties";
	
	private static PrintWriter writer;
    private static BufferedReader reader;
    private static Scanner scanner;
    private static Gson gson;

    private static boolean isLoggedIn = false;
    private static String username = null;
    
    //valori di config
    private static String serverAddress;
    private static int serverTcpPort;
    private static int dynamicUdpPort;
    private static int udpBufferSize;
	
	public static void main(String[] args) {
		gson = new Gson();
		scanner = new Scanner(System.in);
		
		//caricamento file config
		Properties config = new Properties();
        try (FileInputStream fis = new FileInputStream(CLIENT_CONFIG_FILE)) {
            config.load(fis);
        } catch (Exception e) {
            System.err.println("Errore: Impossibile trovare o caricare " + CLIENT_CONFIG_FILE);
            e.printStackTrace();
            return;
        }

        serverAddress = config.getProperty("server_address");
        String serverTcpPortStr = config.getProperty("server_tcp_port");
        String udpBufferSizeStr = config.getProperty("udp_buffer_size");
        
        //check correttezza dei valori config
        if (serverAddress == null || serverTcpPortStr == null || udpBufferSizeStr == null) {
            System.err.println("Errore: Il file 'client.properties' è incompleto.");
            if (serverAddress == null) System.err.println("Manca la chiave: 'server_address'");
            if (serverTcpPortStr == null) System.err.println("Manca la chiave: 'server_tcp_port'");
            if (udpBufferSizeStr == null) System.err.println("Manca la chiave: 'udp_buffer_size'");
            System.err.println("Avvio interrotto.");
            return;
        }
        
        try {	//conversione a int
            serverTcpPort = Integer.parseInt(serverTcpPortStr);
            udpBufferSize = Integer.parseInt(udpBufferSizeStr);
        } catch (NumberFormatException e) {
            System.err.println("Errore: Le porte o il buffer size in 'client.properties' non sono numeri validi.");
            System.err.println("Avvio interrotto.");
            return;
        }
        
		try {	//avvio listener UDP in thread separato prima della connessione al server TCP
			NotificationListener listener = new NotificationListener(udpBufferSize);
			dynamicUdpPort = listener.getPort();
			Thread listenerThread = new Thread(listener);
			listenerThread.setDaemon(true);	//thread in baackground, si chiude quando main termina
			listenerThread.start();
		} catch (Exception e) {
            System.err.println("Errore avvio listener UDP: " + e.getMessage());
            return; // Non continuiamo se il listener fallisce
        }
		
		try(Socket socket = new Socket(serverAddress, serverTcpPort);)
		{
			writer = new PrintWriter(socket.getOutputStream(), true);
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			System.out.println("CROSS Client Connesso al Server " + serverAddress + ":" + serverTcpPort + 
                    " (Notifiche UDP su porta " + dynamicUdpPort + ")");
			
            while(true) {
            	if(!isLoggedIn) {
            		showLoggedOutMenu();
            	} else {
            		showLoggedInMenu();
            	}
            }
		} catch(Exception e) {
			System.err.println("Errore nel client: " + e.getMessage());
			e.printStackTrace();
		} finally {
			scanner.close();
		}
	}
	
	private static void showLoggedOutMenu() {
        System.out.println("\n--- MENU (Non Autenticato) ---");
        System.out.println("1. Registrati");
        System.out.println("2. Login");
        System.out.println("3. Aggiorna Password");
        System.out.println("9. Esci");
        System.out.print("Scegli un'opzione(n): ");

        String choice = scanner.nextLine();
        
        switch (choice) {
            case "1":
                handleRegistration();
                break;
            case "2":
                handleLogin();
                break;
            case "3":
                handleUpdateCredentials();
                break;
            case "9":
                System.out.println("Uscita...");
                System.exit(0);
                break;
            default:
                System.out.println("Scelta non valida. Riprova.");
                break;
        }
    }
	
	private static void showLoggedInMenu() {
		System.out.println("\n--- MENU (Autenticato come " + username + ") ---");
        System.out.println("1. Inserisci Limit Order");
        System.out.println("2. Inserisci Market Order");
        System.out.println("3. Inserisci Stop Order");
        System.out.println("4. Cancella Ordine");
        System.out.println("5. Vedi Storico Prezzi (OHLC)");
        System.out.println("6. Mostra Order Book (Debug)");
        System.out.println("9. Logout");
        System.out.print("Scegli un'opzione: ");

        String choice = scanner.nextLine();
        
        switch (choice) {
            case "1":
                handleInsertLimitOrder();
                break;
            case "2":
                handleInsertMarketOrder();
                break;
            case "3":
                handleInsertStopOrder();
                break;
            case "4":
                handleCancelOrder();
                break;
            case "5":
                handleGetPriceHistory();
                break;
            case "6":
                handleShowOrderBook();
                break;
            case "9":
                handleLogout();
                break;
            default:
                System.out.println("Scelta non valida. Riprova.");
        }
    }
	
	//gestisce invio di limit order
	private static void handleInsertLimitOrder() {
		System.out.println("\n--- Inserisci Limit Order ---");
		try {	//chiede il TIPO (ASK/BID)
			String typeStr;
			while(true) {
				System.out.print("Tipo (ask/bid): ");
                typeStr = scanner.nextLine().toUpperCase();
                if (typeStr.equals("ASK") || typeStr.equals("BID")) 
                    break;
                System.out.println("Errore: inserisci 'ask' o 'bid'.");
			}
			
			//chiede la DIMENSIONE (size)
			System.out.print("Dimensione (in millesimi di BTC): ");
            int size = Integer.parseInt(scanner.nextLine());
            
            //chiede il PREZZO (limitPrice)
            System.out.print("Prezzo Limite (in millesimi di USD): ");
            int price = Integer.parseInt(scanner.nextLine());
            
            if(size <= 0 || price <= 0) {
            	System.out.println("Errore: Dimensione e Prezzo devono essere positivi.");
                return;
            }
            
            //costruisce JSON
            JsonObject request = new JsonObject();
            request.addProperty("operation", "insertLimitOrder");
            
			JsonObject values = new JsonObject();
			values.addProperty("type", typeStr);
			values.addProperty("size", size);
            values.addProperty("price", price);
            request.add("values", values);
            
            //invia e riceve risposta
            String jsonResponse = sendAndReceive(gson.toJson(request));
            
            //stampa risultato
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
            long orderId = response.get("orderId").getAsLong();
            
            if(orderId == -1) {
            	System.out.println("Errore: L'ordine non è stato accettato dal server.");
            } else {
                System.out.println("Ordine Limit inserito con successo! ID Ordine: " + orderId);
            }
			
		} catch (NumberFormatException e) {
            System.out.println("Errore: Inserisci un numero valido per size e price.");
        } catch (Exception e) {
            System.err.println("Errore durante l'inserimento dell'ordine: " + e.getMessage());
        }
	}
	
	//gestisce invio di market order
	private static void handleInsertMarketOrder() {
		System.out.println("\n--- Inserisci Market Order ---");
		try {	//chiede il TIPO (ASK/BID)
			String typeStr;
			while(true) {
				System.out.print("Tipo (ask/bid): ");
                typeStr = scanner.nextLine().toUpperCase();
                if (typeStr.equals("ASK") || typeStr.equals("BID")) 
                    break;
                System.out.println("Errore: inserisci 'ask' o 'bid'.");
			}
			
			//chiede la DIMENSIONE (size)
			System.out.print("Dimensione (in millesimi di BTC): ");
            int size = Integer.parseInt(scanner.nextLine());
            
            if(size <= 0) {
            	System.out.println("Errore: Dimensione deve essere positiva.");
                return;
            }
            
            //costruisce JSON
            JsonObject request = new JsonObject();
            request.addProperty("operation", "insertMarketOrder");
            
			JsonObject values = new JsonObject();
			values.addProperty("type", typeStr);
			values.addProperty("size", size);
            request.add("values", values);
            
            //invia e riceve risposta
            String jsonResponse = sendAndReceive(gson.toJson(request));
            
            //stampa risultato
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
            long orderId = response.get("orderId").getAsLong();
            
            if(orderId == -1) {
            	System.out.println("Errore: L'ordine è fallito (es. liquidità insufficiente).");
            } else {
                System.out.println("Ordine Market inserito con successo! ID Ordine: " + orderId);
            }
			
		} catch (NumberFormatException e) {
            System.out.println("Errore: Inserisci un numero valido per la size.");
        } catch (Exception e) {
            System.err.println("Errore durante l'inserimento dell'ordine: " + e.getMessage());
        }
	}
	
	//gestisce invio di stop order
	private static void handleInsertStopOrder() {
		System.out.println("\n--- Inserisci Stop Order ---");
		try {	//chiede il TIPO (ASK/BID)
			String typeStr;
			while(true) {
				System.out.print("Tipo (ask/bid): ");
                typeStr = scanner.nextLine().toUpperCase();
                if (typeStr.equals("ASK") || typeStr.equals("BID")) 
                    break;
                System.out.println("Errore: inserisci 'ask' o 'bid'.");
			}
			
			//chiede la DIMENSIONE (size)
			System.out.print("Dimensione (in millesimi di BTC): ");
            int size = Integer.parseInt(scanner.nextLine());
            
            //chiede il PREZZO (stopPrice)
            System.out.print("Prezzo di Stop (in millesimi di USD): ");
            int price = Integer.parseInt(scanner.nextLine());
            
            if(size <= 0 || price <= 0) {
            	System.out.println("Errore: Dimensione e Prezzo devono essere positivi.");
                return;
            }
            
            //costruisce JSON
            JsonObject request = new JsonObject();
            request.addProperty("operation", "insertStopOrder");
            
			JsonObject values = new JsonObject();
			values.addProperty("type", typeStr);
			values.addProperty("size", size);
            values.addProperty("price", price);
            request.add("values", values);
            
            //invia e riceve risposta
            String jsonResponse = sendAndReceive(gson.toJson(request));
            
            //stampa risultato
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
            long orderId = response.get("orderId").getAsLong();
            
            if(orderId == -1) {
            	System.out.println("Errore: L'ordine non è stato accettato dal server.");
            } else {
                System.out.println("Ordine Stop inserito con successo! ID Ordine: " + orderId);
            }
			
		} catch (NumberFormatException e) {
            System.out.println("Errore: Inserisci un numero valido per size e price.");
        } catch (Exception e) {
            System.err.println("Errore durante l'inserimento dell'ordine: " + e.getMessage());
        }
	}
	
	private static List<ActiveOrderInfo> fetchAndDisplayActiveOrders() {
        System.out.println("Recupero i tuoi ordini attivi...");
        
        JsonObject request = new JsonObject();
        request.addProperty("operation", "getMyActiveOrders");
        
        try {
            String jsonResponse = sendAndReceive(gson.toJson(request));
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
            int responseCode = response.get("response").getAsInt();
            
            if (responseCode != 100) {
                System.out.println("Errore nel recupero ordini: " + response.get("errorMessage").getAsString());
                return null;
            }
            
            JsonArray orderArray = response.getAsJsonArray("activeOrders");
            
            if (orderArray == null || orderArray.size() == 0) {
                System.out.println("Non hai ordini attivi.");
                return new ArrayList<ActiveOrderInfo>(); // Ritorna lista vuota
            }

            //dice a Gson di creare una List<ActiveOrderInfo>
            Type listType = new TypeToken<ArrayList<ActiveOrderInfo>>() {}.getType();
            List<ActiveOrderInfo> activeOrders = gson.fromJson(orderArray, listType);
            
            System.out.println("I tuoi ordini attivi:");
            for (ActiveOrderInfo order : activeOrders) {
                System.out.printf("  - ID: %-8d | %-4s | %-6s | Size: %-5d | Prezzo: %d\n",
                    order.getOrderId(),
                    order.getType().toUpperCase(),
                    order.getOrderType(),
                    order.getSize(),
                    order.getPrice());
            }
            return activeOrders;

        } catch (Exception e) {
            System.err.println("Errore nel recupero ordini: " + e.getMessage());
            return null;
        }
    }
	
	//gestisce cancellazione ordine
	private static void handleCancelOrder() {
		System.out.println("\n--- Cancella Ordine ---");
		
		List<ActiveOrderInfo> activeOrders = fetchAndDisplayActiveOrders();
		
		if (activeOrders == null || activeOrders.isEmpty())
			return;
		
		try {
			System.out.print("Inserisci l'ID dell'ordine da cancellare: ");
			long orderId = Long.parseLong(scanner.nextLine());
			
			//costruisce JSON
			JsonObject request = new JsonObject();
			request.addProperty("operation", "cancelOrder");
			
			JsonObject values = new JsonObject();
			values.addProperty("orderId", orderId);
			request.add("values", values);
			
			//invia e riceve
			String jsonResponse = sendAndReceive(gson.toJson(request));
			JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
			int responseCode = response.get("response").getAsInt();
			
			if (responseCode == 100) {
                System.out.println("Ordine " + orderId + " cancellato con successo.");
            } else {
                System.out.println("Errore: " + response.get("errorMessage").getAsString());
            }
			
		} catch (NumberFormatException e) {
            System.out.println("Errore: Inserisci un ID numerico valido.");
        } catch (Exception e) {
            System.err.println("Errore durante la cancellazione: " + e.getMessage());
        }
	}

	//gestisce richiesta dello storico prezzi OHLC
	private static void handleGetPriceHistory() {
		System.out.println("\n--- Storico Prezzi (OHLC) ---");
        try {
            System.out.print("Inserisci mese (MMYYYY, es. 012025): ");
            String month = scanner.nextLine();
            
            if(month.length() != 6 || !month.matches("\\d{6}")) {	// \\d{6} = sequenza di 6 cifre consecutive
            	System.out.println("Errore: Formato non valido. Richiesto MMYYYY.");
                return;
            }
            
            JsonObject request = new JsonObject();
            request.addProperty("operation", "getPriceHistory");
            
            JsonObject values = new JsonObject();
            values.addProperty("month", month);
            request.add("values", values);
            
            String jsonResponse = sendAndReceive(gson.toJson(request));
            
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
            int responseCode = response.get("response").getAsInt();
            
            if(responseCode == 100) {
            	System.out.println("Dati storici per " + month + ":");
            	
            	JsonObject ohlcData = response.getAsJsonObject("ohlcData");
            	
            	if(ohlcData.keySet().isEmpty()) {
            		System.out.println("  Nessun dato trovato per questo mese.");
                    return;
            	}
            	
            	//stampa OHLC per ogni giorno del mese
            	for(String day : ohlcData.keySet()) {
            		JsonObject data = ohlcData.getAsJsonObject(day);
            		System.out.printf("  %s -> Open: %d, High: %d, Low: %d, Close: %d\n",
                            data.get("date").getAsString(),
                            data.get("open").getAsInt(),
                            data.get("high").getAsInt(),
                            data.get("low").getAsInt(),
                            data.get("close").getAsInt()
                        );
            	}
            } else {
            	System.out.println("Errore: " + response.get("errorMessage").getAsString());
            }
        } catch(Exception e) {
        	System.err.println("Errore richiesta storico: " + e.getMessage());
            e.printStackTrace();
        }
	}
	
	private static void handleRegistration() {
		System.out.println("\n--- Registrazione Nuovo Utente ---");
        System.out.print("Inserisci username: ");
        String regUsername = scanner.nextLine();
        System.out.print("Inserisci password: ");
        String regPassword = scanner.nextLine();
        
        if (regUsername.isEmpty() || regPassword.isEmpty()) {
            System.out.println("Username e password non possono essere vuoti.");
            return;
        }
        
        //Costruzione oggetto JSON
        JsonObject request = new JsonObject();
        request.addProperty("operation", "register");
        
        JsonObject values = new JsonObject();
        values.addProperty("username", regUsername);
        values.addProperty("password", regPassword);
        request.add("values", values);
        
        try {
        	//invia e riceve
        	String jsonResponse = sendAndReceive(gson.toJson(request));
        	
        	//stampa risposta
        	JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
        	int responseCode = response.get("response").getAsInt();
        	
        	if (responseCode == 100) {
        		System.out.println("Registrazione avvenuta con successo!");
            } else {
                System.out.println("Errore: " + response.get("errorMessage").getAsString());
            }        
        } catch(Exception e) {
        	System.err.println("Errore durante la registrazione: " + e.getMessage());
        }
	}
	
	private static void handleLogin() {
        System.out.println("\n--- Login Utente ---");
        System.out.print("Username: ");
        String loginUsername = scanner.nextLine();
        System.out.print("Password: ");
        String loginPassword = scanner.nextLine();

        if(loginUsername.isEmpty() || loginPassword.isEmpty()) {
        	System.out.println("Username e password non possono essere vuoti.");
            return;
        }
        
        //costruzione JSON per il login
        JsonObject request = new JsonObject();
        request.addProperty("operation", "login");
        
        JsonObject values = new JsonObject();
        values.addProperty("username", loginUsername);
        values.addProperty("password", loginPassword);
        
        values.addProperty("udpPort", dynamicUdpPort);
        
        request.add("values", values);
        
        try {	//Invia e riceve
        	String jsonResponse = sendAndReceive(gson.toJson(request));
        	
        	JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
        	int responseCode = response.get("response").getAsInt();
        	
        	if(responseCode == 100) {	//OK
        		System.out.println("Login effettuato con successo! Benvenuto, " + loginUsername + ".");
        		isLoggedIn = true;
        		username = loginUsername;
        	} else {
        		// Stampa errore inviato dal server
                System.out.println("Errore: " + response.get("errorMessage").getAsString());
        	}
        } catch(Exception e) {
        	System.err.println("Errore durante il login: " + e.getMessage());
        }
        
        //System.out.println("Funzione Login non ancora implementata.");
    }
	
	private static void handleLogout() {
		if(!isLoggedIn) {
			System.out.println("Non sei attualmente loggato.");
            return;
		}
		
        System.out.println("Esecuzione del logout...");
        
        JsonObject request = new JsonObject();
        request.addProperty("operation", "logout");
        
        try {
        	String jsonResponse = sendAndReceive(gson.toJson(request));
        	
        	JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
        	int responseCode = response.get("response").getAsInt();
        	
        	if(responseCode == 100) {	//OK
        		System.out.println("Logout effettuato con successo.");
            } else {
                System.out.println("Errore: " + response.get("errorMessage").getAsString());
            }
        } catch (Exception e) {
        	System.err.println("Errore durante il logout: " + e.getMessage());
        } finally { //client reset, indipendentemente dalla risposta del server
        	isLoggedIn = false;
        	username = null;
        }
    }
	
	private static void handleUpdateCredentials() {
		System.out.println("\n--- Aggiorna Password ---");
        try {
            System.out.print("Username: ");
            String username = scanner.nextLine();
            System.out.print("Vecchia Password: ");
            String oldPass = scanner.nextLine();
            System.out.print("Nuova Password: ");
            String newPass = scanner.nextLine();

            if (username.isEmpty() || oldPass.isEmpty() || newPass.isEmpty()) {
                System.out.println("Tutti i campi sono obbligatori.");
                return;
            }
            
            //costruisce JSON
            JsonObject request = new JsonObject();
            request.addProperty("operation", "updateCredentials");
            
            JsonObject values = new JsonObject();
            values.addProperty("username", username);
            values.addProperty("old_password", oldPass);
            values.addProperty("new_password", newPass);
            request.add("values", values);

            String jsonResponse = sendAndReceive(gson.toJson(request));
            
            //stampa risultato
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
            int responseCode = response.get("response").getAsInt();
            
            if (responseCode == 100) {
                System.out.println("Password aggiornata con successo!");
            } else {
                System.out.println("Errore: " + response.get("errorMessage").getAsString());
            }

        } catch (Exception e) {
            System.err.println("Errore durante l'aggiornamento password: " + e.getMessage());
            e.printStackTrace();
        }
	}
	
	//chiede al server lo snapshot dell'order book e lo stampa
	private static void handleShowOrderBook() {
		System.out.println("\nRecupero snapshot dell'Order Book...");
        
        JsonObject request = new JsonObject();
        request.addProperty("operation", "getOrderBookSnapshot");
        
        try {
            String jsonResponse = sendAndReceive(gson.toJson(request));
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
            int responseCode = response.get("response").getAsInt();
            
            if (responseCode != 100) {
                System.out.println("Errore nel recupero snapshot: " + response.get("errorMessage").getAsString());
                return;
            }
            
            //estrai la stringa dello snapshot
            String snapshot = response.get("snapshot").getAsString();
            
            System.out.println("\n--- SNAPSHOT ORDER BOOK (DAL SERVER) ---");
            System.out.println(snapshot);
            System.out.println("------------------------------------------");

        } catch (Exception e) {
            System.err.println("Errore nel recupero dello snapshot: " + e.getMessage());
            e.printStackTrace();
        }
	}
	
	//invia stringa JSON e riceve risposta
	private static String sendAndReceive(String jsonRequest) throws Exception {
		System.out.println("C -> S: " + jsonRequest);
		writer.println(jsonRequest);
		
		String jsonResponse = reader.readLine();
		System.out.println("S -> C: " + jsonResponse);
		
		if (jsonResponse == null)
            throw new Exception("Il server ha chiuso la connessione.");
		
        return jsonResponse;
	}
}
