package ProgettoFinale.entities;

//classe market order

public class MarketOrder extends Order {
	public MarketOrder(String username, OrderType type, int size) {
		super(username, type, size);
	}
}
