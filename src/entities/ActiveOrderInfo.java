package ProgettoFinale.entities;

//contiene dati per inviare dettagli di un ordine attivo al client

public class ActiveOrderInfo {
	private long orderId;
    private String type;      // "ask" o "bid"
    private String orderType; // "limit" o "stop"
    private int size;
    private int price;
    
    //costruttore vuoto per la deserializzazione Gson sul client
    public ActiveOrderInfo() {}
    
    //costruttore per la creazione sul server
    public ActiveOrderInfo(long orderId, String type, String orderType, int size, int price) {
        this.orderId = orderId;
        this.type = type;
        this.orderType = orderType;
        this.size = size;
        this.price = price;
    }
    
    public long getOrderId() { 
    	return orderId; 
    }
    
    public String getType() { 
    	return type; 
    }
    
    public String getOrderType() { 
    	return orderType; 
    }
    
    public int getSize() { 
    	return size; 
    }
    
    public int getPrice() { 
    	return price; 
    }
}
