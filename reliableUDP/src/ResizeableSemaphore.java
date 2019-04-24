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

    protected void changePermits(int change) {

        int permits = super.availablePermits();
        super.reducePermits(permits);

        super.release(change);
    }
}