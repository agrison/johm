package redis.clients.johm;

import static redis.clients.johm.ResourceManager.extract;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;

import org.apache.commons.lang.SystemUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class JOhmTestBase extends Assert {
    protected JedisPool jedisPool;
    protected volatile static boolean benchmarkMode;
    protected static int listenPort;

    private static Boolean redisIsRunning = false;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        if (!redisIsRunning) {
            final String redisServerPath;
            if (SystemUtils.IS_OS_LINUX) {
                redisServerPath = extract("/server/linux/redis-server");
            } else if (SystemUtils.IS_OS_WINDOWS) {
                redisServerPath = extract("/server/win64/redis-server.exe");
            } else if (SystemUtils.IS_OS_MAC_OSX) {
                redisServerPath = extract("/server/osx/redis-server");
            } else {
                throw new RuntimeException("Can't launch redis server for unit tests.");
            }

            listenPort = findFreePort();
            String command = redisServerPath + " --port " + listenPort;
            // only set maxheap for windows
            if(SystemUtils.IS_OS_WINDOWS) {
                command = command + " --maxheap 10mb"; 
            }
            final Process redisServerProcess = 
                    Runtime.getRuntime().exec(command);
            final StreamGobbler errorGobbler = new
                    StreamGobbler(redisServerProcess.getErrorStream(), "ERROR");
            final StreamGobbler outputGobbler = new
                    StreamGobbler(redisServerProcess.getInputStream(), "OUTPUT");

            errorGobbler.start();
            outputGobbler.start();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    redisServerProcess.destroy();
                    try {
                        redisServerProcess.waitFor();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    try {
                        errorGobbler.join();
                        outputGobbler.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    File file = new File(redisServerPath);
                    file.delete();
                }
            });

            redisIsRunning = true;
        }
    }

    @Before
    public void startUp() {
        startJedisEngine();
    }

    protected void startJedisEngine() {
        if (benchmarkMode) {
            jedisPool = new JedisPool(new GenericObjectPoolConfig(), "localhost",
                    listenPort, 2000);
        } else {
            jedisPool = new JedisPool(new GenericObjectPoolConfig(), "localhost", listenPort);
        }
        JOhm.setPool(jedisPool);
        purgeRedis();
    }

    protected void purgeRedis() {
        Jedis jedis = jedisPool.getResource();
        jedis.flushAll();
        jedisPool.returnResource(jedis);
    }
    
    /**
     * Returns a free port number on localhost.
     * 
     * Heavily inspired from org.eclipse.jdt.launching.SocketUtil (to avoid a dependency to JDT just because of this).
     * Slightly improved with close() missing in JDT. And throws exception instead of returning -1.
     * 
     * Taken from: https://gist.github.com/vorburger/3429822
     * 
     * @return a free port number on localhost
     * @throws IllegalStateException if unable to find a free port
     */
    private static int findFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore IOException on close()
            }
            return port;
        } catch (IOException e) { 
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
        throw new IllegalStateException("Could not find a free TCP/IP port to start embedded Jetty HTTP Server on");
    }
    
    
}