package ProgettoFinale.entities;

/**
 * Classe stop order, rimane "dormiente" sul server finché il prezzo
 * di mercato non raggiunge lo stopPrice.
 *
 * Quando attivato, viene trattato come un market order.
 */

public class StopOrder extends Order {
	private final int stopPrice;
	
	public StopOrder(String username, OrderType type, int size, int stopPrice) {
		super(username, type, size);
		this.stopPrice = stopPrice;
	}
	
	public int getStopPrice() {
		return stopPrice;
	}
}
