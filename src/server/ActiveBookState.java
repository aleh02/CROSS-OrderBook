package ProgettoFinale.server;

import ProgettoFinale.entities.*;
import java.util.*;

//classe per salvare lo stato attivo dell'order book in file JSON

public class ActiveBookState {	//ask, bid e stopOrders
	private final TreeMap<Integer, Queue<LimitOrder>> asks;
    private final TreeMap<Integer, Queue<LimitOrder>> bids;
    private final List<StopOrder> stopOrders;

    public ActiveBookState(TreeMap<Integer, Queue<LimitOrder>> asks, 
                           TreeMap<Integer, Queue<LimitOrder>> bids, 
                           List<StopOrder> stopOrders) {
        this.asks = asks;
        this.bids = bids;
        this.stopOrders = stopOrders;
    }

    public TreeMap<Integer, Queue<LimitOrder>> getAsks() { 
    	return asks; 
    }
    
    public TreeMap<Integer, Queue<LimitOrder>> getBids() { 
    	return bids; 
    }
    
    public List<StopOrder> getStopOrders() { 
    	return stopOrders; 
    }
}
