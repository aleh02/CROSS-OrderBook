package ProgettoFinale.server;

import ProgettoFinale.entities.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import com.google.gson.*;

/**
 * Gestiione delle strutture dati per gli ordini di acquisto (BID)
 * e di vendita (ASK), implementa l'algoritmo di matching e gestisce
 * l'attivazione degli Stop Order.
 *
 * Progettata per essere thread-safe utilizzando il meccanismo
 * synchronized di Java su tutti i metodi pubblici, garantendo che
 * solo un thread alla volta possa modificare lo stato dell'order book.
 */

public class OrderBook {
	//Ordinamento crescente degli ordini per prezzo, miglior ASK (prezzo più basso) è il primo elemento
	private final TreeMap<Integer, Queue<LimitOrder>> asks;	//Coda di ordini per gestire priorità temporale(FIFO) 
	
	//Ordinamento decrescente, miglior BID (prezzo più alto) è il primo elemento
	private final TreeMap<Integer, Queue<LimitOrder>> bids; //Coda per priorità temporale(FIFO)
	
	//Lista di stopOrder in attesa di attivazione
	private final List<StopOrder> stopOrders;
	
	//contatore atomico per generare id univoci(static) per gli ordini
	private static final AtomicLong orderIdGenerator = new AtomicLong(0);
	
	//nome del file su cui salvare/caricare lo stato attivo
	private final String activeBookFilename;
	
	//numero di livelli di prezzo da mostrare
	private static final int MAX_LEVELS_TO_SHOW = 10;
	
	public OrderBook(String activeBookFilename) {
		this.activeBookFilename = activeBookFilename;
		ActiveBookState loadedState = loadActiveStateFromFile();
		
		if(loadedState != null) {	//caricamento riuscito
            //popola l'order book con i dati letti dal file
            this.asks = loadedState.getAsks();
            this.bids = loadedState.getBids();
            //ricrea la lista synchronized dai dati letti
            this.stopOrders = Collections.synchronizedList(loadedState.getStopOrders()); 
            System.out.println("OrderBook: Stato attivo caricato con successo da " + this.activeBookFilename);
        } else {	//avvio pulito, inizializza order book da zero
        	this.asks = new TreeMap<>();
        	this.bids = new TreeMap<>(Collections.reverseOrder());	//ordinamento inverso(decrescente)
        	this.stopOrders = Collections.synchronizedList(new ArrayList<>());
        }
	}
	
	public static synchronized void setInitialOrderId(long maxId) {
		if (maxId > 0) {
            orderIdGenerator.set(maxId);
            System.out.println("OrderBook: Contatore ID impostato a " + maxId);
        } else {
            System.out.println("OrderBook: Contatore ID parte da 0.");
        }
	}
	
