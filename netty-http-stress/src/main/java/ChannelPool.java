import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

class ChannelPool {

  private final int maxNumOfAcquiredChannels;
  private final Semaphore acquiredChannelsSemaphore;
  private final Bootstrap bootstrap;
  private final SocketAddress socketAddress;
  private final Queue<Channel> idleChannels = new ConcurrentLinkedQueue<>();
  private final GenericFutureListener<Future<? super Void>> releaseOnCloseListener;

  ChannelPool(Bootstrap bootstrap, SocketAddress socketAddress, int maxNumOfAcquiredChannels) {
    this.maxNumOfAcquiredChannels = maxNumOfAcquiredChannels;
    this.acquiredChannelsSemaphore = new Semaphore(maxNumOfAcquiredChannels);
    this.releaseOnCloseListener = f -> acquiredChannelsSemaphore.release();
    this.bootstrap = bootstrap;
    this.socketAddress = socketAddress;
  }

  CompletableFuture<Channel> getChannel() {
    if (acquiredChannelsSemaphore.availablePermits() > maxNumOfAcquiredChannels) {
      throw new IllegalStateException("released more channels than acquired");
    }
    acquiredChannelsSemaphore.acquireUninterruptibly();

    while (true) {
      Channel channel = idleChannels.poll();
      if (channel == null) {
        CompletableFuture<Channel> channelFuture = new CompletableFuture<>();
        bootstrap.connect(socketAddress).addListener(new ConnectListener(channelFuture));
        return channelFuture;
      }

      if (channel.isActive()) {
        channel.closeFuture().addListener(releaseOnCloseListener);
        return CompletableFuture.completedFuture(channel);
      }

      if (channel.isOpen()) {
        channel.close();
      }
    }
  }

  void returnChannel(Channel channel) {
    if (channel.isActive()) {
      channel.closeFuture().removeListener(releaseOnCloseListener);
      idleChannels.add(channel);
      acquiredChannelsSemaphore.release();
    } else if (channel.isOpen()) {
      channel.close();
    }
  }

  class ConnectListener implements GenericFutureListener<ChannelFuture> {

    private final CompletableFuture<Channel> channelFuture;

    ConnectListener(CompletableFuture<Channel> channelFuture) {
      this.channelFuture = channelFuture;
    }

    @Override
    public void operationComplete(ChannelFuture channelFuture) throws Exception {
      if (channelFuture.cause() != null) {
        acquiredChannelsSemaphore.release();
        this.channelFuture.completeExceptionally(channelFuture.cause());
        return;
      }
      Channel channel = channelFuture.channel();
      channel.closeFuture().addListener(releaseOnCloseListener);
      this.channelFuture.complete(channel);
    }
  }
}
