import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

class SocketPool {

  private final InetSocketAddress serverAddress;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final Queue<Socket> sockets = new LinkedBlockingQueue<>();

  SocketPool(InetSocketAddress serverAddress, int connectTimeoutMs, int readTimeoutMs) {
    this.serverAddress = serverAddress;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
  }

  Socket get() throws IOException {
    while (true) {
      Socket socket = sockets.poll();
      if (socket == null) {
        return create();
      }
      if (socket.isClosed()) {
        continue;
      }
      if (socket.isInputShutdown() || socket.isOutputShutdown()) {
        socket.close();
        continue;
      }
      return socket;
    }
  }

  void putBack(Socket socket) {
    sockets.offer(socket);
  }

  private Socket create() throws IOException {
    Socket socket = new Socket();
    socket.setSoTimeout(readTimeoutMs);
    socket.connect(serverAddress, connectTimeoutMs);
    return socket;
  }

}