	public synchronized List<Trade> addLimitOrder(LimitOrder order) {
		List<Trade> completedTrades = new ArrayList<>();
		
		//Assegna id e timestamp
		order.setOrderId(orderIdGenerator.incrementAndGet());
		order.setTimestamp(System.currentTimeMillis());
		
		//tenta di matchare con gli ask
		if(order.getType() == OrderType.BID) {	//ordine acquisto (BID)
			//loop se miglior ask (firstkey) è <= prezzo dell'ordine
			while(order.getSize()>0 && !asks.isEmpty() && asks.firstKey() <= order.getLimitPrice()) {
				int bestAskPrice = asks.firstKey();
				Queue<LimitOrder> bestAsksQueue = asks.get(bestAskPrice);
				LimitOrder sellerOrder = bestAsksQueue.peek();	//Time-priority, guarda il primo
				
				if(sellerOrder == null) {
					asks.remove(bestAskPrice);	//coda vuota, pulisce ed esce
					break;
				}
				
				//controllo self trade
				if (sellerOrder.getUsername().equals(order.getUsername())) {
					// Trovato un self-trade
					bestAsksQueue.poll();	//annulla ordine l'ordine esistente
					
					if(bestAsksQueue.isEmpty())	//se coda per quel prezzo vuota
						asks.remove(bestAskPrice);	//rimuove prezzo

					System.out.println("STP: Annullato ordine ASK " + sellerOrder.getOrderId() + " per self-trade.");
					continue;
				}
				
				//esegue trade
				int tradePrice = sellerOrder.getLimitPrice();
				int tradeSize = Math.min(order.getSize(), sellerOrder.getSize());
				
				Trade trade = new Trade(
						order.getOrderId(), sellerOrder.getOrderId(),
						tradeSize, tradePrice, 
						order.getUsername(), sellerOrder.getUsername(),
						System.currentTimeMillis(),
						"limit", "limit");
				
				completedTrades.add(trade);
				
				//aggiorna le size
				order.setSize(order.getSize() - tradeSize);
				sellerOrder.setSize(sellerOrder.getSize() - tradeSize);
				
				if(sellerOrder.getSize() == 0)
					bestAsksQueue.poll(); //Rimuove dalla coda (FIFO)
				
				//se coda vuota per quel prezzo, rimuove il prezzo
				if(bestAsksQueue.isEmpty())
					asks.remove(bestAskPrice);
			}
			
			//se c'è redisuo nell'ordine viene riaggiunto ai bid
			if (order.getSize() > 0) {
				bids.computeIfAbsent(order.getLimitPrice(), k -> new LinkedList<>()).add(order);
			}
		} else {	// ordine vendita (ASK)
			while(order.getSize() > 0 && !bids.isEmpty() && bids.firstKey() >= order.getLimitPrice()) {
				int bestBidPrice = bids.firstKey();
				Queue<LimitOrder> bestBidsQueue = bids.get(bestBidPrice);
				LimitOrder buyerOrder = bestBidsQueue.peek();
				
				if(buyerOrder == null) {
                    bids.remove(bestBidPrice);
                    break;
                }
				
				//controllo self trade
				if (buyerOrder.getUsername().equals(order.getUsername())) {
					// Trovato un self-trade
					bestBidsQueue.poll();	//annulla ordine l'ordine esistente
					
					if(bestBidsQueue.isEmpty())	//se coda per quel prezzo vuota
						bids.remove(bestBidPrice);	//rimuove prezzo

					System.out.println("STP: Annullato ordine BID " + buyerOrder.getOrderId() + " per self-trade.");
					continue;
				}
				
				int tradePrice = buyerOrder.getLimitPrice();
				int tradeSize = Math.min(order.getSize(), buyerOrder.getSize());
				
				Trade trade = new Trade(
						buyerOrder.getOrderId(), order.getOrderId(),
						tradeSize, tradePrice, 
						buyerOrder.getUsername(), order.getUsername(),
						System.currentTimeMillis(),
						"limit", "limit");
				
				completedTrades.add(trade);
				
				order.setSize(order.getSize() - tradeSize);
				buyerOrder.setSize(buyerOrder.getSize() - tradeSize);
				
				if(buyerOrder.getSize() == 0)
					bestBidsQueue.poll();
				
				if(bestBidsQueue.isEmpty())
					bids.remove(bestBidPrice);
			}
			
			if(order.getSize() > 0) {
				asks.computeIfAbsent(order.getLimitPrice(), k -> new LinkedList<>()).add(order);
			}
		}
		
		// Dopo ogni operazione, controlla se si attivano gli Stop Order
		List<Trade> stopTrades = checkStopOrders();
		completedTrades.addAll(stopTrades);
		
		return completedTrades;
	}
	
	//chiama la logica privata, passando "market" come tipo
	public synchronized List<Trade> executeMarketOrder(MarketOrder order) throws Exception {
	    return executeMarketOrderLogic(order, "market");
	}
	
	//aggiunge stop order alla lista di monitoraggio
	public synchronized void addStopOrder(StopOrder order) {
        order.setOrderId(orderIdGenerator.incrementAndGet());
        order.setTimestamp(System.currentTimeMillis());
        this.stopOrders.add(order);
    }
	
