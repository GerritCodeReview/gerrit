// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.plugincontext;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PluginEntry;
import com.google.gerrit.server.plugincontext.PluginContext.PluginEntryConsumer;
import com.google.inject.Inject;
import java.util.Iterator;
import java.util.SortedSet;

/**
 * Context to invoke {@link DynamicMap} plugins.
 *
 * <p>When the plugin is invoked a logging tag with the plugin name is set. This way any errors that
 * are triggered by the plugin (even if they happen in Gerrit code which is called by the plugin)
 * can be easily attributed to the plugin.
 *
 * <p>Example if all exceptions should be caught and logged:
 *
 * <pre>
 * Map<String, Object> results = new HashMap<>();
 * fooPluginMapContext.runEach(
 *     pluginEntry -> results.put(pluginEntry.getExportName(), pluginEntry.get().getFoo());
 * </pre>
 *
 * <p>Example if all exceptions, but one, should be caught and logged:
 *
 * <pre>
 * Map<String, Object> results = new HashMap<>();
 * try {
 *   fooPluginMapContext.runEach(
 *       pluginEntry -> results.put(pluginEntry.getExportName(), pluginEntry.get().getFoo(),
 *       MyException.class);
 * } catch (MyException e) {
 *   // handle the exception
 * }
 * </pre>
 *
 * <p>Example if return values should be handled:
 *
 * <pre>
 * Map<String, Object> results = new HashMap<>();
 * for (PluginMapEntryContext<Foo> c : fooPluginMapContext) {
 *   if (c.call(pluginEntry -> pluginEntry.get().handles(x))) {
 *     c.run(pluginEntry -> results.put(pluginEntry.getExportName(), pluginEntry.get().getFoo());
 *   }
 * }
 * </pre>
 *
 * <p>Example if return values and a single exception should be handled:
 *
 * <pre>
 * Map<String, Object> results = new HashMap<>();
 * try {
 *   for (PluginMapEntryContext<Foo> c : fooPluginMapContext) {
 *     if (c.call(pluginEntry -> pluginEntry.handles(x), MyException.class)) {
 *       c.run(pluginEntry -> results.put(pluginEntry.getExportName(), pluginEntry.get().getFoo(),
 *           MyException.class);
 *     }
 *   }
 * } catch (MyException e) {
 *   // handle the exception
 * }
 * </pre>
 *
 * <p>Example if several exceptions should be handled:
 *
 * <pre>
 * for (PluginEntry<Foo> fooEntry : fooDynamicMap) {
 *   try (TraceContext traceContext = PluginContext.newTrace(fooEntry)) {
 *     fooEntry.get().doFoo();
 *   } catch (MyException1 | MyException2 | MyException3 e) {
 *     // handle the exception
 *   }
 * }
 * </pre>
 */
public class PluginMapContext<T> implements Iterable<PluginMapEntryContext<T>> {
  private final DynamicMap<T> dynamicMap;

  @VisibleForTesting
  @Inject
  public PluginMapContext(DynamicMap<T> dynamicMap) {
    this.dynamicMap = dynamicMap;
  }

  /**
   * Iterator that provides contexts for invoking the entries of this map.
   *
   * <p>This is useful if:
   *
   * <ul>
   *   <li>invoking of each entry returns a result that should be handled
   *   <li>a sequence of invocations should be done on each entry
   * </ul>
   */
  @Override
  public Iterator<PluginMapEntryContext<T>> iterator() {
    Iterator<PluginEntry<T>> pluginIterator = dynamicMap.iterator();
    return new Iterator<PluginMapEntryContext<T>>() {

      @Override
      public boolean hasNext() {
        return pluginIterator.hasNext();
      }

      @Override
      public PluginMapEntryContext<T> next() {
        return new PluginMapEntryContext<>(pluginIterator.next());
      }
    };
  }

  /**
   * Checks if no implementations for this extension point have been registered.
   *
   * @return {@code true} if no implementations for this extension point have been registered,
   *     otherwise {@code false}
   */
  public boolean isEmpty() {
    return !dynamicMap.iterator().hasNext();
  }

  /**
   * Returns a sorted list of the plugins that have registered an implementation for this extension
   * point.
   *
   * @return sorted list of the plugins that have registered an implementation for this extension
   *     point
   */
  public SortedSet<String> plugins() {
    return dynamicMap.plugins();
  }

  /**
   * Invokes each entry of the map. All exceptions from the plugin are caught and logged.
   *
   * <p>The consumer get the {@link PluginEntry} provided that should be invoked. The plugin entry
   * provides access to the plugin name and the export name.
   *
   * @param pluginConsumer consumer that invokes the extension point
   */
  public void runEach(PluginEntryConsumer<T> pluginConsumer) {
    dynamicMap.forEach(p -> PluginContext.runLogExceptions(p, pluginConsumer));
  }

  /**
   * Invokes each entry of the map. All exceptions from the plugin except exceptions of the
   * specified type are caught and logged.
   *
   * <p>The consumer get the {@link PluginEntry} provided that should be invoked. The plugin entry
   * provides access to the plugin name and the export name.
   *
   * @param pluginConsumer consumer that invokes the extension point
   * @param exceptionClass type of the exceptions that should be thrown
   * @throws X expected exception from the plugin
   */
  public <X extends Exception> void runEach(
      PluginEntryConsumer<T> pluginConsumer, Class<X> exceptionClass) throws X {
    for (PluginEntry<T> pluginEntry : dynamicMap) {
      PluginContext.runLogExceptions(pluginEntry, pluginConsumer, exceptionClass);
    }
  }
}
