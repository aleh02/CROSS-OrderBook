package ProgettoFinale.server;

import ProgettoFinale.entities.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;	//per passare a Gson le generics che Java cancella a runtime

import java.io.*;
import java.lang.reflect.Type;	//per ricordare i tipi generics
import java.text.SimpleDateFormat;
import java.util.*;

//gestisce lettura ed elaborazione dei dati storici dei trade

public class HistoryManager {
	private final String oldHistoryFilename;
	private final String myHistoryFilename;
	private final Gson gson;
	
	//classe interna per dati open high low close di un singolo giorno
	public static class OhlcData {
		String date;	//"YYYY-MM-DD"
		int open;
		int high;
		int low;
		int close;
		
		public OhlcData(String date) {
			this.date = date;
			this.open = -1;
			this.high = Integer.MIN_VALUE;
			this.low = Integer.MAX_VALUE;
			this.close = -1;
		}
	}
	
	//classe interna per unificare i due formati di file
	private static class HistoryRecord {
		long timestamp;
        int price;
        
        HistoryRecord(long t, int p) { 
        	timestamp = t; 
        	price = p;
        }
        
        long getTimestamp() { 
        	return timestamp; 
        }
        int getPrice() { 
        	return price; 
        }
	}
	
	public HistoryManager(String oldHistoryFilename, String myHistoryFilename) {
		this.oldHistoryFilename = oldHistoryFilename;
		this.myHistoryFilename = myHistoryFilename;
		this.gson = new GsonBuilder().setPrettyPrinting().create();
	}
	
	//calcola dati OHLC per un mese specifico, restituisce Map(giorno->OhlcData)
	public Map<String, OhlcData> getHistory(String monthYear) {
		List<HistoryRecord> allRecords = new ArrayList<>();
		
		//carica i trade da oldHistory
        try (Reader reader = new FileReader(oldHistoryFilename)) {
            OldHistoryFile historyFile = gson.fromJson(reader, OldHistoryFile.class);
            if (historyFile != null && historyFile.getTrades() != null) {
                for (OldTradeRecord order : historyFile.getTrades()) {
                	//converte timestamp da secondi a millisecondi
                	long timestampInMillis = order.getTimestamp() * 1000L;
                    allRecords.add(new HistoryRecord(timestampInMillis, order.getPrice()));
                }
            }
        } catch (Exception e) {
            System.err.println("HistoryManager: Errore lettura file storico ordini fornito " + oldHistoryFilename);
            e.printStackTrace();
        }
		
        //carica i trade dal trades history dinamico
		try(FileReader reader = new FileReader(this.myHistoryFilename)) {
			//definizione tipo corretto per Gson, lista di HistoryTrade
			Type listType = new TypeToken<ArrayList<Trade>>() {}.getType();
			List<Trade> myTrades = gson.fromJson(reader, listType);
			if(myTrades != null) {
				for(Trade trade : myTrades)
					allRecords.add(new HistoryRecord(trade.getTimestamp(), trade.getPrice()));
			}
		} catch(Exception e) {
			System.err.println("HistoryManager: Errore lettura file storico " + myHistoryFilename);
		}
		
		//filtra la lista unita per il mese richiesto
		List<HistoryRecord> filteredTrades = new ArrayList<>();
		SimpleDateFormat monthSdf = new SimpleDateFormat("MMYYYY");
		monthSdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		
		for(HistoryRecord trade : allRecords) {
			String tradeMonth = monthSdf.format(new Date(trade.getTimestamp()));
			if(tradeMonth.equals(monthYear))
				filteredTrades.add(trade);
		}
		
		if(filteredTrades.isEmpty()) {
			System.out.println("HistoryManager: Nessun trade trovato per " + monthYear);
            return new TreeMap<>(); // Ritorna mappa vuota
		}
		
		//ordina i trade per data
		filteredTrades.sort(Comparator.comparingLong(HistoryRecord::getTimestamp)); //come se facessi (HistoryRecord trade) -> trade.getTimestamp()
		
		//calcola i dati OHLC
		Map<String, OhlcData> monthlyData = new TreeMap<>(); //treemap per giorni ordinati
		SimpleDateFormat daySdf = new SimpleDateFormat("yyyy-MM-dd");
		daySdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		
		for(HistoryRecord trade : filteredTrades) {
			String day = daySdf.format(new Date(trade.getTimestamp()));
			
			//crea nuova entry per il giorno se non presente
			OhlcData dayData = monthlyData.computeIfAbsent(day, OhlcData::new);
			
			//aggiorna valori
			if(dayData.open == -1)
				dayData.open = trade.getPrice();	//prezzo apertura
			
			if(trade.getPrice() > dayData.high)
				dayData.high = trade.getPrice();	//prezzo max
			
			if(trade.getPrice() < dayData.low)
				dayData.low = trade.getPrice();		//prezzo min
			
			dayData.close = trade.getPrice(); //ultimo trade sovrascrive quello di chiusura
		}
		
		return monthlyData;
	}
}
