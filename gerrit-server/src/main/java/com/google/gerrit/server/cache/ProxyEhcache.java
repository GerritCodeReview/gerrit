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
import net.sf.ehcache.transaction.manager.TransactionManagerLookup;
import net.sf.ehcache.writer.CacheWriter;
import net.sf.ehcache.writer.CacheWriterManager;

import java.beans.PropertyChangeListener;
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
  @Override
  public void bootstrap() {
    self().bootstrap();
  }

  @Override
  public long calculateInMemorySize() throws IllegalStateException,
      CacheException {
    return self().calculateInMemorySize();
  }

  @Override
  public void clearStatistics() {
    self().clearStatistics();
  }

  @Override
  public void dispose() throws IllegalStateException {
    self().dispose();
  }

  @Override
  public void evictExpiredElements() {
    self().evictExpiredElements();
  }

  @Override
  public void flush() throws IllegalStateException, CacheException {
    self().flush();
  }

  @Override
  public Element get(Object key) throws IllegalStateException, CacheException {
    return self().get(key);
  }

  @Override
  public Element get(Serializable key) throws IllegalStateException,
      CacheException {
    return self().get(key);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map getAllWithLoader(Collection keys, Object loaderArgument)
      throws CacheException {
    return self().getAllWithLoader(keys, loaderArgument);
  }

  @Override
  public float getAverageGetTime() {
    return self().getAverageGetTime();
  }

  @Override
  public BootstrapCacheLoader getBootstrapCacheLoader() {
    return self().getBootstrapCacheLoader();
  }

  @Override
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

  @Override
  public RegisteredEventListeners getCacheEventNotificationService() {
    return self().getCacheEventNotificationService();
  }

  @Override
  public CacheExceptionHandler getCacheExceptionHandler() {
    return self().getCacheExceptionHandler();
  }

  @Override
  public CacheManager getCacheManager() {
    return self().getCacheManager();
  }

  @Override
  public int getDiskStoreSize() throws IllegalStateException {
    return self().getDiskStoreSize();
  }

  @Override
  public String getGuid() {
    return self().getGuid();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List getKeys() throws IllegalStateException, CacheException {
    return self().getKeys();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List getKeysNoDuplicateCheck() throws IllegalStateException {
    return self().getKeysNoDuplicateCheck();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List getKeysWithExpiryCheck() throws IllegalStateException,
      CacheException {
    return self().getKeysWithExpiryCheck();
  }

  @Override
  public long getMemoryStoreSize() throws IllegalStateException {
    return self().getMemoryStoreSize();
  }

  @Override
  public Element getQuiet(Object key) throws IllegalStateException,
      CacheException {
    return self().getQuiet(key);
  }

  @Override
  public Element getQuiet(Serializable key) throws IllegalStateException,
      CacheException {
    return self().getQuiet(key);
  }

  @Override
  public List<CacheExtension> getRegisteredCacheExtensions() {
    return self().getRegisteredCacheExtensions();
  }

  @Override
  public List<CacheLoader> getRegisteredCacheLoaders() {
    return self().getRegisteredCacheLoaders();
  }

  @Override
  public int getSize() throws IllegalStateException, CacheException {
    return self().getSize();
  }

  @Override
  public Statistics getStatistics() throws IllegalStateException {
    return self().getStatistics();
  }

  @Override
  public int getStatisticsAccuracy() {
    return self().getStatisticsAccuracy();
  }

  @Override
  public Status getStatus() {
    return self().getStatus();
  }

  @Override
  public Element getWithLoader(Object key, CacheLoader loader,
      Object loaderArgument) throws CacheException {
    return self().getWithLoader(key, loader, loaderArgument);
  }

  @Override
  public void initialise() {
    self().initialise();
  }

  @Override
  public boolean isDisabled() {
    return self().isDisabled();
  }

  @Override
  public boolean isElementInMemory(Object key) {
    return self().isElementInMemory(key);
  }

  @Override
  public boolean isElementInMemory(Serializable key) {
    return self().isElementInMemory(key);
  }

  @Override
  public boolean isElementOnDisk(Object key) {
    return self().isElementOnDisk(key);
  }

  @Override
  public boolean isElementOnDisk(Serializable key) {
    return self().isElementOnDisk(key);
  }

  @Override
  public boolean isExpired(Element element) throws IllegalStateException,
      NullPointerException {
    return self().isExpired(element);
  }

  @Override
  public boolean isKeyInCache(Object key) {
    return self().isKeyInCache(key);
  }

  @Override
  public boolean isValueInCache(Object value) {
    return self().isValueInCache(value);
  }

  @Override
  public void load(Object key) throws CacheException {
    self().load(key);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void loadAll(Collection keys, Object argument) throws CacheException {
    self().loadAll(keys, argument);
  }

  @Override
  public void put(Element element, boolean doNotNotifyCacheReplicators)
      throws IllegalArgumentException, IllegalStateException, CacheException {
    self().put(element, doNotNotifyCacheReplicators);
  }

  @Override
  public void put(Element element) throws IllegalArgumentException,
      IllegalStateException, CacheException {
    self().put(element);
  }

  @Override
  public void putQuiet(Element element) throws IllegalArgumentException,
      IllegalStateException, CacheException {
    self().putQuiet(element);
  }

  @Override
  public void registerCacheExtension(CacheExtension cacheExtension) {
    self().registerCacheExtension(cacheExtension);
  }

  @Override
  public void registerCacheLoader(CacheLoader cacheLoader) {
    self().registerCacheLoader(cacheLoader);
  }

  @Override
  public boolean remove(Object key, boolean doNotNotifyCacheReplicators)
      throws IllegalStateException {
    return self().remove(key, doNotNotifyCacheReplicators);
  }

  @Override
  public boolean remove(Object key) throws IllegalStateException {
    return self().remove(key);
  }

  @Override
  public boolean remove(Serializable key, boolean doNotNotifyCacheReplicators)
      throws IllegalStateException {
    return self().remove(key, doNotNotifyCacheReplicators);
  }

  @Override
  public boolean remove(Serializable key) throws IllegalStateException {
    return self().remove(key);
  }

  @Override
  public void removeAll() throws IllegalStateException, CacheException {
    self().removeAll();
  }

  @Override
  public void removeAll(boolean doNotNotifyCacheReplicators)
      throws IllegalStateException, CacheException {
    self().removeAll(doNotNotifyCacheReplicators);
  }

  @Override
  public boolean removeQuiet(Object key) throws IllegalStateException {
    return self().removeQuiet(key);
  }

  @Override
  public boolean removeQuiet(Serializable key) throws IllegalStateException {
    return self().removeQuiet(key);
  }

  @Override
  public void setBootstrapCacheLoader(BootstrapCacheLoader bootstrapCacheLoader)
      throws CacheException {
    self().setBootstrapCacheLoader(bootstrapCacheLoader);
  }

  @Override
  public void setCacheExceptionHandler(
      CacheExceptionHandler cacheExceptionHandler) {
    self().setCacheExceptionHandler(cacheExceptionHandler);
  }

  @Override
  public void setCacheManager(CacheManager cacheManager) {
    self().setCacheManager(cacheManager);
  }

  @Override
  public void setDisabled(boolean disabled) {
    self().setDisabled(disabled);
  }

  @Override
  public void setDiskStorePath(String diskStorePath) throws CacheException {
    self().setDiskStorePath(diskStorePath);
  }

  @Override
  public void setStatisticsAccuracy(int statisticsAccuracy) {
    self().setStatisticsAccuracy(statisticsAccuracy);
  }

  @Override
  public void unregisterCacheExtension(CacheExtension cacheExtension) {
    self().unregisterCacheExtension(cacheExtension);
  }

  @Override
  public void unregisterCacheLoader(CacheLoader cacheLoader) {
    self().unregisterCacheLoader(cacheLoader);
  }

  @Override
  public Object getInternalContext() {
    return self().getInternalContext();
  }

  @Override
  public LiveCacheStatistics getLiveCacheStatistics() throws IllegalStateException {
    return self().getLiveCacheStatistics();
  }

  @Override
  public SampledCacheStatistics getSampledCacheStatistics() {
    return self().getSampledCacheStatistics();
  }

  @Override
  public int getSizeBasedOnAccuracy(int statisticsAccuracy) throws IllegalArgumentException,
      IllegalStateException, CacheException {
    return self().getSizeBasedOnAccuracy(statisticsAccuracy);
  }

  @Override
  public boolean isSampledStatisticsEnabled() {
    return self().isSampledStatisticsEnabled();
  }

  @Override
  public boolean isStatisticsEnabled() {
    return self().isStatisticsEnabled();
  }

  @Override
  public void registerCacheUsageListener(CacheUsageListener cacheUsageListener)
      throws IllegalStateException {
    self().registerCacheUsageListener(cacheUsageListener);
  }

  @Override
  public void removeCacheUsageListener(CacheUsageListener cacheUsageListener)
      throws IllegalStateException {
    self().removeCacheUsageListener(cacheUsageListener);
  }

  @Override
  public void setSampledStatisticsEnabled(boolean enableStatistics) {
    self().setSampledStatisticsEnabled(enableStatistics);
  }

  @Override
  public void setStatisticsEnabled(boolean enableStatistics) {
    self().setStatisticsEnabled(enableStatistics);
  }

  @Override
  public void putWithWriter(Element element) throws IllegalArgumentException, IllegalStateException, CacheException {
    self().putWithWriter(element);
  }

  @Override
  public Element putIfAbsent(Element element) throws NullPointerException {
    return self().putIfAbsent(element);
  }

  @Override
  public boolean removeElement(Element element) throws NullPointerException {
    return self().removeElement(element);
  }

  @Override
  public boolean replace(Element element, Element element1) throws NullPointerException, IllegalArgumentException {
    return self().replace(element, element1);
  }

  @Override
  public Element replace(Element element) throws NullPointerException {
    return self().replace(element);
  }

  @Override
  public boolean removeWithWriter(Object o) throws IllegalStateException, CacheException {
    return self().removeWithWriter(o);
  }

  @Override
  public long calculateOffHeapSize() throws IllegalStateException, CacheException {
    return self().calculateOffHeapSize();
  }

  @Override
  public long getOffHeapStoreSize() throws IllegalStateException {
    return self().getOffHeapStoreSize();
  }

  @Override
  public void registerCacheWriter(CacheWriter cacheWriter) {
    self().registerCacheWriter(cacheWriter);
  }

  @Override
  public void unregisterCacheWriter() {
    self().unregisterCacheWriter();
  }

  @Override
  public CacheWriter getRegisteredCacheWriter() {
    return self().getRegisteredCacheWriter();
  }

  @Override
  public void disableDynamicFeatures() {
    self().disableDynamicFeatures();
  }

  @Override
  public CacheWriterManager getWriterManager() {
    return self().getWriterManager();
  }

  @Override
  public boolean isClusterCoherent() {
    return self().isClusterCoherent();
  }

  @Override
  public boolean isNodeCoherent() {
    return self().isNodeCoherent();
  }

  @Override
  public void setNodeCoherent(boolean b) throws UnsupportedOperationException {
    self().setNodeCoherent(b);
  }

  @Override
  public void waitUntilClusterCoherent() throws UnsupportedOperationException {
    self().waitUntilClusterCoherent();
  }

  @Override
  public void setTransactionManagerLookup(TransactionManagerLookup transactionManagerLookup) {
    self().setTransactionManagerLookup(transactionManagerLookup);
  }

  @Override
  public void addPropertyChangeListener(PropertyChangeListener propertyChangeListener) {
    self().addPropertyChangeListener(propertyChangeListener);
  }

  @Override
  public void removePropertyChangeListener(PropertyChangeListener propertyChangeListener) {
    self().removePropertyChangeListener(propertyChangeListener);
  }
}
