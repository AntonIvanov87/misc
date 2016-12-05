import java.util.concurrent.SynchronousQueue;

class FullyBlockingQueue<E> extends SynchronousQueue<E> {
  @Override
  public boolean offer(E e) {
    try {
      super.put(e);
      return true;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(ie);
    }
  }
}
