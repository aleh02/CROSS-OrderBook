package ProgettoFinale.entities;

//classe astratta ordine generico

public abstract class Order {
	protected long orderId;
	protected final String username;
	protected final OrderType type;	//ASK o BID
	protected int size;			//Millesimi di BTC
	protected long timestamp;
	
	public Order(String username, OrderType type, int size) {
		this.username = username;
		this.type = type;
		this.size = size;
		
		orderId = -1;
		timestamp = -1;
	}
	
	public long getOrderId() {
		return orderId;
	}
	
	public String getUsername() {
		return username;
	}
	
	public OrderType getType() {
		return type;
	}
	
	public int getSize() {
		return size;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setOrderId(long id) {
		this.orderId = id;
	}
	
	public void setTimestamp(long t) {
		this.timestamp = t;
	}
	
	public void setSize(int s) {
		this.size = s;
	}
}
