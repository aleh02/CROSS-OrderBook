# 💱 CROSS – BTC/USD Exchange Simulator  
A multithreaded client–server Bitcoin exchange simulator built in Java.  
Developed as part of *Laboratorio 3 – Università di Pisa*.

The system implements a simplified cryptocurrency exchange with:
- An **Order Book** (bid/ask)  
- **Matching Engine** (market & limit orders)  
- **Concurrent request handling** via a **Thread Pool**  
- **TCP/UDP dual communication**  
- **JSON persistence** for storing orders and trades  
- **Custom request/response protocol**

---

## 📌 Overview

CROSS is a distributed system composed of a **server** hosting the exchange logic and multiple **clients** that submit buy/sell orders.  
The server maintains an in-memory Order Book and processes each request concurrently using a thread pool.

The project demonstrates:
- socket programming (TCP for requests, UDP for notifications)  
- concurrency and shared-state management  
- data serialization (JSON)  
- order matching algorithms  
- synchronous and asynchronous communication  

---

## 🧩 Features

### ✔ Order Types  
- **LIMIT BUY / SELL**  
- **MARKET BUY / SELL**

### ✔ Matching Engine  
- FIFO within same price level  
- Price–time priority  
- Partial fills  
- Trade generation  

### ✔ Thread Pool Server  
All requests are handled through a custom thread pool to ensure:
- bounded concurrency  
- FIFO processing of submitted jobs  
- deterministic scheduling  
- improved resource management

### ✔ Dual Protocol Communication  
- **TCP** → order submission, queries, client-server requests  
- **UDP** → asynchronous event notifications (e.g., “order filled”, “trade executed”)

### ✔ Persistence  
The Order Book and executed trades are stored using:
- **JSON** files for readability and debugging  
- simple data recovery on restart

---

## 🏛 Architecture

               +-------------------+
               |      Clients      |
               |  TCP / UDP Socket |
               +---------+---------+
                         |
                         v
+--------------------------------------------------------+
| Server                                                     |               |                 |
| ---------------------------------------------------------- | ------------- | --------------- |
| Request Handler                                            | Thread Pool   | Order Book      |
| (TCP Listener)                                             | (Worker pool) | (Bid/Ask lists) |

+--------------------------------------------------------+

            | Matching Engine |
            +------------------+
            |  JSON Storage    |
            +------------------+
                         |
                         v
                 UDP Notifications

---

## 🗂 Project Structure
/src

├── server/

│ ├── CrossServer.java

│ ├── ThreadPool.java

│ ├── Worker.java

│ ├── OrderBook.java

│ ├── Order.java

│ ├── Trade.java

│ ├── JsonStorage.java

│ └── Protocol.java

│

├── client/

│ ├── CrossClient.java

│ └── UdpListener.java

│

└── utils/

├── Logger.java

└── Config.java


---

## ⚙️ Build & Run

### 🔧 Requirements
- Java 17+
- Terminal with `javac`/`java`

### ▶ Compile
```bash
javac -d out src/**/*.java

java -cp out server.CrossServer

java -cp out client.CrossClient
```

🧪 Example Interaction (simplified)
NEW_ORDER LIMIT BUY 45000 0.5

Server:

[INFO] Received BUY LIMIT 0.5 BTC @ 45000$
[INFO] Added to OrderBook (BID)


Matching Engine:

[TRADE] BUY 0.3 BTC matched @ 44900$ with SELL order #18


Server → Client (UDP):

TRADE_EXECUTED order=42 filled=0.3 price=44900


🧠 Core Concepts Demonstrated
🧵 Concurrency

-Custom ThreadPool

-Worker threads

-Shared order book protected against race conditions

🔌 Networking

-TCP socket server

-TCP clients

-UDP message broadcast

📊 Data Structures

-Priority queues for bid/ask

-FIFO queues inside price levels

-Trade history list

💾 Persistence

-JSON encoding/decoding

-Automatic recovery of saved state

🧱 Distributed Protocols

-Custom text-based protocol for commands

-Asynchronous events over UDP


👤 Author

Alessandro Han

Computer Science, University of Pisa

LinkedIn: https://www.linkedin.com/in/alessandro-han-b87391223/
