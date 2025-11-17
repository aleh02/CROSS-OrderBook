package ProgettoFinale.entities;

//classe limit order

public class LimitOrder extends Order {
	private final int limitPrice;	//prezzo in millesimi di USD
	
	public LimitOrder(String username, OrderType type, int size, int limitPrice) {
		super(username, type, size);
		this.limitPrice = limitPrice;
	}
	
	public int getLimitPrice() {
		return limitPrice;
	}
}
