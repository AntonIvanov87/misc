import org.eclipse.jetty.io.SelectorManager;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;


public class JettyTest {

  public static void main(String[] args) throws Exception {
    QueuedThreadPool serverPool = new QueuedThreadPool(5);
    Server server = new Server(serverPool);

    ServerConnector connector = new FailFastServerConnector(server, 1, 1);
    connector.setPort(8080);
    server.addConnector(connector);

    MBeanContainer mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
    server.addBean(mbContainer);

    server.setHandler(new MyHandler());

    server.start();
  }

  public static class FailFastServerConnector extends ServerConnector {

    private static final Logger logger = LoggerFactory.getLogger(FailFastServerConnector.class);

    // TODO: implement all ServerConnector constructors
    public FailFastServerConnector(@Name("server") Server server,
                                   @Name("acceptors") int acceptors,
                                   @Name("selectors") int selectors) {
      super(server, acceptors, selectors);
    }

    @Override
    protected SelectorManager newSelectorManager(Executor executor, Scheduler scheduler, int selectors) {
      return new FailFastServerConnectorManager(executor, scheduler, selectors);
    }

    private class FailFastServerConnectorManager extends ServerConnectorManager {

      FailFastServerConnectorManager(Executor executor, Scheduler scheduler, int selectors) {
        super(executor, scheduler, selectors);
      }

      @Override
      public void accept(SocketChannel channel) {
        Executor executor = getExecutor();
        if (executor instanceof QueuedThreadPool) {
          QueuedThreadPool queuedThreadPool = (QueuedThreadPool) executor;
          if (queuedThreadPool.isLowOnThreads()) {
            logger.warn("low on threads, closing accepted socket");
            try {
              channel.close();
            } catch (IOException e) {
              logger.warn("failed to close socket, leaving socket as is", e);
            }
            return;
          }
        }
        super.accept(channel);
      }
    }
  }

  static class MyHandler extends AbstractHandler {
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      try {
        Thread.sleep(100L);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      response.setContentType("text/plain; charset=utf-8");
      response.getWriter().write("Hello, World!");
      baseRequest.setHandled(true);
    }
  }

}
