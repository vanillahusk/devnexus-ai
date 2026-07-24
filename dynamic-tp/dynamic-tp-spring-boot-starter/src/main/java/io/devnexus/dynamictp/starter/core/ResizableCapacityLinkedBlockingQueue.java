package io.devnexus.dynamictp.starter.core;

import java.io.Serializable;
import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ResizableCapacityLinkedBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, Serializable {

    private static final long serialVersionUID = 1L;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private final ArrayDeque<E> deque = new ArrayDeque<E>();

    private volatile int capacity;

    public ResizableCapacityLinkedBlockingQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public void setCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        lock.lock();
        try {
            this.capacity = capacity;
            if (deque.size() < capacity) {
                notFull.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return deque.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int remainingCapacity() {
        lock.lock();
        try {
            return Math.max(capacity - deque.size(), 0);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(E element) throws InterruptedException {
        if (element == null) {
            throw new NullPointerException();
        }
        lock.lockInterruptibly();
        try {
            while (deque.size() >= capacity) {
                notFull.await();
            }
            enqueue(element);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean offer(E element, long timeout, TimeUnit unit) throws InterruptedException {
        if (element == null) {
            throw new NullPointerException();
        }
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (deque.size() >= capacity) {
                if (nanos <= 0L) {
                    return false;
                }
                nanos = notFull.awaitNanos(nanos);
            }
            enqueue(element);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean offer(E element) {
        if (element == null) {
            throw new NullPointerException();
        }
        lock.lock();
        try {
            if (deque.size() >= capacity) {
                return false;
            }
            enqueue(element);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (deque.isEmpty()) {
                notEmpty.await();
            }
            return dequeue();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (deque.isEmpty()) {
                if (nanos <= 0L) {
                    return null;
                }
                nanos = notEmpty.awaitNanos(nanos);
            }
            return dequeue();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E poll() {
        lock.lock();
        try {
            return deque.isEmpty() ? null : dequeue();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E peek() {
        lock.lock();
        try {
            return deque.peekFirst();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Iterator<E> iterator() {
        lock.lock();
        try {
            return new ArrayDeque<E>(deque).iterator();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int drainTo(Collection<? super E> collection) {
        return drainTo(collection, Integer.MAX_VALUE);
    }

    @Override
    public int drainTo(Collection<? super E> collection, int maxElements) {
        if (collection == null) {
            throw new NullPointerException();
        }
        if (collection == this) {
            throw new IllegalArgumentException();
        }
        if (maxElements <= 0) {
            return 0;
        }
        lock.lock();
        try {
            int transferred = 0;
            while (transferred < maxElements && !deque.isEmpty()) {
                collection.add(deque.removeFirst());
                transferred++;
            }
            if (transferred > 0) {
                notFull.signalAll();
            }
            return transferred;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(Object object) {
        lock.lock();
        try {
            boolean removed = deque.remove(object);
            if (removed) {
                notFull.signal();
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean contains(Object object) {
        lock.lock();
        try {
            return deque.contains(object);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Object[] toArray() {
        lock.lock();
        try {
            return deque.toArray();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T> T[] toArray(T[] array) {
        lock.lock();
        try {
            return deque.toArray(array);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            if (deque.isEmpty()) {
                return;
            }
            deque.clear();
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E remove() {
        E element = poll();
        if (element == null) {
            throw new NoSuchElementException();
        }
        return element;
    }

    private void enqueue(E element) {
        deque.addLast(element);
        notEmpty.signal();
    }

    private E dequeue() {
        E element = deque.removeFirst();
        notFull.signal();
        return element;
    }
}