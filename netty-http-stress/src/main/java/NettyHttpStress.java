import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NettyHttpStress {

  private static final SocketAddress serverAddress = InetSocketAddress.createUnresolved("127.0.0.1", 8081);
  private static final int connectTimeoutMs = 10;
  private static final int readTimeoutMs = 1_000;
  private static final int sleepBetweenRequestsMs = 1;
  private static final int maxConcurrentRequests = 4;
  private static final int secsBetweenPrint = 2;

  private static final Bootstrap bootstrap = new Bootstrap();
  private static final ChannelPool channelPool = new ChannelPool(bootstrap, serverAddress, maxConcurrentRequests);
  private static final StatsAccumulator statsAccumulator = new StatsAccumulator();


  public static void main(String[] args) throws InterruptedException {

    bootstrap.group(new EpollEventLoopGroup());
    bootstrap.channel(EpollSocketChannel.class);
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs);
    bootstrap.option(ChannelOption.SO_KEEPALIVE, true);

    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(
        () -> statsAccumulator.printStats(secsBetweenPrint),
        secsBetweenPrint, secsBetweenPrint, TimeUnit.SECONDS);

    bootstrap.handler(new ChannelInitializer<SocketChannel>() {
      protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline channelPipeline = ch.pipeline();
        channelPipeline.addLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS));
        channelPipeline.addLast(new HttpClientCodec());
        channelPipeline.addLast(new HttpClientHandler());
      }
    });

    while (true) {
      sendRequest();
      //Thread.sleep(sleepBetweenRequestsMs);
    }

    //eventLoopGroup.shutdownGracefully();
  }

  private static void sendRequest() throws InterruptedException {
    channelPool.getChannel().whenComplete((ch, ex) -> {
      if (ex != null) {
        statsAccumulator.addResult(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        return;
      }
      sendRequestToChannel(ch);
    });
  }

  private static void sendRequestToChannel(Channel channel) {
    HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", false);
    request.headers().set(HttpHeaderNames.HOST, "127.0.0.1");
    //request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
    channel.writeAndFlush(request);
  }

  static class HttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
      if (msg instanceof HttpResponse) {
        HttpResponse response = (HttpResponse) msg;
        // inaccurately: we can getChannel exception while receiving a body
        statsAccumulator.addResult(Integer.toString(response.status().code()));
      }

      if (msg instanceof LastHttpContent) {
        channelPool.returnChannel(ctx.channel());
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable originalCause) throws Exception {
      Throwable cause = originalCause;
      while (cause.getCause() != null) {
        cause = cause.getCause();
      }
      statsAccumulator.addResult(cause.getClass().getSimpleName() + ": " + cause.getMessage());
      ctx.close();
    }
  }
}
