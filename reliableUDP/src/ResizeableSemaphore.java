import java.util.concurrent.Semaphore;

public final class ResizeableSemaphore extends Semaphore {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * Create a new semaphore with 0 permits.
     */
    ResizeableSemaphore() {
        super(0);
    }

    @Override
    protected void reducePermits(int reduction) {
        int permits = super.availablePermits();
        super.reducePermits(permits);

        super.release(reduction);
    }
}