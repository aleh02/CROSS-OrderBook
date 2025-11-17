package ProgettoFinale.server;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import ProgettoFinale.entities.*;

/**
 * Runnable che gestisce la comunicazione con un singolo client
 * per l'intera durata della sua connessione.
 *
 * Ogni ClientHandler viene eseguito in un thread separato
 * dal Thread Pool del ServerMain.
 */

public class ClientHandler implements Runnable {
	private final Socket clientSocket;
	private final UserManager userManager;
	private final OrderBook orderBook;
	private final DatagramSocket udpSocket;
	
	private final HistoryManager historyManager;
	private final String historyFilename;
	
	private String loggedInUsername = null;
	private final Gson gson = new Gson();
	
	//oggetto condiviso da tutti i thread per sincronizzare accesso al file storico.
	private static final Object historyFileLock = new Object();
	
	public ClientHandler(Socket socket, UserManager userManager, OrderBook orderBook, 
			DatagramSocket udpSocket, HistoryManager historyManager, String historyFilename) {
		this.clientSocket = socket;
        this.userManager = userManager;
        this.orderBook = orderBook;
        this.udpSocket = udpSocket;
        this.historyManager = historyManager;
        this.historyFilename = historyFilename;
	}
	
	public void run() {
		try(InputStreamReader isr = new InputStreamReader(clientSocket.getInputStream());
			BufferedReader reader = new BufferedReader(isr);
			PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
			){	//chiusura reader e writer gestita da try-with-resources
			String requestJson;
			//legge un comando (riga JSON) alla volta finché il client è connesso
			while((requestJson = reader.readLine()) != null) {
				System.out.println("Ricevuto da " + clientSocket.getInetAddress() + ": " + requestJson);
				
				//map per la risposta
				Map<String, Object> response = new HashMap<>();
				
				try {
					//deserializzazione (da string a object)
					JsonObject request = JsonParser.parseString(requestJson).getAsJsonObject();
					String operation = request.get("operation").getAsString();
					
					switch(operation) {
					case "register": {
						JsonObject values = request.getAsJsonObject("values");
						String username = values.get("username").getAsString();
						String password = values.get("password").getAsString();
						
						int responseCode = userManager.registerUser(username, password);
						
						response.put("response", responseCode);
						response.put("errorMessage", getErrorMessage(responseCode, "register"));
						break;
					}
					case "login":
						//utente può fare login solo se non è già loggato
                        if (this.loggedInUsername != null) {
                        	response.put("response", 102); //already logged in
                        	response.put("errorMessage", getErrorMessage(102, "login"));
                            break;
                        }
                        
                        //Estrae valori
                        JsonObject loginValues = request.getAsJsonObject("values");
                        String loginUsername = loginValues.get("username").getAsString();
                        String loginPassword = loginValues.get("password").getAsString();
                        int udpPort = loginValues.get("udpPort").getAsInt();
                        
                        //Ottiene IP dal client del socket TCP
                        InetAddress clientIp = clientSocket.getInetAddress();
                        
                        //Se login ha avuto successo, associa username a questo handler
                        int loginCode = userManager.loginUser(loginUsername, loginPassword, clientIp, udpPort);
                        if(loginCode == 100) {	//OK
                        	this.loggedInUsername = loginUsername;
                        	System.out.println("Utente " + loginUsername + " loggato su questa connessione.");
                        }
                        //Prepara risposta
                        response.put("response", loginCode);
                        response.put("errorMessage", getErrorMessage(loginCode, "login"));
                        break;
					case "logout":
						//utente può fare logout solo se è già loggato
                        if (this.loggedInUsername == null) {
                        	response.put("response", 101); //errore
                        	response.put("errorMessage", getErrorMessage(101, "logout"));
                            break;
                        } else {
                        	String logoutUser = this.loggedInUsername;
                        	this.loggedInUsername = null;
                        	
                        	userManager.logoutUser(logoutUser);
                        	
                        	response.put("response", 100);
                        	response.put("errorMessage", getErrorMessage(100, "logout"));
                        	System.out.println("Utente " + logoutUser + " ha effettuato il logout.");
                        }
                        break;
					case "insertLimitOrder": {
						if(this.loggedInUsername == null) {
							response.put("orderId", -1);	//errore
                            break;
						}
						
						//parsa i dati
						JsonObject values = request.getAsJsonObject("values");
						OrderType type = gson.fromJson(values.get("type"), OrderType.class);
						int size = values.get("size").getAsInt();
						int price = values.get("price").getAsInt();
						
						//crea oggetto order
						LimitOrder order = new LimitOrder(this.loggedInUsername, type, size, price);
						
						List<Trade> trades = orderBook.addLimitOrder(order); //processa
						
						response.put("orderId", order.getOrderId());	//risposta
						
						//notifica e persiste
						sendTradeNotifications(trades);
						persistTrades(trades);
						
                        break;
					}
					case "insertMarketOrder": {
						if(this.loggedInUsername == null) {
							response.put("orderId", -1);
						}
						
						JsonObject values = request.getAsJsonObject("values");
						OrderType type = gson.fromJson(values.get("type"), OrderType.class);
						int size = values.get("size").getAsInt();
						
						MarketOrder order = new MarketOrder(this.loggedInUsername, type, size);
						
						try {
							List<Trade> trades = orderBook.executeMarketOrder(order);
							response.put("orderId", order.getOrderId());
							
							sendTradeNotifications(trades);
							persistTrades(trades);
						} catch(Exception e) {
							// Ordine fallito (tutto o niente)
							response.put("orderId", -1);
						}
						break;
					}
					case "insertStopOrder": {
						if (this.loggedInUsername == null) {
                            response.put("orderId", -1);
                            break;
                        }
						
						JsonObject values = request.getAsJsonObject("values");
						OrderType type = gson.fromJson(values.get("type"), OrderType.class);
						int size = values.get("size").getAsInt();
						int stopPrice = values.get("price").getAsInt();
						
						StopOrder order = new StopOrder(this.loggedInUsername, type, size, stopPrice);
						orderBook.addStopOrder(order);
						
						response.put("orderId", order.getOrderId());
						//nessun trade, quindi nessuna notifica o persistenza
						
						break;
					}
					case "cancelOrder": {
						if(this.loggedInUsername == null) {
							response.put("response", 101);	//Errore
							response.put("errorMessage", getErrorMessage(101, "cancelOrder"));
							break;
						}
						
						JsonObject values = request.getAsJsonObject("values");
						long orderId = values.get("orderId").getAsLong();
						
						boolean success = orderBook.cancelOrder(orderId, this.loggedInUsername);
						
						if(success) {
							response.put("response", 100);
						} else {
                            response.put("response", 101);
                        }
                        response.put("errorMessage", getErrorMessage((int)response.get("response"), "cancelOrder"));
                        break;
					}
					case "getPriceHistory": {
						if(this.loggedInUsername == null) {
							response.put("response", 101); //errore
                            response.put("errorMessage", getErrorMessage(101, "getPriceHistory"));
                            break;
						}
						
						JsonObject values = request.getAsJsonObject("values");
						String month = values.get("month").getAsString();	//"MMYYYY"
						
						//chiamata al manager
						Map<String, HistoryManager.OhlcData> data = historyManager.getHistory(month);
						
						if (data == null) {
                            response.put("response", 103); //errore
                            response.put("errorMessage", getErrorMessage(103, "getPriceHistory"));
                        } else {
                            response.put("response", 100);
                            response.put("month", month);
                            response.put("ohlcData", gson.toJsonTree(data)); //aggiunge i dati come albero JSON
                        }
                        break;
					}
					case "updateCredentials": {
						//si può fare solo da sloggati
                        if (this.loggedInUsername != null) {
                            response.put("response", 104); //user logged in
                            response.put("errorMessage", getErrorMessage(104, "updateCredentials"));
                            break;
                        }

                        JsonObject values = request.getAsJsonObject("values");
                        String username = values.get("username").getAsString();
                        String oldPass = values.get("old_password").getAsString();
                        String newPass = values.get("new_password").getAsString();
                        
                        int responseCode = userManager.updateCredentials(username, oldPass, newPass);
                        
                        response.put("response", responseCode);
                        response.put("errorMessage", getErrorMessage(responseCode, "updateCredentials"));
                        break;
                    }
					case "getMyActiveOrders": {
                        if (this.loggedInUsername == null) {
                            response.put("response", 101);
                            response.put("errorMessage", getErrorMessage(101, "getMyActiveOrders"));
                            break;
                        }
                       
                        List<ActiveOrderInfo> activeOrders = orderBook.getActiveOrders(this.loggedInUsername);
                        
                        response.put("response", 100);
                        response.put("activeOrders", gson.toJsonTree(activeOrders));
                        break;
                    }
					case "getOrderBookSnapshot": {
                        if (this.loggedInUsername == null) {
                            response.put("response", 101);
                            response.put("errorMessage", getErrorMessage(101, "getOrderBookSnapshot"));
                            break;
                        }
                        
                        String snapshotData = orderBook.getOrderBookSnapshot();
                        
                        response.put("response", 100);
                        response.put("snapshot", snapshotData);
                        break;
                    }
                     default:
                    	 response.put("response", 103); //altri errori
                         response.put("errorMessage", getErrorMessage(103, "default"));
					}
				} catch(Exception e) {
					System.err.println("Errore parsing JSON o esecuzione: " + e.getMessage());
                    response.put("response", 103);
                    response.put("errorMessage", "Error processing request: " + e.getMessage());
                    e.printStackTrace();
				}
				
				//Serializzazione e invio risposta
				String jsonResponse = gson.toJson(response);
				writer.println(jsonResponse);
			}
		} catch(IOException e) {
			System.err.println("Connessione persa con " + clientSocket.getInetAddress() + ": " + e.getMessage());
		} finally {	//logout in caso di disconnessione
			if(this.loggedInUsername != null) {
				userManager.logoutUser(this.loggedInUsername);
				System.out.println("Logout automatico per: " + this.loggedInUsername);
			}
			try {
				clientSocket.close();	//chiude client socket
			} catch(IOException e) {
				//ignora
			}
		}		
	}
	
