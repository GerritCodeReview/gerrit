// Copyright (C) 2008 The Android Open Source Project
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

import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Statistics;
import net.sf.ehcache.Status;
import net.sf.ehcache.bootstrap.BootstrapCacheLoader;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.event.RegisteredEventListeners;
import net.sf.ehcache.exceptionhandler.CacheExceptionHandler;
import net.sf.ehcache.extension.CacheExtension;
import net.sf.ehcache.loader.CacheLoader;
import net.sf.ehcache.statistics.CacheUsageListener;
import net.sf.ehcache.statistics.LiveCacheStatistics;
import net.sf.ehcache.statistics.sampled.SampledCacheStatistics;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Proxy around a cache which has not yet been created. */
final class ProxyEhcache implements Ehcache {
  private final String cacheName;
  private volatile Ehcache self;

  ProxyEhcache(final String cacheName) {
    this.cacheName = cacheName;
  }

  void bind(final Ehcache self) {
    this.self = self;
  }

  private Ehcache self() {
    return self;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException();
  }

  @Override
  public String getName() {
    return cacheName;
  }

  @Override
  public void setName(String name) {
    throw new UnsupportedOperationException();
  }

  //
  // Everything else delegates through self.
  //

  public void bootstrap() {
    self().bootstrap();
  }

  public long calculateInMemorySize() throws IllegalStateException,
      CacheException {
    return self().calculateInMemorySize();
  }

  public void clearStatistics() {
    self().clearStatistics();
  }

  public void dispose() throws IllegalStateException {
    self().dispose();
  }

  public void evictExpiredElements() {
    self().evictExpiredElements();
  }

  public void flush() throws IllegalStateException, CacheException {
    self().flush();
  }

  public Element get(Object key) throws IllegalStateException, CacheException {
    return self().get(key);
  }

  public Element get(Serializable key) throws IllegalStateException,
      CacheException {
    return self().get(key);
  }

  public Map getAllWithLoader(Collection keys, Object loaderArgument)
      throws CacheException {
    return self().getAllWithLoader(keys, loaderArgument);
  }

  public float getAverageGetTime() {
    return self().getAverageGetTime();
  }

  public BootstrapCacheLoader getBootstrapCacheLoader() {
    return self().getBootstrapCacheLoader();
  }

  public CacheConfiguration getCacheConfiguration() {
    if (self == null) {
      // In Ehcache 1.7, BlockingCache wants to ask us if we are
      // clustered using Terracotta. Unfortunately it is too early
      // to know for certain as the caches have not actually been
      // created or configured.
      //
      return new CacheConfiguration();
    }
    return self().getCacheConfiguration();
  }

  public RegisteredEventListeners getCacheEventNotificationService() {
    return self().getCacheEventNotificationService();
  }

  public CacheExceptionHandler getCacheExceptionHandler() {
    return self().getCacheExceptionHandler();
  }

  public CacheManager getCacheManager() {
    return self().getCacheManager();
  }

  public int getDiskStoreSize() throws IllegalStateException {
    return self().getDiskStoreSize();
  }

  public String getGuid() {
    return self().getGuid();
  }

  public List getKeys() throws IllegalStateException, CacheException {
    return self().getKeys();
  }

  public List getKeysNoDuplicateCheck() throws IllegalStateException {
    return self().getKeysNoDuplicateCheck();
  }

  public List getKeysWithExpiryCheck() throws IllegalStateException,
      CacheException {
    return self().getKeysWithExpiryCheck();
  }

  public long getMemoryStoreSize() throws IllegalStateException {
    return self().getMemoryStoreSize();
  }

  public Element getQuiet(Object key) throws IllegalStateException,
      CacheException {
    return self().getQuiet(key);
  }

  public Element getQuiet(Serializable key) throws IllegalStateException,
      CacheException {
    return self().getQuiet(key);
  }

  public List<CacheExtension> getRegisteredCacheExtensions() {
    return self().getRegisteredCacheExtensions();
  }

  public List<CacheLoader> getRegisteredCacheLoaders() {
    return self().getRegisteredCacheLoaders();
  }

  public int getSize() throws IllegalStateException, CacheException {
    return self().getSize();
  }

  public Statistics getStatistics() throws IllegalStateException {
    return self().getStatistics();
  }

  public int getStatisticsAccuracy() {
    return self().getStatisticsAccuracy();
  }

  public Status getStatus() {
    return self().getStatus();
  }

  public Element getWithLoader(Object key, CacheLoader loader,
      Object loaderArgument) throws CacheException {
    return self().getWithLoader(key, loader, loaderArgument);
  }

  public void initialise() {
    self().initialise();
  }

  public boolean isDisabled() {
    return self().isDisabled();
  }

  public boolean isElementInMemory(Object key) {
    return self().isElementInMemory(key);
  }

  public boolean isElementInMemory(Serializable key) {
    return self().isElementInMemory(key);
  }

