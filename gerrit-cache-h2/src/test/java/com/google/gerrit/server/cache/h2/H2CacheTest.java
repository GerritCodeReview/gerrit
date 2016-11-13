// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.cache.h2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.server.cache.h2.H2CacheImpl.SqlStore;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.inject.TypeLiteral;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;

public class H2CacheTest {
  private static int dbCnt;

  private Cache<String, ValueHolder<Boolean>> mem;
  private H2CacheImpl<String, Boolean> impl;

  @Before
  public void setUp() {
    mem = CacheBuilder.newBuilder().build();

    TypeLiteral<String> keyType = new TypeLiteral<String>() {};
    SqlStore<String, Boolean> store =
        new SqlStore<>("jdbc:h2:mem:" + "Test_" + (++dbCnt), keyType, 1 << 20, 0);
    impl = new H2CacheImpl<>(MoreExecutors.directExecutor(), store, keyType, mem);
  }

  @Test
  public void get() throws ExecutionException {
    assertNull(impl.getIfPresent("foo"));

    final AtomicBoolean called = new AtomicBoolean();
    assertTrue(
        impl.get(
            "foo",
            new Callable<Boolean>() {
              @Override
              public Boolean call() throws Exception {
                called.set(true);
                return true;
              }
            }));
    assertTrue("used Callable", called.get());
    assertTrue("exists in cache", impl.getIfPresent("foo"));
    mem.invalidate("foo");
    assertTrue("exists on disk", impl.getIfPresent("foo"));

    called.set(false);
    assertTrue(
        impl.get(
            "foo",
            new Callable<Boolean>() {
              @Override
              public Boolean call() throws Exception {
                called.set(true);
                return true;
              }
            }));
    assertFalse("did not invoke Callable", called.get());
  }
}