	//invia notifiche di trade via UDP agli utenti coinvolti
	private void sendTradeNotifications(List<Trade> trades) {
		if(trades == null || trades.isEmpty())
			return;
		
        //Mappa degli utenti da notificare(evita doppioni)
        Map<String, UserNotificationInfo> usersToNotify = new HashMap<>(); 
        
        for(Trade trade : trades) {	//raccoglie gli utenti da notificare
        	//cerca info di notifica per il buyer
        	if(!usersToNotify.containsKey(trade.getBuyerUsername()))
        		usersToNotify.put(trade.getBuyerUsername(), userManager.getNotificationInfo(trade.getBuyerUsername()));
        	
        	//cerca info di notifica per il seller
        	if(!usersToNotify.containsKey(trade.getSellerUsername()))
        		usersToNotify.put(trade.getSellerUsername(), userManager.getNotificationInfo(trade.getSellerUsername()));
        }
        
        try {	//itera su utenti da notificare
    		for(Map.Entry<String, UserNotificationInfo> entry : usersToNotify.entrySet()) {
    			String username = entry.getKey();
    			UserNotificationInfo info = entry.getValue();
    			
    			if(info == null) {
    				System.err.println("ClientHandler: Impossibile trovare info di notifica per " 
    							+ username + " (probabilmente offline).");
                    continue; // Utente non loggato o info non trovate
    			}
    			
    			//costruisce messaggio contenente trade che riguardano l'utente
    	        List<TradeNotificationDetail> userTradeDetails = new ArrayList<>();
    	        for(Trade tradeItem : trades) {
    	        	if(tradeItem.getBuyerUsername().equals(username)) {
    	        		//utente BUYER
    	        		userTradeDetails.add(new TradeNotificationDetail(
    	        				tradeItem.getBuyOrderId(), "bid",
    	        				tradeItem.getBuyerOrderType(), 
    	        				tradeItem.getSize(), tradeItem.getPrice(),
    	        				tradeItem.getTimestamp()));
    	        	} else if (tradeItem.getSellerUsername().equals(username)) {
                        //utente SELLER
                        userTradeDetails.add(new TradeNotificationDetail(
                        		tradeItem.getSellOrderId(), "ask", 
                        		tradeItem.getSellerOrderType(),
                        		tradeItem.getSize(), tradeItem.getPrice(), 
                        		tradeItem.getTimestamp()));
                    }
    	        }
    	        
    	        if(userTradeDetails.isEmpty())
    	        	continue;	//se nessun trade per questo utente
    	        
    	        //costruisce JSON
    	        TradeNotification notificationPayload = new TradeNotification(userTradeDetails);
    	        String jsonMessage = gson.toJson(notificationPayload);
    	        byte[] sendData = jsonMessage.getBytes();
    	        
    	        DatagramPacket sendPacket = new DatagramPacket(
    	        		sendData, sendData.length,
    	        		info.getIpAddress(), info.getUdpPort());
    	        
    	        udpSocket.send(sendPacket);
    	        System.out.println("Inviata notifica UDP a " + username + " @ " 
    	        				+ info.getIpAddress() + ":" + info.getUdpPort());
    		}
    	} catch(Exception e) {
    		System.err.println("Errore invio notifica UDP: " + e.getMessage());
            e.printStackTrace();
    	}
	}
	