	//Restituisce una vista dello stato attuale dell'order book (primi 10 livelli, solo limit orders)
	public synchronized String getOrderBookSnapshot() {
		
		StringBuilder sb = new StringBuilder();
		int asksCount = 0;

		//asks ordinati dal prezzo piu basso(best) al piu alto
		sb.append("--- ASKS (VENDITE) ---\n");
		sb.append(String.format("%-10s | %-15s\n", "PREZZO", "QUANTITÀ"));
        sb.append("--------------------------\n");
        
        for(Map.Entry<Integer, Queue<LimitOrder>> entry : asks.entrySet()) {
        	if(asksCount >= MAX_LEVELS_TO_SHOW)
        		break;
        	
        	int price = entry.getKey();
        	Queue<LimitOrder> queue = entry.getValue();
        	
        	//somma delle size di tutti gli ordini nella coda
        	int totalSizeAtPrice = 0;
        	for (LimitOrder order : queue)
        		totalSizeAtPrice += order.getSize();
        	
        	sb.append(String.format("%-10d | %-15d\n", price, totalSizeAtPrice));
            asksCount++;
        }
        
        if (asksCount == 0)
            sb.append(" (Vuoto)\n");
        
        //bids ordinati dal prezzo piu alto(best) al piu basso
        sb.append("--- BIDS (ACQUISTI) ---\n");
        sb.append(String.format("%-10s | %-15s\n", "PREZZO", "QUANTITÀ"));
        sb.append("--------------------------\n");
        
        int bidsCount = 0;
        
        for (Map.Entry<Integer, Queue<LimitOrder>> entry : bids.entrySet()) {
            if (bidsCount >= MAX_LEVELS_TO_SHOW)
                break;
            
            int price = entry.getKey();
            Queue<LimitOrder> queue = entry.getValue();
            
            int totalSizeAtPrice = 0;
            for (LimitOrder order : queue)
                totalSizeAtPrice += order.getSize();

            sb.append(String.format("%-10d | %-15d\n", price, totalSizeAtPrice));
            bidsCount++;
        }
        
        if (bidsCount == 0) 
            sb.append(" (Vuoto)\n");
        
        return sb.toString();	
	}
	
	//ordine cancellato solo se non è ancora stato ancora (completamente) evaso
	public synchronized boolean cancelOrder(long orderId, String username) {
		if(removeOrderFromMap(bids, orderId, username)) {
			checkStopOrders();	//la cancellazione può cambiare il best-bid
			return true;
		}
		
		if(removeOrderFromMap(asks, orderId, username)) {
			checkStopOrders(); //la cancellazione può cambiare il best-ask
			return true;
		}
		Iterator<StopOrder> stopIterator = stopOrders.iterator();
		while(stopIterator.hasNext()) {
			StopOrder order = stopIterator.next();
			
			if(order.getOrderId() == orderId && order.getUsername().equals(username)) {
				stopIterator.remove();	//non serve checkStopOrders perché gli stop order non sono nell'order book
				return true;
			}
		}

		//se non trovato ritorna false
		return false;
	}
	
