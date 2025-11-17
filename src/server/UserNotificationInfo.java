package ProgettoFinale.server;

import java.net.InetAddress;

/**
 * Contiene le informazioni di connessione (IP e porta UDP)
 * di un utente per l'invio delle notifiche.
 */

public class UserNotificationInfo {
    private final InetAddress ipAddress;
    private final int udpPort;

    public UserNotificationInfo(InetAddress ipAddress, int udpPort) {
        this.ipAddress = ipAddress;
        this.udpPort = udpPort;
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public int getUdpPort() {
        return udpPort;
    }
}