package ProgettoFinale.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.Type;

import ProgettoFinale.entities.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

public class ServerMain {
	
	private static final String SERVER_CONFIG_FILE = "server.properties";
	
	public static void main(String[] args) {
		System.out.println("Avvio del server CROSS...");
		
		//caricamento file config
		Properties config = new Properties();
		try(FileInputStream fis = new FileInputStream(SERVER_CONFIG_FILE)){
			config.load(fis);
		} catch (IOException e) {
            System.err.println("Errore: Impossibile trovare o caricare " + SERVER_CONFIG_FILE);
            e.printStackTrace();
            return;
        }
		
		//int tcpPort = Integer.parseInt(config.getProperty("tcp_port"));
		String tcpPortStr = config.getProperty("tcp_port");
		String usersFile = config.getProperty("users_db_file");
		String oldHistoryFile = config.getProperty("old_history_file");
        String historyFile = config.getProperty("trades_history_file");
        String activeBookFile = config.getProperty("active_book_file");
		
        //verifica correttezza dati config
        if (tcpPortStr == null || usersFile == null || oldHistoryFile == null || 
                historyFile == null || activeBookFile == null) { 
        	System.err.println("Errore: Il file 'server.properties' è incompleto.");
            if (tcpPortStr == null) System.err.println("Manca la chiave: 'tcp_port'");
            if (usersFile == null) System.err.println("Manca la chiave: 'users_db_file'");
            if (oldHistoryFile == null) System.err.println("Manca la chiave: 'old_history_file'");
            if (historyFile == null) System.err.println("Manca la chiave: 'trades_history_file'");
            if (activeBookFile == null) System.err.println("Manca la chiave: 'active_book_file'");
                
            System.err.println("Avvio interrotto.");
            return; //esce
        }
        
        int tcpPort;
        try {
            tcpPort = Integer.parseInt(tcpPortStr);
        } catch (NumberFormatException e) {
            System.err.println("Errore: 'tcp_port' (" + tcpPortStr + ") non è un numero valido.");
            System.err.println("Avvio interrotto.");
            return;
        }
        
        //logica id univoco
        long maxOldId = loadMaxOrderId(oldHistoryFile, OldTradeRecord.class); 
        long maxMyId = loadMaxOrderId(historyFile, Trade.class);
        long maxId = Math.max(maxOldId, maxMyId);	//trova max id
        OrderBook.setInitialOrderId(maxId);	//imposta id iniziale
        
		UserManager userManager = new UserManager(usersFile);	//gestore utenti threadsafe
		OrderBook orderBook = new OrderBook(activeBookFile);	//motore di matching threadsafe
		HistoryManager historyManager = new HistoryManager(oldHistoryFile, historyFile);
		
		//shutdwon hook per salvataggio stato ordini attivi
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("\nServer in chiusura...");
            orderBook.saveActiveStateToFile(); 
            System.out.println("Salvataggio stato attivo completato. Arrivederci.");
		}));
		
		DatagramSocket udpSocket = null;
		try {
			udpSocket = new DatagramSocket();	//porta effimera per invio da server
			System.out.println("Socket UDP (per invio) creato.");
		} catch (SocketException e) {
            System.err.println("Errore: impossibile creare il socket UDP. Uscita.");
            e.printStackTrace();
            return;
        }
		
		ExecutorService pool = Executors.newCachedThreadPool();	//riutilizza o crea thread se necessario
		
		//avvio server in thread separato
        final int finalTcpPort = tcpPort;
        final DatagramSocket finalUdpSocket = udpSocket;
        final String finalHistoryFile = historyFile;
        
        Thread serverThread = new Thread(() -> {
            try(ServerSocket serverSocket = new ServerSocket(finalTcpPort)) {
                System.out.println("=================================================");
                System.out.println("   Server CROSS avviato e in ascolto sulla porta " + finalTcpPort);
                System.out.println("   Digita 'STOP' e premi [INVIO] per arrestare.");
                System.out.println("=================================================");
                
                while(true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Nuova connessione da: " + clientSocket.getInetAddress());
                    
                    ClientHandler clientHandler = new ClientHandler(clientSocket, userManager, 
                            orderBook, finalUdpSocket, historyManager, finalHistoryFile);
                    pool.submit(clientHandler);
                }
            } catch (IOException e) {
                System.out.println("Thread server interrotto (chiusura normale).");
            } finally {
                pool.shutdown();
                if(finalUdpSocket != null) finalUdpSocket.close();
            }
        });
        
        serverThread.start();
        
        //attesa comando STOP su thread principale
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                String input = scanner.nextLine();
                if (input != null && input.trim().equalsIgnoreCase("STOP")) {
                    break; //esce dal loop se utente inserisce "STOP"
                }
            }
        }
        
        System.out.println("Comando 'STOP' ricevuto. Avvio chiusura pulita...");
        System.exit(0); // Esegue una chiusura pulita (attiva shutdown hook)
	}
	
	//carica il max order id
	private static <T> long loadMaxOrderId(String filename, Class<T> recordType) {
		long maxId = 0;
		Gson gson = new Gson();
		
		try(FileReader reader = new FileReader(filename))	{
			if(recordType.equals(Trade.class)) {
				Type listType = TypeToken.getParameterized(ArrayList.class, Trade.class).getType();
				List<Trade> records = gson.fromJson(reader, listType);
				if(records != null) {
					for(Trade trade : records) {
						maxId = Math.max(maxId, trade.getBuyOrderId());
						maxId = Math.max(maxId, trade.getSellOrderId());
					}
				}
					
			} else if(recordType.equals(OldTradeRecord.class)) {
				OldHistoryFile historyFile = gson.fromJson(reader, OldHistoryFile.class);
				if(historyFile != null && historyFile.getTrades() != null) {
					for(OldTradeRecord order : historyFile.getTrades())
						maxId = Math.max(maxId, order.getOrderId());
				}
			}
		} catch (Exception e) {	//file non trovato o vuoto        
            System.out.println("File storico '" + filename + "' non trovato o vuoto. Max ID rilevato: 0.");
        }
		
		System.out.println("Max ID rilevato da '" + filename + "': " + maxId);
        return maxId;
	}
}