	//salva i trade avvenuti sul file storico JSON, thread safe
	private void persistTrades(List<Trade> newTrades) {
		if (newTrades == null || newTrades.isEmpty()) {
            return;
        }
		
		synchronized(historyFileLock) {
			try {
				Gson fileGson = new GsonBuilder().setPrettyPrinting().create();
				List<Trade> allTrades;
				
				//legge trade esistenti
				try(FileReader reader = new FileReader(historyFilename)){
					Type tradesListType = new TypeToken<ArrayList<Trade>>() {}.getType();
					allTrades = fileGson.fromJson(reader, tradesListType);
				} catch(Exception e) {	
					allTrades = new ArrayList<>(); //file inesistente
				}
				if(allTrades == null)
					allTrades = new ArrayList<>();	//se file vuoto
				
				//aggiunge nuovi trade 
				allTrades.addAll(newTrades);
				
				//scrive intera lista aggiornata nel file JSON
				try(FileWriter writer = new FileWriter(historyFilename)){
					fileGson.toJson(allTrades, writer);	//streaming serializer
				} //catch dal blocco esterno
				
				System.out.println("ClientHandler: Salvati " + newTrades.size() + " nuovi trade.");

            } catch (Exception e) {
                System.err.println("ClientHandler: Errore nel salvataggio dei trade!");
                e.printStackTrace();
            }
		}	//lock rilasciato automaticamente
	}
	
