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

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Types;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A map of members that can be modified as plugins reload.
 *
 * <p>Maps index their members by plugin name and export name.
 *
 * <p>DynamicMaps are always mapped as singletons in Guice. Maps store Providers internally, and
 * resolve the provider to an instance on demand. This enables registrations to decide between
 * singleton and non-singleton members.
 */
public abstract class DynamicMap<T> implements Iterable<DynamicMap.Entry<T>> {
  /**
   * Declare a singleton {@code DynamicMap<T>} with a binder.
   *
   * <p>Maps must be defined in a Guice module before they can be bound:
   *
   * <pre>
   * DynamicMap.mapOf(binder(), Interface.class);
   * bind(Interface.class)
   *   .annotatedWith(Exports.named(&quot;foo&quot;))
   *   .to(Impl.class);
   * </pre>
   *
   * @param binder a new binder created in the module.
   * @param member type of value in the map.
   */
  public static <T> void mapOf(Binder binder, Class<T> member) {
    mapOf(binder, TypeLiteral.get(member));
  }

  /**
   * Declare a singleton {@code DynamicMap<T>} with a binder.
   *
   * <p>Maps must be defined in a Guice module before they can be bound:
   *
   * <pre>
   * DynamicMap.mapOf(binder(), new TypeLiteral&lt;Thing&lt;Bar&gt;&gt;(){});
   * bind(new TypeLiteral&lt;Thing&lt;Bar&gt;&gt;() {})
   *   .annotatedWith(Exports.named(&quot;foo&quot;))
   *   .to(Impl.class);
   * </pre>
   *
   * @param binder a new binder created in the module.
   * @param member type of value in the map.
   */
  public static <T> void mapOf(Binder binder, TypeLiteral<T> member) {
    @SuppressWarnings("unchecked")
    Key<DynamicMap<T>> key =
        (Key<DynamicMap<T>>)
            Key.get(Types.newParameterizedType(DynamicMap.class, member.getType()));
    binder.bind(key).toProvider(new DynamicMapProvider<>(member)).in(Scopes.SINGLETON);
  }

  final ConcurrentMap<NamePair, Provider<T>> items;

  DynamicMap() {
    items =
        new ConcurrentHashMap<>(
            16 /* initial size */,
            0.75f /* load factor */,
            1 /* concurrency level of 1, load/unload is single threaded */);
  }

  /**
   * Lookup an implementation by name.
   *
   * @param pluginName local name of the plugin providing the item.
   * @param exportName name the plugin exports the item as.
   * @return the implementation. Null if the plugin is not running, or if the plugin does not export
   *     this name.
   * @throws ProvisionException if the registered provider is unable to obtain an instance of the
   *     requested implementation.
   */
  public T get(String pluginName, String exportName) throws ProvisionException {
    Provider<T> p = items.get(new NamePair(pluginName, exportName));
    return p != null ? p.get() : null;
  }

  /**
   * Get the names of all running plugins supplying this type.
   *
   * @return sorted set of active plugins that supply at least one item.
   */
  public SortedSet<String> plugins() {
    SortedSet<String> r = new TreeSet<>();
    for (NamePair p : items.keySet()) {
      r.add(p.pluginName);
    }
    return Collections.unmodifiableSortedSet(r);
  }

  /**
   * Get the items exported by a single plugin.
   *
   * @param pluginName name of the plugin.
   * @return items exported by a plugin, keyed by the export name.
   */
  public SortedMap<String, Provider<T>> byPlugin(String pluginName) {
    SortedMap<String, Provider<T>> r = new TreeMap<>();
    for (Map.Entry<NamePair, Provider<T>> e : items.entrySet()) {
      if (e.getKey().pluginName.equals(pluginName)) {
        r.put(e.getKey().exportName, e.getValue());
      }
    }
    return Collections.unmodifiableSortedMap(r);
  }

  /** Iterate through all entries in an undefined order. */
  @Override
  public Iterator<Entry<T>> iterator() {
    final Iterator<Map.Entry<NamePair, Provider<T>>> i = items.entrySet().iterator();
    return new Iterator<Entry<T>>() {
      @Override
      public boolean hasNext() {
        return i.hasNext();
      }

      @Override
      public Entry<T> next() {
        final Map.Entry<NamePair, Provider<T>> e = i.next();
        return new Entry<T>() {
          @Override
          public String getPluginName() {
            return e.getKey().pluginName;
          }

          @Override
          public String getExportName() {
            return e.getKey().exportName;
          }

          @Override
          public Provider<T> getProvider() {
            return e.getValue();
          }
        };
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public interface Entry<T> {
    String getPluginName();

    String getExportName();

    Provider<T> getProvider();
  }

  static class NamePair {
    private final String pluginName;
    private final String exportName;

    NamePair(String pn, String en) {
      this.pluginName = pn;
      this.exportName = en;
    }

    @Override
    public int hashCode() {
      return pluginName.hashCode() * 31 + exportName.hashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof NamePair) {
        NamePair np = (NamePair) other;
        return pluginName.equals(np.pluginName) && exportName.equals(np.exportName);
      }
      return false;
    }
  }

  public static <T> DynamicMap<T> emptyMap() {
    return new DynamicMap<T>() {};
  }
}
