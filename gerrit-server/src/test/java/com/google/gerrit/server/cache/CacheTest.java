// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.cache;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeThat;

import com.google.common.base.Charsets;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.cache.ForwardingRemovalListener.Factory;
import com.google.gerrit.server.cache.h2.DefaultCacheFactory;
import com.google.gerrit.server.cache.h2.Util;
import com.google.gerrit.testutil.TempFileUtil;
import com.google.inject.TypeLiteral;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public class CacheTest {

  private static TypeLiteral<String> STRING = new TypeLiteral<String>() {};
  private Config cfg;
  private DefaultCacheFactory factory;
  private PersistentCacheFactory h2Factory;
  private CacheProvider<String, String> def;
  private ExecutorService executorService;

  @Before
  public void createConfig() throws ConfigInvalidException,
      FileNotFoundException, IOException {
    String config = "[cache]\n\tdirectory = cache";
    cfg = new BlobBasedConfig(null, config.getBytes(Charsets.UTF_8));
    factory = createCacheFactory(cfg);
    h2Factory = Util.createH2Factory(factory, cfg);
    executorService = Util.getExecutorService(h2Factory);

    def = new CacheProvider<>(null, "name", STRING, STRING);
    def.setMemoryCacheFactory(factory);
    def.setPersistentCacheFactory(h2Factory);
    def.maximumWeight(1000);
  }

  @Test
  public void testNonPersistent() throws Exception {
    Cache<String, String> cache = def.get();

    cache.put("key", "value");

    assertEquals("value", cache.getIfPresent("key"));
    assertNull(cache.getIfPresent("other"));
  }

  @Test
  public void testNonPersistentWithExpiration() {
    def.expireAfterWrite(2, SECONDS);
    AdjustableTicker ticker = new AdjustableTicker();
    def.ticker(ticker);
    Cache<String, String> cache = def.get();

    cache.put("key", "value");
    ticker.set(SECONDS.toNanos(1));

    assertEquals("value", cache.getIfPresent("key"));

    ticker.set(SECONDS.toNanos(3));
    assertNull(cache.getIfPresent("key"));
  }

  @Test
  public void testNonPersistentWithLoader() throws Exception {
    def.loader(new IncrementingLoader());
    AdjustableTicker ticker = new AdjustableTicker();
    def.ticker(ticker);

    LoadingCache<String, String> cache =
        (LoadingCache<String, String>) def.get();

    assertEquals("key_0", cache.get("key"));
    assertNull(cache.getIfPresent("other"));

    ticker.set(SECONDS.toNanos(2));
    assertEquals("key_0", cache.get("key"));
  }

  @Test
  public void testNonPersistentWithLoaderAndExpriration() throws Exception {
    def.expireAfterWrite(1, SECONDS);
    def.loader(new IncrementingLoader());
    AdjustableTicker ticker = new AdjustableTicker();
    def.ticker(ticker);

    LoadingCache<String, String> cache =
        (LoadingCache<String, String>) def.get();

    assertEquals("key_0", cache.get("key"));
    assertNull(cache.getIfPresent("other"));

    ticker.set(SECONDS.toNanos(2));
    assertEquals("key_1", cache.get("key"));
  }

  @Test
  public void testPersistent() throws Exception {
    def.persist(true);

    Cache<String, String> cache = def.get();

    cache.put("key", "value");
    waitForExecutor();

    assertEquals("value", cache.getIfPresent("key"));
    assertNull(cache.getIfPresent("other"));
  }

  @Test
  public void testPersistentWithExpiration() throws Exception {
    def.persist(true);
    def.expireAfterWrite(1, SECONDS);
    AdjustableTicker ticker = new AdjustableTicker();
    def.ticker(ticker);
    Cache<String, String> cache = def.get();

    cache.put("key", "value");
    waitForExecutor();

    assertEquals("value", cache.getIfPresent("key"));

    ticker.set(SECONDS.toNanos(2));
    assertNull(cache.getIfPresent("key"));
  }

  @Test
  public void testPersistentWithLoader() throws Exception {
    def.persist(true);
    def.loader(new IncrementingLoader());
    AdjustableTicker ticker = new AdjustableTicker();
    def.ticker(ticker);

    LoadingCache<String, String> cache =
        (LoadingCache<String, String>) def.get();

    assertEquals("key_0", cache.get("key"));
    assertNull(cache.getIfPresent("other"));

    ticker.set(SECONDS.toNanos(2));
    assertEquals("key_0", cache.get("key"));
  }

  @Test
  public void testPersistentWithLoaderAndExpriration() throws Exception {
    def.persist(true);
    def.expireAfterWrite(1, SECONDS);
    def.loader(new IncrementingLoader());
    AdjustableTicker ticker = new AdjustableTicker();
    def.ticker(ticker);

    LoadingCache<String, String> cache =
        (LoadingCache<String, String>) def.get();

    assertEquals("key_0", cache.get("key"));
    assertNull(cache.getIfPresent("other"));

    ticker.set(SECONDS.toNanos(2));
    assertEquals("key_1", cache.get("key"));
  }

  private void waitForExecutor() throws InterruptedException, BrokenBarrierException {
    assumeThat(((ThreadPoolExecutor)executorService).getMaximumPoolSize(), is(1));

    final CyclicBarrier barrier = new CyclicBarrier(2);
    executorService.submit(new Runnable() {
      @Override
      public void run() {
        try {
          barrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
          return;
        }
      }
    });
    barrier.await();
  }

  private DefaultCacheFactory createCacheFactory(Config cfg) {
    ForwardingRemovalListener.Factory ff = new Factory() {
      @SuppressWarnings({"rawtypes", "unchecked"})
      @Override
      public ForwardingRemovalListener create(String cacheName) {
        return new ForwardingRemovalListener(DynamicSet.emptySet(), cacheName);
      }
    };
    return new DefaultCacheFactory(cfg, ff);
  }

  @After
  public void cleanup() throws IOException {
    TempFileUtil.cleanup();
  }

  private static class IncrementingLoader extends CacheLoader<String, String> {

    AtomicInteger counter = new AtomicInteger();

    @Override
    public String load(String key) throws Exception {
      return key + "_" + counter.getAndIncrement();
    }
  }
}
