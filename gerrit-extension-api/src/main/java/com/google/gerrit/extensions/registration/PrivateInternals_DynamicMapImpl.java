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

package com.google.gerrit.extensions.registration;

import com.google.gerrit.extensions.annotations.Export;
import com.google.inject.Key;
import com.google.inject.Provider;

/** <b>DO NOT USE</b> */
public class PrivateInternals_DynamicMapImpl<T> extends DynamicMap<T> {
  PrivateInternals_DynamicMapImpl() {}

  /**
   * Store one new element into the map.
   *
   * @param pluginName unique name of the plugin providing the export.
   * @param exportName name the plugin has exported the item as.
   * @param item the item to add to the collection. Must not be null.
   * @return handle to remove the item at a later point in time.
   */
  public RegistrationHandle put(String pluginName, String exportName, final Provider<T> item) {
    final NamePair key = new NamePair(pluginName, exportName);
    items.put(key, item);
    return new RegistrationHandle() {
      @Override
      public void remove() {
        items.remove(key, item);
      }
    };
  }

  /**
   * Store one new element that may be hot-replaceable in the future.
   *
   * @param pluginName unique name of the plugin providing the export.
   * @param key unique description from the item's Guice binding. This can be later obtained from
   *     the registration handle to facilitate matching with the new equivalent instance during a
   *     hot reload. The key must use an {@link Export} annotation.
   * @param item the item to add to the collection right now. Must not be null.
   * @return a handle that can remove this item later, or hot-swap the item without it ever leaving
   *     the collection.
   */
  public ReloadableRegistrationHandle<T> put(String pluginName, Key<T> key, Provider<T> item) {
    String exportName = ((Export) key.getAnnotation()).value();
    NamePair np = new NamePair(pluginName, exportName);
    items.put(np, item);
    return new ReloadableHandle(np, key, item);
  }

  private class ReloadableHandle implements ReloadableRegistrationHandle<T> {
    private final NamePair np;
    private final Key<T> key;
    private final Provider<T> item;

    ReloadableHandle(NamePair np, Key<T> key, Provider<T> item) {
      this.np = np;
      this.key = key;
      this.item = item;
    }

    @Override
    public void remove() {
      items.remove(np, item);
    }

    @Override
    public Key<T> getKey() {
      return key;
    }

    @Override
    public ReloadableHandle replace(Key<T> newKey, Provider<T> newItem) {
      if (items.replace(np, item, newItem)) {
        return new ReloadableHandle(np, newKey, newItem);
      }
      return null;
    }
  }
}
