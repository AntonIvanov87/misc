import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SimpleHttpStress {

  public static void main(String[] args) throws InterruptedException {

    // http://127.0.0.1:8081/
    InetSocketAddress serverAddress = new InetSocketAddress(8081);
    String pathAndQuery = "/";
    // -c
    int maxConcurrentRequests = 1;
    // --connect-timeout
    int connectTimeoutMs = 100;
    // -s
    int readTimeoutMs = 1_000;
    //
    int sleepBetweenRequestsMs = 0;

    int secsBetweenPrint = 2;
    // -H

    SocketPool socketPool = new SocketPool(serverAddress, connectTimeoutMs, readTimeoutMs);
    SimpleHttpClient stressHttpClient = new SimpleHttpClient(socketPool, maxConcurrentRequests);

    StatsAccumulator statsAccumulator = new StatsAccumulator();
    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(
        () -> statsAccumulator.printStats(secsBetweenPrint),
        secsBetweenPrint, secsBetweenPrint, TimeUnit.SECONDS);

    while (true) {
      stressHttpClient.getStatus(pathAndQuery).whenComplete((status, ex) -> {
        if (ex != null) {
          Throwable rootEx = ex;
          while (rootEx.getCause() != null) {
            rootEx = rootEx.getCause();
          }
          statsAccumulator.addResult(rootEx.getClass().getSimpleName() + ": " + rootEx.getMessage());
          return;
        }
        statsAccumulator.addResult(status.toString());
      });

      if (sleepBetweenRequestsMs > 0) {
        Thread.sleep(sleepBetweenRequestsMs);
      }
    }

  }
}
