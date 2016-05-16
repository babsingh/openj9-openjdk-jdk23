/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8143628
 * @summary Test unsafe access for Object
 * @modules java.base/jdk.internal.misc
 * @run testng/othervm -Diters=100   -Xint                   JdkInternalMiscUnsafeAccessTestObject
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 JdkInternalMiscUnsafeAccessTestObject
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  JdkInternalMiscUnsafeAccessTestObject
 * @run testng/othervm -Diters=20000                         JdkInternalMiscUnsafeAccessTestObject
 */

import org.testng.annotations.Test;

import java.lang.reflect.Field;

import static org.testng.Assert.*;

public class JdkInternalMiscUnsafeAccessTestObject {
    static final int ITERS = Integer.getInteger("iters", 1);

    static final jdk.internal.misc.Unsafe UNSAFE;

    static final long V_OFFSET;

    static final Object STATIC_V_BASE;

    static final long STATIC_V_OFFSET;

    static int ARRAY_OFFSET;

    static int ARRAY_SHIFT;

    static {
        try {
            Field f = jdk.internal.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (jdk.internal.misc.Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Unable to get Unsafe instance.", e);
        }

        try {
            Field staticVField = JdkInternalMiscUnsafeAccessTestObject.class.getDeclaredField("static_v");
            STATIC_V_BASE = UNSAFE.staticFieldBase(staticVField);
            STATIC_V_OFFSET = UNSAFE.staticFieldOffset(staticVField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            Field vField = JdkInternalMiscUnsafeAccessTestObject.class.getDeclaredField("v");
            V_OFFSET = UNSAFE.objectFieldOffset(vField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ARRAY_OFFSET = UNSAFE.arrayBaseOffset(Object[].class);
        int ascale = UNSAFE.arrayIndexScale(Object[].class);
        ARRAY_SHIFT = 31 - Integer.numberOfLeadingZeros(ascale);
    }

    static Object static_v;

    Object v;

    @Test
    public void testFieldInstance() {
        JdkInternalMiscUnsafeAccessTestObject t = new JdkInternalMiscUnsafeAccessTestObject();
        for (int c = 0; c < ITERS; c++) {
            testAccess(t, V_OFFSET);
        }
    }

    @Test
    public void testFieldStatic() {
        for (int c = 0; c < ITERS; c++) {
            testAccess(STATIC_V_BASE, STATIC_V_OFFSET);
        }
    }

    @Test
    public void testArray() {
        Object[] array = new Object[10];
        for (int c = 0; c < ITERS; c++) {
            for (int i = 0; i < array.length; i++) {
                testAccess(array, (((long) i) << ARRAY_SHIFT) + ARRAY_OFFSET);
            }
        }
    }


    static void testAccess(Object base, long offset) {
        // Plain
        {
            UNSAFE.putObject(base, offset, "foo");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "foo", "set Object value");
        }

        // Volatile
        {
            UNSAFE.putObjectVolatile(base, offset, "bar");
            Object x = UNSAFE.getObjectVolatile(base, offset);
            assertEquals(x, "bar", "putVolatile Object value");
        }


        // Lazy
        {
            UNSAFE.putObjectRelease(base, offset, "foo");
            Object x = UNSAFE.getObjectAcquire(base, offset);
            assertEquals(x, "foo", "putRelease Object value");
        }

        // Opaque
        {
            UNSAFE.putObjectOpaque(base, offset, "bar");
            Object x = UNSAFE.getObjectOpaque(base, offset);
            assertEquals(x, "bar", "putOpaque Object value");
        }


        UNSAFE.putObject(base, offset, "foo");

        // Compare
        {
            boolean r = UNSAFE.compareAndSwapObject(base, offset, "foo", "bar");
            assertEquals(r, true, "success compareAndSwap Object");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "bar", "success compareAndSwap Object value");
        }

        {
            boolean r = UNSAFE.compareAndSwapObject(base, offset, "foo", "baz");
            assertEquals(r, false, "failing compareAndSwap Object");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "bar", "failing compareAndSwap Object value");
        }

        // Advanced compare
        {
            Object r = UNSAFE.compareAndExchangeObjectVolatile(base, offset, "bar", "foo");
            assertEquals(r, "bar", "success compareAndExchangeVolatile Object");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "foo", "success compareAndExchangeVolatile Object value");
        }

        {
            Object r = UNSAFE.compareAndExchangeObjectVolatile(base, offset, "bar", "baz");
            assertEquals(r, "foo", "failing compareAndExchangeVolatile Object");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "foo", "failing compareAndExchangeVolatile Object value");
        }

        {
            Object r = UNSAFE.compareAndExchangeObjectAcquire(base, offset, "foo", "bar");
            assertEquals(r, "foo", "success compareAndExchangeAcquire Object");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "bar", "success compareAndExchangeAcquire Object value");
        }

        {
            Object r = UNSAFE.compareAndExchangeObjectAcquire(base, offset, "foo", "baz");
            assertEquals(r, "bar", "failing compareAndExchangeAcquire Object");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "bar", "failing compareAndExchangeAcquire Object value");
        }

        {
            Object r = UNSAFE.compareAndExchangeObjectRelease(base, offset, "bar", "foo");
            assertEquals(r, "bar", "success compareAndExchangeRelease Object");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "foo", "success compareAndExchangeRelease Object value");
        }

        {
            Object r = UNSAFE.compareAndExchangeObjectRelease(base, offset, "bar", "baz");
            assertEquals(r, "foo", "failing compareAndExchangeRelease Object");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "foo", "failing compareAndExchangeRelease Object value");
        }

        {
            boolean r = UNSAFE.weakCompareAndSwapObject(base, offset, "foo", "bar");
            assertEquals(r, true, "weakCompareAndSwap Object");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "bar", "weakCompareAndSwap Object value");
        }

        {
            boolean r = UNSAFE.weakCompareAndSwapObjectAcquire(base, offset, "bar", "foo");
            assertEquals(r, true, "weakCompareAndSwapAcquire Object");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "foo", "weakCompareAndSwapAcquire Object");
        }

        {
            boolean r = UNSAFE.weakCompareAndSwapObjectRelease(base, offset, "foo", "bar");
            assertEquals(r, true, "weakCompareAndSwapRelease Object");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "bar", "weakCompareAndSwapRelease Object");
        }

        // Compare set and get
        {
            Object o = UNSAFE.getAndSetObject(base, offset, "foo");
            assertEquals(o, "bar", "getAndSet Object");
            Object x = UNSAFE.getObject(base, offset);
            assertEquals(x, "foo", "getAndSet Object value");
        }

    }

}

