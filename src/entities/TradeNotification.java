package ProgettoFinale.entities;

import java.util.*;

/**
 * Rappresenta l'oggetto JSON per una notifica di trade
 * Contiene una lista di trade individuali
 */

public class TradeNotification {
    private final String notification = "closedTrades";
    private final List<TradeNotificationDetail> trades;

    public TradeNotification(List<TradeNotificationDetail> trades) {
        this.trades = trades;
    }

	public List<TradeNotificationDetail> getTrades() {
		return trades;
	}

	public String getNotification() {
		return notification;
	}
}