	//order tutto o niente, se non completamente evaso viene scartato
		private synchronized List<Trade> executeMarketOrderLogic(Order order, String incomingOrderType) throws Exception {
	        List<Trade> completedTrades = new ArrayList<>();
	        int sizeToFill = order.getSize();
			
	        boolean canBeFilled = false;
	        int availableSize = 0;
	        String incomingUsername = order.getUsername();	//username di chi sta ordinando
	        
	        if(order.getType() == OrderType.BID) {	//BID(buy), consugma gli ASK
	        	for(Queue<LimitOrder> queue : asks.values()) {
	        		for(LimitOrder askOrder : queue) {
	        			if (!askOrder.getUsername().equals(incomingUsername))	//controlla size se non è un self trade
	        				availableSize += askOrder.getSize();
	        			if(availableSize >= sizeToFill) {
	        				canBeFilled = true;
	        				break;
	        			}
	        		}
	        		if(canBeFilled) break;
	        	}
	        } else {	//ASK(sell), consuma i BID
	        	for(Queue<LimitOrder> queue : bids.values()) {
	        		for(LimitOrder bidOrder : queue) {
	        			if (!bidOrder.getUsername().equals(incomingUsername))	//controllo selftrade prevention
	        				availableSize += bidOrder.getSize();
	        			if(availableSize >= sizeToFill) {
	        				canBeFilled = true;
	        				break;
	        			}
	        		}
	        		if(canBeFilled) break;
	        	}
	        }
	        
	        if (!canBeFilled) 
	        	throw new Exception("Ordine (" + incomingOrderType + ") fallito: liquidità non sufficiente.");
	                
	        //assegna id e timestamp se non è un MarketOrder che li ha già
	        if(order.getOrderId() == -1) {	//gli StopOrder li hanno già
	        	order.setOrderId(orderIdGenerator.incrementAndGet());
	            order.setTimestamp(System.currentTimeMillis());
	        }
	        
	        if(order.getType() == OrderType.BID) {	//esecuzione BUY consumando ASKs
	        	Iterator<Map.Entry<Integer, Queue<LimitOrder>>> asksIterator = asks.entrySet().iterator();
	        	
	        	while(sizeToFill > 0 && asksIterator.hasNext()) {
	        		Map.Entry<Integer, Queue<LimitOrder>> entry = asksIterator.next();
	        		int tradePrice = entry.getKey();
	        		Queue<LimitOrder> queue = entry.getValue();
	        		
	        		Iterator<LimitOrder> queueIterator = queue.iterator();
	        		while(sizeToFill > 0 && queueIterator.hasNext()) {
	        			LimitOrder sellerOrder = queueIterator.next();
	        			
	        			if (sellerOrder.getUsername().equals(incomingUsername)) {
	        				queueIterator.remove();	//rimuove ordine dalla coda
	        				System.out.println("STP: Annullato ordine ASK " + sellerOrder.getOrderId() + " per self-trade.");
	        				
	        				if (queue.isEmpty())	//controlla se coda vuota
	        						asksIterator.remove();	//rimuove prezzo
	        				
	        				continue;
	        			}
	        			
	        			int tradeSize = Math.min(sizeToFill, sellerOrder.getSize());
	        			
	        			Trade trade = new Trade(
	        					order.getOrderId(), sellerOrder.getOrderId(),
	        					tradeSize, tradePrice,
	        					order.getUsername(), sellerOrder.getUsername(),
	        					System.currentTimeMillis(),
	        					incomingOrderType, "limit");
	        			
	        			completedTrades.add(trade);
	        			
	        			sizeToFill -= tradeSize;
	        			sellerOrder.setSize(sellerOrder.getSize() - tradeSize);
	        			
	        			if(sellerOrder.getSize() == 0)
	        				queueIterator.remove(); //se ordine evaso completamente lo rimuove
	        		}
	        		
	        		if(queue.isEmpty())	//se coda vuota rimuove prezzo dall'order book
	        			asksIterator.remove();	//iterator gestisce ConcurrentModificationException
	        	}
	        } else {	//esecuzione SELL consumando BIDs
	        	Iterator<Map.Entry<Integer, Queue<LimitOrder>>> bidsIterator = bids.entrySet().iterator();
	        	
	        	while(sizeToFill > 0 && bidsIterator.hasNext()) {
	        		Map.Entry<Integer, Queue<LimitOrder>> entry = bidsIterator.next();
	        		
	        		int tradePrice = entry.getKey();
	        		Queue<LimitOrder> queue = entry.getValue();
	        		
	        		Iterator<LimitOrder> queueIterator = queue.iterator();
	        		while(sizeToFill > 0 && queueIterator.hasNext()) {
	        			LimitOrder buyerOrder = queueIterator.next();
	        			
	        			if (buyerOrder.getUsername().equals(incomingUsername)) {	//self trade
	                        queueIterator.remove();	//annulla ordine esistente (BID)
	                        System.out.println("STP: Annullato ordine BID " + buyerOrder.getOrderId() + " per self-trade.");

	                        if (queue.isEmpty()) {
	                            bidsIterator.remove();	//rimuove prezzo
	                        }
	                        continue;
	                    }
	        			
	        			int tradeSize = Math.min(sizeToFill, buyerOrder.getSize());
	        			
	        			Trade trade = new Trade(
	        					buyerOrder.getOrderId(), order.getOrderId(),
	        					tradeSize, tradePrice, 
	        					buyerOrder.getUsername(), order.getUsername(),
	        					System.currentTimeMillis(),
	        					"limit", incomingOrderType);
	        			
	        			completedTrades.add(trade);
	        			
	        			sizeToFill -= tradeSize;
	        			buyerOrder.setSize(buyerOrder.getSize() - tradeSize);
	        			
	        			if(buyerOrder.getSize() == 0)
	        				queueIterator.remove();
	        		}
	        		if(queue.isEmpty())
	        			bidsIterator.remove();
	        	}
	        }
	        
	        //controlla gli stopOrder dopo i trade
	        List<Trade> stopTrades = checkStopOrders();
	        completedTrades.addAll(stopTrades);
	        
	        return completedTrades;
	    }
	
