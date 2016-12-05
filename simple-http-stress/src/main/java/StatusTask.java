import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

class StatusTask implements Runnable {

  private final String pathAndQuery;
  private final CompletableFuture<Integer> statusFuture;
  private final SocketPool socketPool;

  StatusTask(String pathAndQuery, CompletableFuture<Integer> statusFuture, SocketPool socketPool) {
    this.pathAndQuery = pathAndQuery;
    this.statusFuture = statusFuture;
    this.socketPool = socketPool;
  }

  @Override
  public void run() {
    Socket socket;
    try {
      socket = socketPool.get();
    } catch (IOException e) {
      statusFuture.completeExceptionally(e);
      return;
    }

    OutputStream outputStream;
    try {
      outputStream = socket.getOutputStream();
    } catch (IOException e) {
      closeNoException(socket);
      statusFuture.completeExceptionally(e);
      return;
    }

    PrintWriter printWriter = new PrintWriter(outputStream, false);
    printWriter.println("GET " + pathAndQuery + " HTTP/1.1");
    // TODO: normal host
    printWriter.println("Host: localhost");
    printWriter.println("User-Agent: simple-http-client");
    printWriter.println();
    if (printWriter.checkError()) {
      closeNoException(socket);
      statusFuture.completeExceptionally(new RuntimeException("failed to write request"));
      return;
    }

    InputStream inputStream;
    try {
      inputStream = socket.getInputStream();
    } catch (IOException e) {
      closeNoException(socket);
      statusFuture.completeExceptionally(e);
      return;
    }
    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
    String firstLine;
    try {
      firstLine = bufferedReader.readLine();
    } catch (IOException e) {
      closeNoException(socket);
      statusFuture.completeExceptionally(e);
      return;
    }

    if (firstLine == null) {
      closeNoException(socket);
      statusFuture.completeExceptionally(new RuntimeException("end of the input stream has been reached"));
      return;
    }

    String[] firstLineParts = firstLine.split(" ");
    int status;
    try {
      status = Integer.parseInt(firstLineParts[1]);
    } catch (NumberFormatException e) {
      closeNoException(socket);
      statusFuture.completeExceptionally(e);
      return;
    }

    // TODO: skip headers
    //try {
    // skip headers
    //while (true) {
    //  String line = bufferedReader.readLine();
    //  if (line.isEmpty()) {
    //    break;
    //  }
    //}
    // TODO: skip body
    //} catch (IOException e) {
    //  statusFuture.completeExceptionally(e);
    //  return statusFuture;
    //}
    socketPool.putBack(socket);
    statusFuture.complete(status);
  }

  private static void closeNoException(Socket socket) {
    try {
      socket.close();
    } catch (IOException e) {
    }
  }
}
