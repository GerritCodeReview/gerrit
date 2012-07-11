// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.gerrit.extensions.registration.DynamicSet;

/**
 * This listener dispatches removal events to all other RemovalListeners
 * attached using through the DynamicSet API.
 *
 * @param <K>
 * @param <V>
 */
public class MasterRemovalListener<K, V> implements RemovalListener<K, V> {
  private DynamicSet<CacheRemovalListenerFactory> listenerFactories;
  private String pluginName;
  private String cacheName;

  public MasterRemovalListener(
      DynamicSet<CacheRemovalListenerFactory> listenerFactories,
      String pluginName, String cacheName) {
    this.listenerFactories = listenerFactories;
    this.pluginName = pluginName;
    this.cacheName = cacheName;
  }

  @Override
  public void onRemoval(RemovalNotification<K, V> notification) {
    for (CacheRemovalListenerFactory factory : listenerFactories) {
      RemovalListener<K, V> listener = factory.get(pluginName, cacheName);
      listener.onRemoval(notification);
    }
  }
}
