import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
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

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class StressHttpClient {

  private final ChannelPool channelPool;

  StressHttpClient(SocketAddress serverAddress, int maxConcurrentRequests, int connectTimeoutMs, int readTimeoutMs) {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(new EpollEventLoopGroup());
    bootstrap.channel(EpollSocketChannel.class);
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs);
    bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
    bootstrap.handler(new ChannelInitializer<SocketChannel>() {
      protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline channelPipeline = ch.pipeline();
        channelPipeline.addLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS));
        channelPipeline.addLast(new HttpClientCodec());
        channelPipeline.addLast(new HttpClientHandler(channelPool));
      }
    });

    channelPool = new ChannelPool(bootstrap, serverAddress, maxConcurrentRequests);
  }

  CompletableFuture<Integer> getStatus(String pathAndQuery) {
    CompletableFuture<Integer> statusFuture = new CompletableFuture<>();
    channelPool.getChannel().addListener((ChannelFuture channelFuture) -> {
      if (channelFuture.cause() != null) {
        statusFuture.completeExceptionally(channelFuture.cause());
        return;
      }

      // TODO: sometimes NullPointerException
      channelFuture.channel().pipeline().get(HttpClientHandler.class).statusFuture = statusFuture;

      HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, pathAndQuery, false);
      //TODO:
      request.headers().set(HttpHeaderNames.HOST, "127.0.0.1");
      channelFuture.channel().writeAndFlush(request);
    });
    return statusFuture;
  }

  static class HttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    // TODO: do not pass channelPool, just close channel
    private final ChannelPool channelPool;

    private CompletableFuture<Integer> statusFuture;
    private int status;

    HttpClientHandler(ChannelPool channelPool) {
      this.channelPool = channelPool;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
      if (msg instanceof HttpResponse) {
        HttpResponse response = (HttpResponse) msg;
        status = response.status().code();
      }

      if (msg instanceof LastHttpContent) {
        statusFuture.complete(status);
        status = 0;
        statusFuture = null;
        channelPool.returnChannel(ctx.channel());
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
      if (statusFuture != null) {
        statusFuture.completeExceptionally(cause);
      }
      ctx.close();
    }

  }

}
