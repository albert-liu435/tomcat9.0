/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.net;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Error;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * Acceptor实现了Runnable接口，根据其命名就知道它是一个接收器，负责接收socket，其接收方法是serverSocket.accept()方式，
 * 获得SocketChannel对象，然后封装成tomcat自定义的org.apache.tomcat.util.net.NioChannel。
 * 虽然是Nio，但在接收socket时仍然使用传统的方法，使用阻塞方式实现
 * <p>
 * Acceptor线程主要用于监听套接字，将已连接套接字转给Poller线程。Acceptor线程数由AbstracEndPoint的acceptorThreadCount成员变量控制，
 * 默认值为1
 *
 * @param <U>
 */
public class Acceptor<U> implements Runnable {

    private static final Log log = LogFactory.getLog(Acceptor.class);
    private static final StringManager sm = StringManager.getManager(Acceptor.class);

    private static final int INITIAL_ERROR_DELAY = 50;
    private static final int MAX_ERROR_DELAY = 1600;

    private final AbstractEndpoint<?, U> endpoint;
    private String threadName;
    /*
     * Tracked separately rather than using endpoint.isRunning() as calls to
     * endpoint.stop() and endpoint.start() in quick succession can cause the
     * acceptor to continue running when it should terminate.
     */
    private volatile boolean stopCalled = false;//这是是停止的指令
    private final CountDownLatch stopLatch = new CountDownLatch(1);
    protected volatile AcceptorState state = AcceptorState.NEW;

    //AbstractProtocol.start();
    //在NioEndpoint.startInternal();AbstractEndpoint.startAcceptorThread()方法中启动
    public Acceptor(AbstractEndpoint<?, U> endpoint) {
        this.endpoint = endpoint;
    }


    public final AcceptorState getState() {
        return state;
    }


    final void setThreadName(final String threadName) {
        this.threadName = threadName;
    }


    final String getThreadName() {
        return threadName;
    }