  public boolean isElementOnDisk(Object key) {
    return self().isElementOnDisk(key);
  }

  public boolean isElementOnDisk(Serializable key) {
    return self().isElementOnDisk(key);
  }

  public boolean isExpired(Element element) throws IllegalStateException,
      NullPointerException {
    return self().isExpired(element);
  }

  public boolean isKeyInCache(Object key) {
    return self().isKeyInCache(key);
  }

  public boolean isValueInCache(Object value) {
    return self().isValueInCache(value);
  }

  public void load(Object key) throws CacheException {
    self().load(key);
  }

  public void loadAll(Collection keys, Object argument) throws CacheException {
    self().loadAll(keys, argument);
  }

  public void put(Element element, boolean doNotNotifyCacheReplicators)
      throws IllegalArgumentException, IllegalStateException, CacheException {
    self().put(element, doNotNotifyCacheReplicators);
  }

  public void put(Element element) throws IllegalArgumentException,
      IllegalStateException, CacheException {
    self().put(element);
  }

  public void putQuiet(Element element) throws IllegalArgumentException,
      IllegalStateException, CacheException {
    self().putQuiet(element);
  }

  public void registerCacheExtension(CacheExtension cacheExtension) {
    self().registerCacheExtension(cacheExtension);
  }

  public void registerCacheLoader(CacheLoader cacheLoader) {
    self().registerCacheLoader(cacheLoader);
  }

  public boolean remove(Object key, boolean doNotNotifyCacheReplicators)
      throws IllegalStateException {
    return self().remove(key, doNotNotifyCacheReplicators);
  }

  public boolean remove(Object key) throws IllegalStateException {
    return self().remove(key);
  }

  public boolean remove(Serializable key, boolean doNotNotifyCacheReplicators)
      throws IllegalStateException {
    return self().remove(key, doNotNotifyCacheReplicators);
  }

  public boolean remove(Serializable key) throws IllegalStateException {
    return self().remove(key);
  }

  public void removeAll() throws IllegalStateException, CacheException {
    self().removeAll();
  }

  public void removeAll(boolean doNotNotifyCacheReplicators)
      throws IllegalStateException, CacheException {
    self().removeAll(doNotNotifyCacheReplicators);
  }

  public boolean removeQuiet(Object key) throws IllegalStateException {
    return self().removeQuiet(key);
  }

  public boolean removeQuiet(Serializable key) throws IllegalStateException {
    return self().removeQuiet(key);
  }

  public void setBootstrapCacheLoader(BootstrapCacheLoader bootstrapCacheLoader)
      throws CacheException {
    self().setBootstrapCacheLoader(bootstrapCacheLoader);
  }

  public void setCacheExceptionHandler(
      CacheExceptionHandler cacheExceptionHandler) {
    self().setCacheExceptionHandler(cacheExceptionHandler);
  }

  public void setCacheManager(CacheManager cacheManager) {
    self().setCacheManager(cacheManager);
  }

  public void setDisabled(boolean disabled) {
    self().setDisabled(disabled);
  }

  public void setDiskStorePath(String diskStorePath) throws CacheException {
    self().setDiskStorePath(diskStorePath);
  }

  public void setStatisticsAccuracy(int statisticsAccuracy) {
    self().setStatisticsAccuracy(statisticsAccuracy);
  }

  public void unregisterCacheExtension(CacheExtension cacheExtension) {
    self().unregisterCacheExtension(cacheExtension);
  }

  public void unregisterCacheLoader(CacheLoader cacheLoader) {
    self().unregisterCacheLoader(cacheLoader);
  }

  public Object getInternalContext() {
    return self.getInternalContext();
  }

  public LiveCacheStatistics getLiveCacheStatistics() throws IllegalStateException {
    return self.getLiveCacheStatistics();
  }

  public SampledCacheStatistics getSampledCacheStatistics() {
    return self.getSampledCacheStatistics();
  }

  public int getSizeBasedOnAccuracy(int statisticsAccuracy) throws IllegalArgumentException,
      IllegalStateException, CacheException {
    return self.getSizeBasedOnAccuracy(statisticsAccuracy);
  }

  public boolean isSampledStatisticsEnabled() {
    return self.isSampledStatisticsEnabled();
  }

  public boolean isStatisticsEnabled() {
    return self.isStatisticsEnabled();
  }

  public void registerCacheUsageListener(CacheUsageListener cacheUsageListener)
      throws IllegalStateException {
    self.registerCacheUsageListener(cacheUsageListener);
  }

  public void removeCacheUsageListener(CacheUsageListener cacheUsageListener)
      throws IllegalStateException {
    self.removeCacheUsageListener(cacheUsageListener);
  }

  public void setSampledStatisticsEnabled(boolean enableStatistics) {
    self.setSampledStatisticsEnabled(enableStatistics);
  }

  public void setStatisticsEnabled(boolean enableStatistics) {
    self.setStatisticsEnabled(enableStatistics);
  }
}
