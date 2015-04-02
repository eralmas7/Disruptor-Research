import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

public class Simple {

    public static class MyThread extends Thread {

        RingBuffer<ValueEvent> ringBuffer;

        public MyThread(RingBuffer<ValueEvent> ringBuffer) {
            this.ringBuffer = ringBuffer;
        }

        public void run() {
            for (long i = 10; i < 2000; i++) {
                String uuid = UUID.randomUUID().toString();
                // Two phase commit. Grab one of the 1024 slots
                long seq = ringBuffer.next();
                System.out.println("Will publish sequence " + seq + " thread name " + this.getName());
                ValueEvent valueEvent = ringBuffer.get(seq);
                valueEvent.setValue(uuid);
                ringBuffer.publish(seq);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        ExecutorService exec = Executors.newCachedThreadPool();
        // Preallocate RingBuffer with 1024 ValueEvents
        Disruptor<ValueEvent> disruptor = new Disruptor<ValueEvent>(ValueEvent.EVENT_FACTORY, 1, exec, ProducerType.MULTI, new BusySpinWaitStrategy());
        final EventHandler<ValueEvent> handler1 = new EventHandler<ValueEvent>() {

            // event will eventually be recycled by the Disruptor after it wraps
            public void onEvent(final ValueEvent event, final long sequence, final boolean endOfBatch) throws Exception {
                System.out.println("Sequence1: " + sequence);
            }
        };
        final EventHandler<ValueEvent> handler2 = new EventHandler<ValueEvent>() {

            // event will eventually be recycled by the Disruptor after it wraps
            public void onEvent(final ValueEvent event, final long sequence, final boolean endOfBatch) throws Exception {
                Thread.sleep(9999000);
                System.out.println("Sequence2: " + sequence);
            }
        };
        disruptor.handleEventsWith(handler1, handler2);
        RingBuffer<ValueEvent> ringBuffer = disruptor.start();
        MyThread thread = new MyThread(ringBuffer);
        thread.start();
        thread = new MyThread(ringBuffer);
        thread.start();
        disruptor.shutdown();
        exec.shutdown();
    }
}
