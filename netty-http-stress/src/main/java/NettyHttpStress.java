import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NettyHttpStress {

  public static void main(String[] args) throws InterruptedException {

    // http://127.0.0.1:8081/
    SocketAddress serverAddress = InetSocketAddress.createUnresolved("127.0.0.1", 8081);
    String pathAndQuery = "/";
    // -c
    int maxConcurrentRequests = 10;
    // --connect-timeout
    int connectTimeoutMs = 10;
    // -s
    int readTimeoutMs = 1_000;
    //
    int sleepBetweenRequestsMs = 1;
    int secsBetweenPrint = 2;
    // -H

    StressHttpClient stressHttpClient = new StressHttpClient(serverAddress, maxConcurrentRequests, connectTimeoutMs, readTimeoutMs);

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

      //Thread.sleep(sleepBetweenRequestsMs);
    }

  }
}
