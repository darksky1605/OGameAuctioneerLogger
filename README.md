# OGameAuctioneerLogger
Connects to the auctioneer of an OGame server without requiring an account. Saves auctions and bids to log file.

Auction messages are saved to file, and printed on the console. The following message types are recorded:

"new auction", "new bid", and "auction finished"

"timeLeft" messages are neither recorded nor displayed on the console.


**How to build:**

Use ANT to run the build script

**How to run:**

java -jar SimpleAuctioneerLogger.jar input.txt

input.txt specifies the servers to connect to. 
One line per server. A line must begin with the server id, followed by whitespace and the socket.io port number for this server.

See file allports.txt for a list of many servers with their respective port numbers.
