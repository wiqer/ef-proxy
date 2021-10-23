package com.wiqer.proxy.utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Administrator
 */
public class IdUtils {

    private static  final int   RANDOM_MASK =0x7fffffff;

    private static  final int   ADDER_MASK =31;
    /**
     * Start time intercept (2021-09-17 20:22)
     */
    public static final long EPOCH = 1631881473317L;

    private static int ADDER = 0;

    private static int ADDER_LEFT_SHIFT_BITS = 16;

    private static int TIMESTAMP_LEFT_SHIFT_BITS = 23;

    /**
     * 正常是10
     */
    private static int RANDOM_RIGHT_SHIFT_BITS = 15;



    public static long randomId() {
        return   ThreadLocalRandom.current().nextInt()&RANDOM_MASK
                | ((long) (ThreadLocalRandom.current().nextInt() & RANDOM_MASK) <<31);
    }

    /**
     * 相对的雪花算法
     *  |TIME 43 bit | ADDER 5bit  | RANDOM 16 bit|
     * @return
     */
    public static long randomSnowFlowerId() {
        return   (System.currentTimeMillis()-EPOCH)<<TIMESTAMP_LEFT_SHIFT_BITS
                |( (ADDER++&ADDER_MASK)<<ADDER_LEFT_SHIFT_BITS)
                |( (ThreadLocalRandom.current().nextInt() & RANDOM_MASK)>>RANDOM_RIGHT_SHIFT_BITS);
    }
    public static long randomSnowFlowerId(Long currentTimeMillis) {
        return   (currentTimeMillis-EPOCH)<<TIMESTAMP_LEFT_SHIFT_BITS
                |( (ADDER++&ADDER_MASK)<<ADDER_LEFT_SHIFT_BITS)
                |( (ThreadLocalRandom.current().nextInt() & RANDOM_MASK)>>RANDOM_RIGHT_SHIFT_BITS);
    }

}
