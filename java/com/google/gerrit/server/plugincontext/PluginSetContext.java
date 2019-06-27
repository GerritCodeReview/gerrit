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
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.server.plugincontext.PluginContext.ExtensionImplConsumer;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;
import com.google.inject.Inject;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.stream.Stream;

/**
 * Context to invoke extensions from a {@link DynamicSet}.
 *
 * <p>When a plugin extension is invoked a logging tag with the plugin name is set. This way any
 * errors that are triggered by the plugin extension (even if they happen in Gerrit code which is
 * called by the plugin extension) can be easily attributed to the plugin.
 *
 * <p>Example if all exceptions should be caught and logged:
 *
 * <pre>
 * fooPluginSetContext.runEach(foo -> foo.doFoo());
 * </pre>
 *
 * <p>Example if all exceptions, but one, should be caught and logged:
 *
 * <pre>
 * try {
 *   fooPluginSetContext.runEach(foo -> foo.doFoo(), MyException.class);
 * } catch (MyException e) {
 *   // handle the exception
 * }
 * </pre>
 *
 * <p>Example if return values should be handled:
 *
 * <pre>
 * for (PluginSetEntryContext<Foo> c : fooPluginSetContext) {
 *   if (c.call(foo -> foo.handles(x))) {
 *     c.run(foo -> foo.doFoo());
 *   }
 * }
 * </pre>
 *
 * <p>Example if return values and a single exception should be handled:
 *
 * <pre>
 * try {
 *   for (PluginSetEntryContext<Foo> c : fooPluginSetContext) {
 *     if (c.call(foo -> foo.handles(x), MyException.class)) {
 *       c.run(foo -> foo.doFoo(), MyException.class);
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
 * for (Extension<Foo> fooExtension : fooDynamicSet.entries()) {
 *   try (TraceContext traceContext = PluginContext.newTrace(fooExtension)) {
 *     fooExtension.get().doFoo();
 *   } catch (MyException1 | MyException2 | MyException3 e) {
 *     // handle the exception
 *   }
 * }
 * </pre>
 */
public class PluginSetContext<T> implements Iterable<PluginSetEntryContext<T>> {
  private final DynamicSet<T> dynamicSet;
  private final PluginMetrics pluginMetrics;

  @VisibleForTesting
  @Inject
  public PluginSetContext(DynamicSet<T> dynamicSet, PluginMetrics pluginMetrics) {
    this.dynamicSet = dynamicSet;
    this.pluginMetrics = pluginMetrics;
  }

  /**
   * Iterator that provides contexts for invoking the extensions in this set.
   *
   * <p>This is useful if:
   *
   * <ul>
   *   <li>invoking of each extension returns a result that should be handled
   *   <li>a sequence of invocations should be done on each extension
   * </ul>
   */
  @Override
  public Iterator<PluginSetEntryContext<T>> iterator() {
    return Iterators.transform(
        dynamicSet.entries().iterator(), e -> new PluginSetEntryContext<>(e, pluginMetrics));
  }

  /**
   * Checks if no implementations for this extension point have been registered.
   *
   * @return {@code true} if no implementations for this extension point have been registered,
   *     otherwise {@code false}
   */
  public boolean isEmpty() {
    return !dynamicSet.iterator().hasNext();
  }

  /**
   * Returns a sorted list of the plugins that have registered implementations for this extension
   * point.
   *
   * @return sorted list of the plugins that have registered implementations for this extension
   *     point
   */
  public SortedSet<String> plugins() {
    return dynamicSet.plugins();
  }

  /**
   * Invokes each extension in the set. All exceptions from the plugin extensions are caught and
   * logged.
   *
   * <p>The consumer gets the extension implementation provided that should be invoked.
   *
   * <p>All extension in the set are invoked, even if invoking some of the extensions failed.
   *
   * @param extensionImplConsumer consumer that invokes the extension
   */
  public void runEach(ExtensionImplConsumer<T> extensionImplConsumer) {
    dynamicSet
        .entries()
        .forEach(p -> PluginContext.runLogExceptions(pluginMetrics, p, extensionImplConsumer));
  }

  public Stream<T> stream() {
    return dynamicSet.stream();
  }

  /**
   * Invokes each extension in the set. All exceptions from the plugin extensions except exceptions
   * of the specified type are caught and logged.
   *
   * <p>The consumer gets the extension implementation provided that should be invoked.
   *
   * <p>All extension in the set are invoked, even if invoking some of the extensions failed.
   *
   * @param extensionImplConsumer consumer that invokes the extension
   * @param exceptionClass type of the exceptions that should be thrown
   * @throws X expected exception from the plugin extension
   */
  public <X extends Exception> void runEach(
      ExtensionImplConsumer<T> extensionImplConsumer, Class<X> exceptionClass) throws X {
    for (Extension<T> extension : dynamicSet.entries()) {
      PluginContext.runLogExceptions(
          pluginMetrics, extension, extensionImplConsumer, exceptionClass);
    }
  }
}