    //这里用来接受外部的http请求的socket连接
    @Override
    public void run() {

        int errorDelay = 0;
        long pauseStart = 0;

        try {
            //循环执行除非收到停止指令
            // Loop until we receive a shutdown command
            while (!stopCalled) {

                // Loop if endpoint is paused.
                // There are two likely scenarios here.
                // The first scenario is that Tomcat is shutting down. In this
                // case - and particularly for the unit tests - we want to exit
                // this loop as quickly as possible. The second scenario is a
                // genuine pause of the connector. In this case we want to avoid
                // excessive CPU usage.
                // Therefore, we start with a tight loop but if there isn't a
                // rapid transition to stop then sleeps are introduced.
                // < 1ms       - tight loop
                // 1ms to 10ms - 1ms sleep
                // > 10ms      - 10ms sleep
                //// endpoint阻塞
                while (endpoint.isPaused() && !stopCalled) {
                    if (state != AcceptorState.PAUSED) {
                        pauseStart = System.nanoTime();
                        // Entered pause state
                        state = AcceptorState.PAUSED;
                    }
                    if ((System.nanoTime() - pauseStart) > 1_000_000) {
                        // Paused for more than 1ms
                        try {
                            if ((System.nanoTime() - pauseStart) > 10_000_000) {
                                Thread.sleep(10);
                            } else {
                                Thread.sleep(1);
                            }
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                    }
                }

                //如果`Endpoint`终止运行了，则`Acceptor`也会终止
                if (stopCalled) {
                    break;
                }
                state = AcceptorState.RUNNING;

                try {
                    //连接数到达最大值时，await等待释放connection，在Endpoint的startInterval方法中设置了最大连接数
                    //if we have reached max connections, wait
                    //如果请求达到了最大连接数，则wait直到连接数降下来
                    endpoint.countUpOrAwaitConnection();

                    // Endpoint might have been paused while waiting for latch
                    // If that is the case, don't accept new connections
                    if (endpoint.isPaused()) {
                        continue;
                    }
                    //U是一个socketChannel
                    U socket = null;
                    try {
                        // Accept the next incoming connection from the server
                        // socket
                        //接收socket请求
                        //接受下一次连接的socket
                        socket = endpoint.serverSocketAccept();
                    } catch (Exception ioe) {
                        // We didn't get a socket
                        endpoint.countDownConnection();
                        if (endpoint.isRunning()) {
                            // Introduce delay if necessary
                            errorDelay = handleExceptionWithDelay(errorDelay);
                            // re-throw
                            throw ioe;
                        } else {
                            break;
                        }
                    }
                    // Successful accept, reset the error delay
                    errorDelay = 0;

                    // Configure the socket
                    if (!stopCalled && !endpoint.isPaused()) {
                        // setSocketOptions() will hand the socket off to
                        // an appropriate processor if successful
                        // endpoint的setSocketOptions方法对socket进行配置
                        //Acceptor完成了socket请求的接收，然后交给NioEndpoint 进行配置，继续追踪Endpoint的setSocketOptions方法。
                        //setSocketOptions()`这儿是关键，会将socket以事件的方式传递给poller
                        if (!endpoint.setSocketOptions(socket)) {
                            endpoint.closeSocket(socket);
                        }
                    } else {
                        endpoint.destroySocket(socket);
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    String msg = sm.getString("endpoint.accept.fail");
                    // APR specific.
                    // Could push this down but not sure it is worth the trouble.
                    if (t instanceof Error) {
                        Error e = (Error) t;
                        if (e.getError() == 233) {
                            // Not an error on HP-UX so log as a warning
                            // so it can be filtered out on that platform
                            // See bug 50273
                            log.warn(msg, t);
                        } else {
                            log.error(msg, t);
                        }
                    } else {
                        log.error(msg, t);
                    }
                }
            }
        } finally {
            stopLatch.countDown();
        }
        state = AcceptorState.ENDED;
    }


    /**
     * Signals the Acceptor to stop, waiting at most 10 seconds for the stop to
     * complete before returning. If the stop does not complete in that time a
     * warning will be logged.
     *
     * @deprecated This method will be removed in Tomcat 10.1.x onwards.
     * Use {@link #stop(int)} instead.
     */
    @Deprecated
    public void stop() {
        stop(10);
    }


    /**停止指令
     * Signals the Acceptor to stop, optionally waiting for that stop process
     * to complete before returning. If a wait is requested and the stop does
     * not complete in that time a warning will be logged.
     *
     * @param waitSeconds The time to wait in seconds. Use a value less than
     *                    zero for no wait.
     */
    public void stop(int waitSeconds) {
        stopCalled = true;
        if (waitSeconds > 0) {
            try {
                if (!stopLatch.await(waitSeconds, TimeUnit.SECONDS)) {
                    log.warn(sm.getString("acceptor.stop.fail", getThreadName()));
                }
            } catch (InterruptedException e) {
                log.warn(sm.getString("acceptor.stop.interrupted", getThreadName()), e);
            }
        }
    }


    /**
     * Handles exceptions where a delay is required to prevent a Thread from
     * entering a tight loop which will consume CPU and may also trigger large
     * amounts of logging. For example, this can happen if the ulimit for open
     * files is reached.
     *
     * @param currentErrorDelay The current delay being applied on failure
     * @return The delay to apply on the next failure
     */
    protected int handleExceptionWithDelay(int currentErrorDelay) {
        // Don't delay on first exception
        if (currentErrorDelay > 0) {
            try {
                Thread.sleep(currentErrorDelay);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        // On subsequent exceptions, start the delay at 50ms, doubling the delay
        // on every subsequent exception until the delay reaches 1.6 seconds.
        if (currentErrorDelay == 0) {
            return INITIAL_ERROR_DELAY;
        } else if (currentErrorDelay < MAX_ERROR_DELAY) {
            return currentErrorDelay * 2;
        } else {
            return MAX_ERROR_DELAY;
        }
    }


    //Acceptor枚举值
    public enum AcceptorState {
        NEW, RUNNING, PAUSED, ENDED
    }
}