	//cerca e rimuove ordine da una delle TreeMap (bids o asks)
	private boolean removeOrderFromMap(TreeMap<Integer, Queue<LimitOrder>> map, long orderId, String username) {
		//itera sulle entry della TreeMap per poter rimuovere l'intera entry se la coda si svuota
		Iterator<Map.Entry<Integer, Queue<LimitOrder>>> mapIterator = map.entrySet().iterator();
		while(mapIterator.hasNext()) {
			Map.Entry<Integer,Queue<LimitOrder>> entry = mapIterator.next();
			Queue<LimitOrder> queue = entry.getValue();
			
			Iterator<LimitOrder> queueIterator = queue.iterator();
			while(queueIterator.hasNext()) {
				LimitOrder order = queueIterator.next();
				
				if(order.getOrderId() == orderId && order.getUsername().equals(username)) {
					queueIterator.remove();	//trovato, rimuove dalla coda
					
					if(queue.isEmpty())	//se coda vuota
						mapIterator.remove();	//rimuove l'intera entry(livello di prezzo) dalla map
					
					return true;
				}
			}
		}	
		return false;	//non trovato
	}
	
	//controlla se StopOrder vengono attivati, ritorna lista di trade generati dagli StopOrder attivati
	private List<Trade> checkStopOrders() {	//metodo privato chiamato solo da metodi synchronized
		List<Trade> stopTrades = new ArrayList<>();
		
		Integer bestBid = bids.isEmpty() ? null : bids.firstKey();
		Integer bestAsk = asks.isEmpty() ? null : asks.firstKey();
		
		//iterator per rimuovere in sicurezza
		Iterator<StopOrder> iterator = stopOrders.iterator();
		while(iterator.hasNext()) {
			StopOrder stopOrder = iterator.next();
			boolean activated = false;
			
			//se ASK(sell) si attivaa se il bestAsk scende a <= stopPrice
			if(stopOrder.getType() == OrderType.ASK && bestBid != null)
				if(bestBid <= stopOrder.getStopPrice())
					activated = true;
			
			//se BID(buy) si attiva se il bestBid sale a >= stopPrice
			if(stopOrder.getType() == OrderType.BID && bestAsk != null)
				if(bestAsk >= stopOrder.getStopPrice())
					activated = true;
		
		
			if(activated) {
				iterator.remove();	//rimuove da lista di attesa
				
				try {	//esegue come market order
					List<Trade> trades = executeMarketOrderLogic(stopOrder, "stop");
					stopTrades.addAll(trades);
				} catch(Exception e) {	//ordine attivato ma fallito
					System.err.println("StopOrder " + stopOrder.getOrderId() + " attivato ma fallito: " + e.getMessage());
                    // TODO: Notificare l'utente del fallimento?
				}
			}
		}
		return stopTrades;
	}
	
