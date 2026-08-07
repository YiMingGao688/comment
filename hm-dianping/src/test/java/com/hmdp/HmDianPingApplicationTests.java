package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopServiceImpl;

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            try {
                for (int i = 0; i < 100; i++) {
                    long id = redisIdWorker.nextId("order");
                    System.out.println("id = " + id);
                }
            } finally {
                latch.countDown(); // 每个线程执行完毕，计数器-1，放finally保证异常也会执行
            }
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await(); // 等待300次countDown，计数器归0才往下走
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }
    @Test
    void contextLoads() {
        shopServiceImpl.saveShop2Redis(1L, 10L);
    }
}
