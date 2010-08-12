// Copyright (C) 2010 The Android Open Source Project
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

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.TimeUnit;

/** Proxy around a cache which has not yet been created. */
public final class ProxyCache<K, V> implements Cache<K, V> {
  private volatile Cache<K, V> self;

  public void bind(Cache<K, V> self) {
    this.self = self;
  }

  @Override
  public ListenableFuture<V> get(K key) {
    return self.get(key);
  }

  @Override
  public ListenableFuture<Void> putAsync(K key, V value) {
    return self.putAsync(key, value);
  }

  @Override
  public ListenableFuture<Void> removeAsync(K key) {
    return self.removeAsync(key);
  }

  @Override
  public ListenableFuture<Void> removeAllAsync() {
    return self.removeAllAsync();
  }

  @Override
  public long getTimeToLive(TimeUnit unit) {
    return self.getTimeToLive(unit);
  }
}