	//mappa i codici di errore ai messaggi in base all'operazione specifica
	private String getErrorMessage(int code, String operation) {
        if (code == 100) return "OK";

        switch (operation) {
            case "register":
                switch (code) {
                    case 101: return "Invalid password"; 
                    case 102: return "Username not available"; 
                    case 103: return "Other error cases"; 
                }
                break;
            
            case "login":
                switch (code) {
                    case 101: return "Username/password mismatch or non-existent username"; 
                    case 102: return "User already logged in"; 
                    case 103: return "Other error cases"; 
                }
                break;
            
            case "logout":
                switch (code) {
                    case 101: return "User not logged in"; 
                }
                break;
            
            case "cancelOrder":
                switch (code) {
                    case 101: return "Order does not exist or belongs to different user or has already been finalized"; 
                }
                break;
            case "getPriceHistory":
                switch(code) {
                    case 101: return "User not logged in";
                    case 103: return "Error reading history data file";
                }
                break;
            case "updateCredentials":
                switch (code) {
                    case 101: return "Invalid new password"; 
                    case 102: return "Username/old_password mismatch or non-existent username"; 
                    case 103: return "New password equal to old one"; 
                    case 104: return "User currently logged in"; 
                }
                break;
            case "getMyActiveOrders":
                if (code == 101) return "User not logged in";
                break;
            case "getOrderBookSnapshot":
                if (code == 101) return "User not logged in";
                break;
            default:
            	break;
        }
        
        //messaggi generici se non specificati
        if (code == -1) return "Operation failed";
        
        return "Unknown error code: " + code;
    }
}
