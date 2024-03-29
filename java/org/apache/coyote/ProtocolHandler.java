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
package org.apache.coyote;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.tomcat.util.net.SSLHostConfig;

/**
 * 协议处理器，针对不同的协议和I/O方式，提供了不同的实现
 * <p>
 * ProtocolHandler包含一个Endpoint用于启动Socket监听，该接口按照I/O方式进行分类实现，如Nio2Endpoint
 * 还包含一个Processor用于按照指定协议读取数据，并将请求交由容器处理。如Http11NioProcessor表示在NIO的方式下HTTP请求的处理类
 * Abstract the protocol implementation, including threading, etc.
 * <p>
 * This is the main interface to be implemented by a coyote protocol.
 * Adapter is the main interface to be implemented by a coyote servlet
 * container.
 * <p>
 * 每一个Connector对应了一个protocolHandler，一个protocolHandler被设计用来监听服务器某个端口的网络请求，但并不负责处理请求(处理请求由Container组件完成)
 * <p>
 * <p>
 * ajp和http11是两种不同的协议
 * nio、nio2和apr是不同的通信方式
 * 协议和通信方式可以相互组合
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 * @see Adapter
 */
public interface ProtocolHandler {

    /**
     * 返回协议处理器的相适应的适配器
     * Return the adapter associated with the protocol handler.
     *
     * @return the adapter
     */
    public Adapter getAdapter();


    /**
     * 设置适配器
     * The adapter, used to call the connector.
     *
     * @param adapter The adapter to associate
     */
    public void setAdapter(Adapter adapter);


    /**
     * 获取执行器
     * The executor, provide access to the underlying thread pool.
     *
     * @return The executor used to process requests
     */
    public Executor getExecutor();


    /**
     * 设置执行器
     * Set the optional executor that will be used by the connector.
     *
     * @param executor the executor
     */
    public void setExecutor(Executor executor);


    /**
     * 获取定时调度器
     * Get the utility executor that should be used by the protocol handler.
     *
     * @return the executor
     */
    public ScheduledExecutorService getUtilityExecutor();


    /**
     * Set the utility executor that should be used by the protocol handler.
     *
     * @param utilityExecutor the executor
     */
    public void setUtilityExecutor(ScheduledExecutorService utilityExecutor);


    /**
     * 初始化协议
     * Initialise the protocol.
     *
     * @throws Exception If the protocol handler fails to initialise
     */
    public void init() throws Exception;


    /**
     * 启动协议
     * Start the protocol.
     *
     * @throws Exception If the protocol handler fails to start
     */
    public void start() throws Exception;


    /**
     * 暂停协议
     * Pause the protocol (optional).
     *
     * @throws Exception If the protocol handler fails to pause
     */
    public void pause() throws Exception;


    /**
     * 恢复协议
     * Resume the protocol (optional).
     *
     * @throws Exception If the protocol handler fails to resume
     */
    public void resume() throws Exception;


    /**
     * 停止协议
     * Stop the protocol.
     *
     * @throws Exception If the protocol handler fails to stop
     */
    public void stop() throws Exception;


    /**
     * 销毁协议
     * Destroy the protocol (optional).
     *
     * @throws Exception If the protocol handler fails to destroy
     */
    public void destroy() throws Exception;


    /**
     * Close the server socket (to prevent further connections) if the server
     * socket was bound on {@link #start()} (rather than on {@link #init()}
     * but do not perform any further shutdown.
     */
    public void closeServerSocketGraceful();


    /**
     * Wait for the client connections to the server to close gracefully. The
     * method will return when all of the client connections have closed or the
     * method has been waiting for {@code waitTimeMillis}.
     *
     * @param waitMillis The maximum time to wait in milliseconds for the
     *                   client connections to close.
     * @return The wait time, if any remaining when the method returned
     */
    public long awaitConnectionsClose(long waitMillis);


    /**
     * Requires APR/native library
     *
     * @return <code>true</code> if this Protocol Handler requires the
     * APR/native library, otherwise <code>false</code>
     */
    public boolean isAprRequired();


    /**
     * Does this ProtocolHandler support sendfile?
     *
     * @return <code>true</code> if this Protocol Handler supports sendfile,
     * otherwise <code>false</code>
     */
    public boolean isSendfileSupported();


    /**
     * Add a new SSL configuration for a virtual host.
     *
     * @param sslHostConfig the configuration
     */
    public void addSslHostConfig(SSLHostConfig sslHostConfig);


    /**
     * Find all configured SSL virtual host configurations which will be used
     * by SNI.
     *
     * @return the configurations
     */
    public SSLHostConfig[] findSslHostConfigs();


    /**
     * Add a new protocol for used by HTTP/1.1 upgrade or ALPN.
     *
     * @param upgradeProtocol the protocol
     */
    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol);


    /**
     * Return all configured upgrade protocols.
     *
     * @return the protocols
     */
    public UpgradeProtocol[] findUpgradeProtocols();


    /**
     * Some protocols, like AJP, have a packet length that
     * shouldn't be exceeded, and this can be used to adjust the buffering
     * used by the application layer.
     *
     * @return the desired buffer size, or -1 if not relevant
     */
    public default int getDesiredBufferSize() {
        return -1;
    }


    /**
     * The default behavior is to identify connectors uniquely with address
     * and port. However, certain connectors are not using that and need
     * some other identifier, which then can be used as a replacement.
     *
     * @return the id
     */
    public default String getId() {
        return null;
    }


    /**
     * 根据给定的protocol创建ProtocolHandler
     * Create a new ProtocolHandler for the given protocol.
     *
     * @param protocol the protocol
     * @param apr      if <code>true</code> the APR protcol handler will be used
     * @return the newly instantiated protocol handler
     * @throws ClassNotFoundException    Specified protocol was not found
     * @throws InstantiationException    Specified protocol could not be instantiated
     * @throws IllegalAccessException    Exception occurred
     * @throws IllegalArgumentException  Exception occurred
     * @throws InvocationTargetException Exception occurred
     * @throws NoSuchMethodException     Exception occurred
     * @throws SecurityException         Exception occurred
     */
    public static ProtocolHandler create(String protocol, boolean apr)
        throws ClassNotFoundException, InstantiationException, IllegalAccessException,
        IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        if (protocol == null || "HTTP/1.1".equals(protocol)
            || (!apr && org.apache.coyote.http11.Http11NioProtocol.class.getName().equals(protocol))
            || (apr && org.apache.coyote.http11.Http11AprProtocol.class.getName().equals(protocol))) {
            if (apr) {
                return new org.apache.coyote.http11.Http11AprProtocol();
            } else {
                return new org.apache.coyote.http11.Http11NioProtocol();
            }
        } else if ("AJP/1.3".equals(protocol)
            || (!apr && org.apache.coyote.ajp.AjpNioProtocol.class.getName().equals(protocol))
            || (apr && org.apache.coyote.ajp.AjpAprProtocol.class.getName().equals(protocol))) {
            if (apr) {
                return new org.apache.coyote.ajp.AjpAprProtocol();
            } else {
                return new org.apache.coyote.ajp.AjpNioProtocol();
            }
        } else {
            // Instantiate protocol handler
            Class<?> clazz = Class.forName(protocol);
            return (ProtocolHandler) clazz.getConstructor().newInstance();
        }
    }


}
