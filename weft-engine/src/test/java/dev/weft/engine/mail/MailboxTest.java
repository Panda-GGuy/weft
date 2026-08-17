package dev.weft.engine.mail;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class MailboxTest {

    @Test
    void fifoPerSenderUnderConcurrency() throws Exception {
        Mailbox<int[]> box = new Mailbox<>(); // message = {senderId, seq}
        int senders = 8, perSender = 5_000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int s = 0; s < senders; s++) {
            final int senderId = s;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < perSender; i++) {
                    box.post(new int[]{senderId, i});
                }
            });
            t.start();
            threads.add(t);
        }
        start.countDown();
        for (Thread t : threads) {
            t.join();
        }

        // Drain (single consumer) and assert per-sender ordering.
        int[] nextExpected = new int[senders];
        int total = 0;
        for (int[] m : box.drain()) {
            assertEquals(nextExpected[m[0]], m[1],
                    "messages from sender " + m[0] + " must arrive in post order");
            nextExpected[m[0]]++;
            total++;
        }
        assertEquals(senders * perSender, total, "no message lost");
        assertTrue(box.isEmpty());
    }
}
