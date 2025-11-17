package ProgettoFinale.entities;

import java.util.List;

/**
 * Rappresenta la struttura del file JSON
 * storico ordini fornito (es. {"trades": [...]})
 */

public class OldHistoryFile {
	//nome del campo deve corrispondere alla chiave JSON
	private List<OldTradeRecord> trades; 

    public List<OldTradeRecord> getTrades() {
        return trades;
    }
}
