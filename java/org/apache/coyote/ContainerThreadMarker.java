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

/**
 * 用于标记当前线程正在处理数据
 * Used to mark threads that have been allocated by the container to process
 * data from an incoming connection. Application created threads are not
 * container threads and neither are threads taken from the container thread
 * pool to execute AsyncContext.start(Runnable).
 */
public class ContainerThreadMarker {

    public static boolean isContainerThread() {
        return org.apache.tomcat.util.net.ContainerThreadMarker.isContainerThread();
    }

    public static void set() {
        org.apache.tomcat.util.net.ContainerThreadMarker.set();
    }

    public static void clear() {
        org.apache.tomcat.util.net.ContainerThreadMarker.clear();
    }
}
