package ProgettoFinale.entities;

//Rappresenta un singolo trade all'interno del JSON di notifica.

public class TradeNotificationDetail {
    private final long orderId;
    private final String type;      // "ask" o "bid"
    private final String orderType; // "limit", "market", "stop"
    private final int size;
    private final int price;
    private final long timestamp;

    public TradeNotificationDetail(long orderId, String type, String orderType, int size, int price, long timestamp) {
        this.orderId = orderId;
        this.type = type;
        this.orderType = orderType;
        this.size = size;
        this.price = price;
        this.timestamp = timestamp;
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

	public long getTimestamp() {
		return timestamp;
	}
}