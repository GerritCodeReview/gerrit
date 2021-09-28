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
import com.google.common.collect.Iterators;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.server.plugincontext.PluginContext.ExtensionConsumer;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;
import com.google.inject.Inject;
import java.util.Iterator;
import java.util.SortedSet;

/**
 * Context to invoke extensions from a {@link DynamicMap}.
 *
 * <p>When a plugin extension is invoked a logging tag with the plugin name is set. This way any
 * errors that are triggered by the plugin extension (even if they happen in Gerrit code which is
 * called by the plugin extension) can be easily attributed to the plugin.
 *
 * <p>Example if all exceptions should be caught and logged:
 *
 * <pre>{@code
 * Map<String, Object> results = new HashMap<>();
 * fooPluginMapContext.runEach(
 *     extension -> results.put(extension.getExportName(), extension.get().getFoo());
 * }</pre>
 *
 * <p>Example if all exceptions, but one, should be caught and logged:
 *
 * <pre>{@code
 * Map<String, Object> results = new HashMap<>();
 * try {
 *   fooPluginMapContext.runEach(
 *       extension -> results.put(extension.getExportName(), extension.get().getFoo(),
 *       MyException.class);
 * } catch (MyException e) {
 *   // handle the exception
 * }
 * }</pre>
 *
 * <p>Example if return values should be handled:
 *
 * <pre>{@code
 * Map<String, Object> results = new HashMap<>();
 * for (PluginMapEntryContext<Foo> c : fooPluginMapContext) {
 *   if (c.call(extension -> extension.get().handles(x))) {
 *     c.run(extension -> results.put(extension.getExportName(), extension.get().getFoo());
 *   }
 * }
 * }</pre>
 *
 * <p>Example if return values and a single exception should be handled:
 *
 * <pre>{@code
 * Map<String, Object> results = new HashMap<>();
 * try {
 *   for (PluginMapEntryContext<Foo> c : fooPluginMapContext) {
 *     if (c.call(extension -> extension.handles(x), MyException.class)) {
 *       c.run(extension -> results.put(extension.getExportName(), extension.get().getFoo(),
 *           MyException.class);
 *     }
 *   }
 * } catch (MyException e) {
 *   // handle the exception
 * }
 * }</pre>
 *
 * <p>Example if several exceptions should be handled:
 *
 * <pre>{@code
 * for (Extension<Foo> fooExtension : fooDynamicMap) {
 *   try (TraceContext traceContext = PluginContext.newTrace(fooExtension)) {
 *     fooExtension.get().doFoo();
 *   } catch (MyException1 | MyException2 | MyException3 e) {
 *     // handle the exception
 *   }
 * }
 * }</pre>
 */
public class PluginMapContext<T> implements Iterable<PluginMapEntryContext<T>> {
  private final DynamicMap<T> dynamicMap;
  private final PluginMetrics pluginMetrics;

  @VisibleForTesting
  @Inject
  public PluginMapContext(DynamicMap<T> dynamicMap, PluginMetrics pluginMetrics) {
    this.dynamicMap = dynamicMap;
    this.pluginMetrics = pluginMetrics;
  }

  /**
   * Iterator that provides contexts for invoking the extensions in this map.
   *
   * <p>This is useful if:
   *
   * <ul>
   *   <li>invoking of each extension returns a result that should be handled
   *   <li>a sequence of invocations should be done on each extension
   * </ul>
   */
  @Override
  public Iterator<PluginMapEntryContext<T>> iterator() {
    return Iterators.transform(
        dynamicMap.iterator(), e -> new PluginMapEntryContext<>(e, pluginMetrics));
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
   * Returns a sorted list of the plugins that have registered implementations for this extension
   * point.
   *
   * @return sorted list of the plugins that have registered implementations for this extension
   *     point
   */
  public SortedSet<String> plugins() {
    return dynamicMap.plugins();
  }

  /**
   * Invokes each extension in the map. All exceptions from the plugin extensions are caught and
   * logged.
   *
   * <p>The consumer get the {@link Extension} provided that should be invoked. The extension
   * provides access to the plugin name and the export name.
   *
   * <p>All extension in the map are invoked, even if invoking some of the extensions failed.
   *
   * @param extensionConsumer consumer that invokes the extension
   */
  public void runEach(ExtensionConsumer<Extension<T>> extensionConsumer) {
    dynamicMap.forEach(p -> PluginContext.runLogExceptions(pluginMetrics, p, extensionConsumer));
  }

  /**
   * Invokes each extension in the map. All exceptions from the plugin extensions except exceptions
   * of the specified type are caught and logged.
   *
   * <p>The consumer get the {@link Extension} provided that should be invoked. The extension
   * provides access to the plugin name and the export name.
   *
   * <p>All extension in the map are invoked, even if invoking some of the extensions failed.
   *
   * @param extensionConsumer consumer that invokes the extension
   * @param exceptionClass type of the exceptions that should be thrown
   * @throws X expected exception from the plugin extension
   */
  public <X extends Exception> void runEach(
      ExtensionConsumer<Extension<T>> extensionConsumer, Class<X> exceptionClass) throws X {
    for (Extension<T> extension : dynamicMap) {
      PluginContext.runLogExceptions(pluginMetrics, extension, extensionConsumer, exceptionClass);
    }
  }
}
