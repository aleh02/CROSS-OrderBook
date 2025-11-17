package ProgettoFinale.entities;

/**
 * Rappresenta un'operazione di scambio(trade) avvenuta
 * Contiene informazioni sull'esecuzione di un match
 * tra un ordine di acquisto e uno di vendita
 */

public class Trade {  
	//dati principali del Trade
    private final int price;
    private final int size;
    private final long timestamp;
    
    //dati del buyer(BID)
    private final long buyOrderId;
    private final String buyerUsername;
    private final String buyerOrderType; 	//"limit", "market", o "stop"
    
    //dati del seller(ASK)
    private final long sellOrderId;
    private final String sellerUsername;
    private final String sellerOrderType;	//"limit", "market", o "stop
    
    public Trade(long buyOrderId, long sellOrderId, int size, int price, 
    		String buyerUsername, String sellerUsername, long timestamp,
    		String buyerOrderType, String sellerOrderType) {
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.size = size;
        this.price = price;
        this.buyerUsername = buyerUsername;
        this.sellerUsername = sellerUsername;
        this.timestamp = timestamp;
        this.buyerOrderType = buyerOrderType;
        this.sellerOrderType = sellerOrderType;
    }
    
    public String toString() {
    	return "Trade{" +
                "buyer=" + buyerUsername +
                ", seller=" + sellerUsername +
                ", size=" + size +
                ", price=" + price +
                ", timestamp=" + timestamp +
                "}";
    }
    
    public long getBuyOrderId() { 
    	return buyOrderId; 
    }
    
    public long getSellOrderId() { 
    	return sellOrderId; 
    }
    
    public int getSize() { 
    	return size; 
    }
    
    public int getPrice() { 
    	return price; 
    }
    
    public String getBuyerUsername() { 
    	return buyerUsername; 
    }
    
    public String getSellerUsername() { 
    	return sellerUsername; 
    }
    
    public long getTimestamp() { 
    	return timestamp; 
    }
    
    public String getBuyerOrderType() { 
    	return buyerOrderType; 
    }
    
    public String getSellerOrderType() { 
    	return sellerOrderType; 
    }
}
