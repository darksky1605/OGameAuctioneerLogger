import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.net.ssl.SSLContext;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import org.json.JSONObject;

public class AuctioneerLogger {
	
	private static final Logger logger = Logger.getLogger( AuctioneerLogger.class.getName() );
	
	static {		
		Logger.getLogger("io").setLevel(Level.OFF);
		FileHandler fh = null;
		try {
			fh = new FileHandler("./auctions_%g_%u.txt", 67108864, 1024, true);
		} catch (SecurityException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}  
		fh.setFormatter(new SimpleFormatter()); //"%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%6$s%n"
		logger.addHandler(fh);
	}

	public String server = null;
	public int port = 0;
	private SocketIO socket = null;

	// init SSL
	static {
		SSLContext sc;
		try {
			sc = SSLContext.getInstance("TLS");
			sc.init(null, null, null);
			SocketIO.setDefaultSSLSocketFactory(sc);
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			throw new RuntimeException("Could not set SSLContext of socket.", e);
		}
	}

	public AuctioneerLogger(String server, int port) {
		this.server = server;
		this.port = port;
	}
	
	private String delim() {
		return " | ";
	}
	
	private String lgprefix() {
		return server + delim(); 
	}

	public void connect() throws MalformedURLException, NoSuchAlgorithmException, KeyManagementException {
		if (server == null)
			return;
		if (socket != null)
			socket.disconnect();

		socket = new SocketIO("https://" + server + ".ogame.gameforge.com:" + port + "/auctioneer");

		socket.connect(new IOCallback() {
			@Override
			public void onError(SocketIOException socketIOException) {
				AuctioneerLogger.this.onError(socketIOException);
			}

			@Override
			public void onDisconnect() {
				AuctioneerLogger.this.onDisconnect();
			}

			@Override
			public void onConnect() {
				AuctioneerLogger.this.onConnect();
			}

			@Override
			public void on(String event, IOAcknowledge ack, Object... args) {
				String arg = (String) args[0].toString();
				arg = arg.replace('\n', ' ');
				//logger.info(lgprefix() + " " + event + " " + delim() + arg);
				
				switch (event) {
				case "timeLeft":
					AuctioneerLogger.this.onTimeLeft(arg);
					break;
				case "auction finished":
					AuctioneerLogger.this.onAuctionFinished(arg);
					break;
				case "new auction":
					AuctioneerLogger.this.onNewAuction(arg);
					break;
				case "new bid":
					AuctioneerLogger.this.onNewBid(arg);
					break;
				default:
					AuctioneerLogger.this.onUnhandledEvent(event, args);					
					break;
				}
			}

			@Override
			public void onMessage(String arg0, IOAcknowledge arg1) {
				AuctioneerLogger.this.onMessage(arg0);
			}

			@Override
			public void onMessage(JSONObject arg0, IOAcknowledge arg1) {
				AuctioneerLogger.this.onMessage(arg0);
			}
		});
	}
	
	protected void onMessage(JSONObject arg0) {
		logger.info(lgprefix() + "onMessage" + delim() + arg0);
	}

	protected void onMessage(String arg0) {
		logger.info(lgprefix() + "onMessage" + delim() + arg0);
	}

	protected void onConnect() {
		logger.info(lgprefix() + "Connected");	
	}

	protected void onDisconnect() {
		logger.info(lgprefix() + "Disconnected");		
	}

	protected void onError(SocketIOException socketIOException) {
		logger.warning(lgprefix() + "Error. Trying to reconnect.");
		logger.warning(lgprefix() + socketIOException.getMessage());
		
		long backoffseconds = 1;
		int iteration = 0;
		boolean connected = false;
		
		while(!connected && iteration < 17) {
			try {
				connect();
				connected = true;
			} catch (MalformedURLException | NoSuchAlgorithmException | KeyManagementException e) {
				//e.printStackTrace();
				if(iteration >= 10) {
					logger.warning(lgprefix() + "Error. Reconnect failed in iteration " + iteration);
					logger.warning(lgprefix() + e.getMessage());
				}
				
				try {
					Thread.sleep(backoffseconds * 1000);
				} catch (InterruptedException e1) {
					logger.severe(lgprefix() + e1.getMessage());
				}
				
				backoffseconds *= 2;				
			}			
		}
		
		if(!connected) {
			logger.severe(lgprefix() + "Error. Reconnect attempt ultimately failed. Abort");
			socket.disconnect();
		}		
	}

	protected void onUnhandledEvent(String event, Object[] args) {
		logger.info(lgprefix() + "Unhandled event'" + event+ "'" + (args.length > 0 ? (String)args[0].toString() : ""));		
	}

	protected void onNewBid(String string) {
		logger.info(lgprefix() + " new bid " + delim() + string);
	}

	protected void onNewAuction(String string) {
		logger.info(lgprefix() + " new auction " + delim() + string);
	}

	protected void onAuctionFinished(String string) {
		logger.info(lgprefix() + " auction finished " + delim() + string);
	}

	protected void onTimeLeft(String string) {
		//logger.info(lgprefix() + " new bid " + delim() + string);
	}

	public void disconnect() {
		this.server = null;
		socket.disconnect();
	}

	public static void main(String args[])
			throws MalformedURLException, URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
		
		

		String filename = null;
		if(args.length == 0) {
			System.out.println("Usage: java -jar file.jar inputfile.txt");
			return;
		}
		
		filename = args[0];

		List<String> lines = null;

		try {
			lines = Files.readAllLines(Paths.get(filename));
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Cannot open input file " + filename);
			return;
		}

		ArrayList<AuctioneerLogger> loggerArray = new ArrayList<>();
		
		System.out.println("Auctioneer logger will try to log auctions of the following servers:");
		lines.forEach(System.out::println);
		
		lines.forEach(line -> {
			String[] tokens = line.split(" ");
			loggerArray.add(new AuctioneerLogger(tokens[0], Integer.parseInt(tokens[1])));
		});
		
		lines = null;

		loggerArray.forEach(logger -> {
			try {
				logger.connect();
			} catch (KeyManagementException | MalformedURLException | NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

		Scanner s = new Scanner(System.in);

		while (!s.next().equals("exit"))
			;

		s.close();
		loggerArray.forEach(logger -> logger.disconnect());

		System.exit(0); // cannot kill library threads in another way

	}

}
