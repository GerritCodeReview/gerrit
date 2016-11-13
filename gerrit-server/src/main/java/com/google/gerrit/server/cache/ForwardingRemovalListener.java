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

package com.google.gerrit.server.cache;

import com.google.common.base.Strings;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/**
 * This listener dispatches removal events to all other RemovalListeners attached via the DynamicSet
 * API.
 *
 * @param <K>
 * @param <V>
 */
@SuppressWarnings("rawtypes")
public class ForwardingRemovalListener<K, V> implements RemovalListener<K, V> {
  public interface Factory {
    ForwardingRemovalListener create(String cacheName);
  }

  private final DynamicSet<CacheRemovalListener> listeners;
  private final String cacheName;
  private String pluginName = "gerrit";

  @Inject
  ForwardingRemovalListener(
      DynamicSet<CacheRemovalListener> listeners, @Assisted String cacheName) {
    this.listeners = listeners;
    this.cacheName = cacheName;
  }

  @Inject(optional = true)
  void setPluginName(String name) {
    if (!Strings.isNullOrEmpty(name)) {
      this.pluginName = name;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void onRemoval(RemovalNotification<K, V> notification) {
    for (CacheRemovalListener<K, V> l : listeners) {
      l.onRemoval(pluginName, cacheName, notification);
    }
  }
}