	//carica lo stato attivo dal file, svuotato dopo caricamento 
	private ActiveBookState loadActiveStateFromFile() {
		File stateFile = new File(this.activeBookFilename); 
        if (!stateFile.exists()) {
            return null;
        }

        ActiveBookState state = null;
		Gson gson = new Gson();
		
		try(FileReader reader = new FileReader(stateFile)){
			state = gson.fromJson(reader, ActiveBookState.class);
			
			if(state == null || 
				(state.getAsks() == null && state.getBids() == null && state.getStopOrders() == null) ||
				(state.getAsks().isEmpty() && state.getBids().isEmpty() && state.getStopOrders().isEmpty()))
				return null;	//file vuoto o senza dati
		} catch(Exception e) {
			System.err.println("OrderBook: Errore nel caricamento di " + this.activeBookFilename + ". Avvio pulito.");
            e.printStackTrace();
            return null; //non procede se il caricamento fallisce
		}
		
		//svuoto file con un JSON vuoto {}
		try(FileWriter writer = new FileWriter(stateFile)){
			writer.write("{}");	//sovrascrive
			System.out.println("OrderBook: Stato " + this.activeBookFilename + " caricato e svuotato per sicurezza.");
		} catch(Exception e) {
			System.err.println("OrderBook: ATTENZIONE! Impossibile svuotare il file di stato " + this.activeBookFilename);
		}
		
		return state;
	}
	
	public synchronized void saveActiveStateToFile() {
		//non salva se order book vuoto
		if(asks.isEmpty() && bids.isEmpty() && stopOrders.isEmpty()) {
			System.out.println("OrderBook: Stato attivo vuoto, nessun salvataggio necessario.");
            return;
		}
		
		ActiveBookState state = new ActiveBookState(asks, bids, stopOrders);
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		try(FileWriter writer = new FileWriter(this.activeBookFilename)){
			gson.toJson(state, writer);
			System.out.println("OrderBook: Stato attivo salvato con successo su " + this.activeBookFilename);
        } catch (Exception e) {
            System.err.println("OrderBook: Errore nel salvataggio dello stato attivo.");
            e.printStackTrace();
        }
	}
	
	//ritorna lista di tutti gli ordini attivi (Limit e Stop) per un utente specifico
	public synchronized List<ActiveOrderInfo> getActiveOrders(String username) {
        List<ActiveOrderInfo> activeOrders = new ArrayList<>();
        
        for (Queue<LimitOrder> queue : asks.values()) {	//cerca negli ASK(Limit Orders)
            for (LimitOrder order : queue) {
                if (order.getUsername().equals(username)) {
                    activeOrders.add(new ActiveOrderInfo(
                        order.getOrderId(), "ask", "limit",
                        order.getSize(), order.getLimitPrice()
                    ));
                }
            }
        }
        
        for (Queue<LimitOrder> queue : bids.values()) {	//cerca nei BID(Limit Orders)
            for (LimitOrder order : queue) {
                if (order.getUsername().equals(username)) {
                    activeOrders.add(new ActiveOrderInfo(
                        order.getOrderId(), "bid", "limit",
                        order.getSize(), order.getLimitPrice()
                    ));
                }
            }
        }
        
        //non ho bisogno di synchronized(stopOrders) perché il metodo è synchronized
        for (StopOrder order : stopOrders) {	//cerca negli STOP(Stop Orders)
        	if (order.getUsername().equals(username)) {
        		activeOrders.add(new ActiveOrderInfo(
        			order.getOrderId(), order.getType().toString().toLowerCase(), "stop",
                    order.getSize(), order.getStopPrice()
                ));
        	}
        }
        
        return activeOrders;
    }
}
