import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NettyHttpStress {

  private static final SocketAddress serverAddress = InetSocketAddress.createUnresolved("127.0.0.1", 8080);
  private static final int maxConcurrentRequests = 1_000;
  private static final int connectTimeoutMs = 10;
  private static final int readTimeoutMs = 1_010;
  private static final int sleepBetweenRequestsMs = 1;
  private static final int secsBetweenPrint = 2;


  public static void main(String[] args) throws InterruptedException {

    StressHttpClient stressHttpClient = new StressHttpClient(serverAddress, maxConcurrentRequests, connectTimeoutMs, readTimeoutMs);

    StatsAccumulator statsAccumulator = new StatsAccumulator();
    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(
        () -> statsAccumulator.printStats(secsBetweenPrint),
        secsBetweenPrint, secsBetweenPrint, TimeUnit.SECONDS);

    while (true) {
      stressHttpClient.getStatus("/").whenComplete((status, ex) -> {
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

      Thread.sleep(sleepBetweenRequestsMs);
    }

  }
}
