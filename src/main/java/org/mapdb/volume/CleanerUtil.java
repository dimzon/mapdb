/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mapdb.volume;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

/**
 * sun.misc.Cleaner has moved in OpenJDK 9 and
 * sun.misc.Unsafe#invokeCleaner(ByteBuffer) is the replacement.
 * Android compatible implementation: plain reflection only, no MethodHandles.
 * This implementation is based on Hadoop class
 * https://github.com/apache/hadoop/blob/5d084d7eca32cfa647a78ff6ed3c378659f5b186/hadoop-common-project/hadoop-common/src/main/java/org/apache/hadoop/util/CleanerUtil.java
 * Some adaptations have been done to handle the attachment() of the byte buffer (explained in Bug #776)
 */
public final class CleanerUtil {

    // Prevent instantiation
    private CleanerUtil(){}

    /**
     * <code>true</code>, if this platform supports unmapping mmapped files.
     */
    public static final boolean UNMAP_SUPPORTED;

    /**
     * if {@link #UNMAP_SUPPORTED} is {@code false}, this contains the reason
     * why unmapping is not supported.
     */
    public static final String UNMAP_NOT_SUPPORTED_REASON;

    public static boolean JAVA8_OR_LESS;

    private static final Method INVOKE_CLEANER;
    private static final Object THE_UNSAFE;
    private static final Method BUFFER_CLEANER;
    private static final Method CLEANER_CLEAN;
    private static final Method BUFFER_ATTACHMENT;

    static {
        Object[] hack = unmapHackImpl();

        if (hack[0] != null) {
            INVOKE_CLEANER = (Method) hack[0];
            THE_UNSAFE = hack[1];
            BUFFER_CLEANER = null;
            CLEANER_CLEAN = null;
            BUFFER_ATTACHMENT = null;
            UNMAP_SUPPORTED = true;
            UNMAP_NOT_SUPPORTED_REASON = null;
        } else if (hack[2] != null) {
            INVOKE_CLEANER = null;
            THE_UNSAFE = null;
            BUFFER_CLEANER = (Method) hack[2];
            CLEANER_CLEAN = (Method) hack[3];
            BUFFER_ATTACHMENT = (Method) hack[4];
            JAVA8_OR_LESS = true;
            UNMAP_SUPPORTED = true;
            UNMAP_NOT_SUPPORTED_REASON = null;
        } else {
            INVOKE_CLEANER = null;
            THE_UNSAFE = null;
            BUFFER_CLEANER = null;
            CLEANER_CLEAN = null;
            BUFFER_ATTACHMENT = null;
            UNMAP_SUPPORTED = false;
            UNMAP_NOT_SUPPORTED_REASON = (String) hack[5];
        }
    }

    private static Object[] unmapHackImpl() {
        Object[] result = new Object[6];

        try {
            try {
                // *** sun.misc.Unsafe unmapping (Java 9+) ***
                final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                final Method invokeCleaner = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
                final Field f = unsafeClass.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                final Object theUnsafe = f.get(null);
                result[0] = invokeCleaner;
                result[1] = theUnsafe;
                return result;
            } catch (SecurityException se) {
                throw se;
            } catch (ReflectiveOperationException | RuntimeException e) {
                // *** sun.misc.Cleaner unmapping (Java 8 / Android) ***
                final Class<?> directBufferClass = Class.forName("java.nio.DirectByteBuffer");
                final Method cleanerMethod = directBufferClass.getMethod("cleaner");
                cleanerMethod.setAccessible(true);
                final Class<?> cleanerClass = cleanerMethod.getReturnType();
                final Method cleanMethod = cleanerClass.getMethod("clean");
                final Method attachmentMethod = directBufferClass.getMethod("attachment");
                attachmentMethod.setAccessible(true);
                result[2] = cleanerMethod;
                result[3] = cleanMethod;
                result[4] = attachmentMethod;
                return result;
            }
        } catch (SecurityException se) {
            result[5] = "Unmapping is not supported, because not all required " +
                    "permissions are given to the MapDB JAR file: " + se +
                    " [Please grant at least the following permissions: " +
                    "RuntimePermission(\"accessClassInPackage.sun.misc\") " +
                    " and ReflectPermission(\"suppressAccessChecks\")]";
            return result;
        } catch (ReflectiveOperationException | RuntimeException e) {
            result[5] = "Unmapping is not supported on this platform, " +
                    "because internal Java APIs are not compatible with " +
                    "this Hadoop version: " + e;
            return result;
        }
    }

    public static boolean freeBuffer (ByteBuffer buffer) throws IOException {
            if (!buffer.isDirect() && UNMAP_SUPPORTED) {
                throw new IllegalArgumentException(
                        "unmapping only works with direct buffers");
            }

            try {
                if (INVOKE_CLEANER != null) {
                    INVOKE_CLEANER.invoke(THE_UNSAFE, buffer);
                } else if (BUFFER_CLEANER != null) {
                    Object cleaner = BUFFER_CLEANER.invoke(buffer);

                    if (cleaner != null) {
                        CLEANER_CLEAN.invoke(cleaner);
                    }

                    /*
                    * Handling attachment of the ByteBuffer object
                    * Refer to https://github.com/jankotek/mapdb/issues/776 for details
                    * */
                    Object attachment = BUFFER_ATTACHMENT.invoke(buffer);

                    if (attachment != null) {
                        return freeBuffer((MappedByteBuffer) attachment);
                    }
                } else {
                    throw new IOException("unmapping is not supported on this platform: " + UNMAP_NOT_SUPPORTED_REASON);
                }

                return true;
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new IOException(cause);
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            } catch (Throwable e) {
                throw new IOException(e);
            }

    }
}
