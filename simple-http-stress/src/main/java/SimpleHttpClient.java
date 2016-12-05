import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class SimpleHttpClient {

  private final SocketPool socketPool;
  private final Executor executor;

  SimpleHttpClient(SocketPool socketPool, int maxConcurrentRequests) {
    this.socketPool = socketPool;
    executor = new ThreadPoolExecutor(maxConcurrentRequests, maxConcurrentRequests,
                                         0L, TimeUnit.MILLISECONDS,
                                         new FullyBlockingQueue<>());
  }

  CompletionStage<Integer> getStatus(String pathAndQuery) {
    CompletableFuture<Integer> statusFuture = new CompletableFuture<>();
    StatusTask statusTask = new StatusTask(pathAndQuery, statusFuture, socketPool);
    executor.execute(statusTask);
    return statusFuture;
  }

}
