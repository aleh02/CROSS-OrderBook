package ProgettoFinale.entities;

/**
 * Rappresenta la struttura di un record nel file storico
 * fornito, usato solo per la lettura
 */

public class OldTradeRecord {
	long orderId;
    int price;
    long timestamp;

    public long getOrderId() { 
    	return orderId; 
    }
    
    public int getPrice() { 
    	return price; 
    }
    
    public long getTimestamp() { 
    	return timestamp; 
    }
}
