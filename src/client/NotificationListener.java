package ProgettoFinale.client;

import java.net.*;

import com.google.gson.*;
import ProgettoFinale.entities.*;

public class NotificationListener implements Runnable {
	private final int BUFFER_SIZE;
	private final DatagramSocket socket;
	private final Gson gson;
	
	public NotificationListener(int bufferSize) throws SocketException {
		this.socket = new DatagramSocket(0); //chiede al SO una porta libera (passando 0)
		this.gson = new Gson();
		this.BUFFER_SIZE = bufferSize;
	}
		
	public void run() {
		try{
			System.out.println("[Listener UDP] In ascolto sulla porta " + getPort());
            byte[] buffer = new byte[BUFFER_SIZE]; // Buffer per il pacchetto
            
            while(!Thread.currentThread().isInterrupted()) {	//ciclo di ascolto
            	DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            	
            	socket.receive(packet); //bloccante, aspetta un pacchetto
            	
            	//converte da byte a stringa
            	String jsonMessage = new String(packet.getData(), 0, packet.getLength());
            	
            	try {
            		TradeNotification notification = gson.fromJson(jsonMessage, TradeNotification.class);
            		
            		if(notification != null && notification.getTrades() != null) {
            			System.out.println("\n\r--- 🔔 NOTIFICA TRADE ESEGUITO ---");
                        
                        //itera su ogni trade ricevuto e stampa
                        for (TradeNotificationDetail trade : notification.getTrades()) {
                            System.out.printf(
                                "  > %s (%s) | Size: %d | Prezzo: %d | ID: %d\n",
                                trade.getType().toUpperCase(), // ASK o BID
                                trade.getOrderType(),          // limit, market, stop
                                trade.getSize(),
                                trade.getPrice(),
                                trade.getOrderId()
                            );
                        } 
            		} else {
                        // Se il JSON non è quello atteso, stampa il messaggio raw
                        System.out.println("\n\r--- 🔔 NOTIFICA DAL SERVER ---");
                        System.out.println(jsonMessage);
                    }
            	} catch(Exception e) {
                    System.err.println("[Listener UDP] Errore parsing notifica: " + e.getMessage());
                    // Stampa comunque il messaggio raw in caso di errore di parsing
                    System.out.println("\n\r--- 🔔 NOTIFICA DAL SERVER (RAW) ---");
                    System.out.println(jsonMessage);
                }
            	
                System.out.print("\rScegli un'opzione: "); // Ristampa il prompt del menu
            }
		} catch(Exception e) {
			if (!Thread.currentThread().isInterrupted()) {
                System.err.println("[Listener UDP] Errore: " + e.getMessage());
            }
        } finally {
        	this.socket.close();
            System.out.println("[Listener UDP] Terminato.");
        }
	}
	
	//metodo per dire al Main quale porta ha ottenuto
    public int getPort() {
        return this.socket.getLocalPort();
    }
}
