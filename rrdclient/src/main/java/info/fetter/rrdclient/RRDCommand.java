package info.fetter.rrdclient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.log4j.Logger;

/**
 * 
 * Generic RRD command. This class must be subclassed to implement specific RRD commands.
 * 
 * @author Didier Fetter
 *
 */
public abstract class RRDCommand {
	private static Logger logger = Logger.getLogger(RRDCommand.class);
	private static ConnectionCache cache = new ConnectionCache();
	protected InetAddress serverAddress;
	protected int serverPort;
	protected double userTime, systemTime;
	String returnCode;
	
	/**
	 * Execute the RRD command and send the result to an OutputStream. Server address and port must have been set before.
	 * 
	 * @param out the OutputStream the result will be sent to
	 */
	public abstract void execute(OutputStream out);
	
	/**
	 * Execute the RRD command and send the result to standard output. Server address and port must have been set before.
	 */
	public abstract void execute();
	
	/**
	 * 
	 * Execute the RRD command and send the result to an OutputStream.
	 * 
	 * @param out the OutputStream the result will be sent to
	 * @param address the server address (host name or IP address)
	 * @param port the server port
	 */
	public void execute(OutputStream out, String address, int port) {
		setServerAddress(address);
		setServerPort(port);
		execute(out);
	}
	
	/**
	 * 
	 * Execute the RRD command and send the result to standard output.
	 * 
	 * @param address the server address (host name or IP address)
	 * @param port the server port
	 */
	public void execute(String address, int port) {
		setServerAddress(address);
		setServerPort(port);
		execute();
	}
	
	/**
	 * Set RRD server address.
	 * 
	 * @param serverAddress
	 */
	public void setServerAddress(String serverAddress) {
		try {
			this.serverAddress = InetAddress.getByName(serverAddress);
		} catch (UnknownHostException e) {
			throw new IllegalArgumentException(e);
		}
	}
	
	/**
	 * Set RRD server port.
	 * 
	 * @param serverPort
	 */
	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}
	
	
	/**
	 * Used by subclasses. Send the command to the server using the SocketChannel and return the result to the ByteBuffer.
	 * 
	 * @param command command to send to the server
	 * @return ByteBuffer containing the result of the command
	 * @throws IOException
	 */
	protected ByteBuffer sendCommandToServer(String command) throws IOException {
		command += "\n";
		InetSocketAddress server = new InetSocketAddress(serverAddress, serverPort);
		SocketChannel channel = cache.get(server);
		ByteBuffer sendBuffer = ByteBuffer.wrap(command.getBytes());
		ByteBuffer receiveBuffer = ByteBuffer.allocate(1024*1024);
		int bytesWritten = channel.write(sendBuffer);
		if(logger.isTraceEnabled())
			logger.trace("Sent " + bytesWritten + " bytes to " + server);
		
		boolean stillSomethingToRead = true;
		while(stillSomethingToRead) {
			logger.trace("There is still something to read");
			int bytesRead = channel.read(receiveBuffer);
			if(logger.isTraceEnabled())
				logger.trace("Received " + bytesRead + " bytes from " + server);
			int position = receiveBuffer.position();
			if(receiveBuffer.get(--position) == '\n') {
				boolean rewind = true;
				while(rewind && position >= 0) {
					byte previousByte = receiveBuffer.get(--position);
					if(previousByte == '\n')
						rewind = false;
				}
				int lineStart = position + 1;
				if(receiveBuffer.get(lineStart) == 'O' && receiveBuffer.get(lineStart + 1) == 'K') {
					stillSomethingToRead = false;
					logger.trace("Found OK");
				}
				if(receiveBuffer.get(lineStart) == 'E' && receiveBuffer.get(lineStart + 1) == 'R' && receiveBuffer.get(lineStart + 2) == 'R') {
					stillSomethingToRead = false;
					logger.trace("Found ERROR");
				}
			}
		}
		
		receiveBuffer.flip();
		if(logger.isTraceEnabled())
			logger.trace("Received a total of " + receiveBuffer.limit() + " bytes from " + server);
		return receiveBuffer;
	}
	
	/**
	 * Used by subclasses. Verify if the server returned an error. If an error is detected, a RuntimException containing the error message is raised.
	 * 
	 * @param line
	 */
	protected void verifyIfError(String line) {
		if(line.startsWith("ERROR")) {
			throw new RuntimeException(line);
		}
	}
	
	/**
	 * Used by subclasses. Parse the last line returned by the server to get execution time.
	 * 
	 * @param line the last line returned by the server
	 */
	protected void parseStatusLine(String line) {
		returnCode = "OK";
		String[] explodedLine = line.split("[ :]+");
		userTime = Double.parseDouble(explodedLine[2]);
		systemTime = Double.parseDouble(explodedLine[4]);
		if(logger.isTraceEnabled()) {
			logger.trace("Return code : " + returnCode);
			logger.trace("System time : " + systemTime);
			logger.trace("User time : " + userTime);
		}
	}
}
