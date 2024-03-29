/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.net;

import java.nio.ByteBuffer;

/**回调接口，以便在发生缓冲区溢出异常时扩展缓冲区或替换缓冲区
 * Callback interface to be able to expand buffers when buffer overflow
 * exceptions happen or to replace buffers
 */
public interface ApplicationBufferHandler {

    static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    static ApplicationBufferHandler EMPTY = new ApplicationBufferHandler() {
        @Override
        public void expand(int newSize) {
        }
        @Override
        public void setByteBuffer(ByteBuffer buffer) {
        }
        @Override
        public ByteBuffer getByteBuffer() {
            return EMPTY_BUFFER;
        }
    };

    public void setByteBuffer(ByteBuffer buffer);

    public ByteBuffer getByteBuffer();

    public void expand(int size);

}
