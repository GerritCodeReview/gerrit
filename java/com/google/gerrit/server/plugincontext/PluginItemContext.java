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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.server.plugincontext.PluginContext.CheckedExtensionImplFunction;
import com.google.gerrit.server.plugincontext.PluginContext.ExtensionImplConsumer;
import com.google.gerrit.server.plugincontext.PluginContext.ExtensionImplFunction;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;
import com.google.inject.Inject;

/**
 * Context to invoke an extension from a {@link DynamicItem}.
 *
 * <p>When the plugin extension is invoked a logging tag with the plugin name is set. This way any
 * errors that are triggered by the plugin extension (even if they happen in Gerrit code which is
 * called by the plugin extension) can be easily attributed to the plugin.
 *
 * <p>The run* methods execute an extension but don't deliver a result back to the caller.
 * Exceptions can be caught and logged.
 *
 * <p>The call* methods execute an extension and deliver a result back to the caller.
 *
 * <p>Example if all exceptions should be caught and logged:
 *
 * <pre>
 * fooPluginItemContext.run(foo -> foo.doFoo());
 * </pre>
 *
 * <p>Example if all exceptions, but one, should be caught and logged:
 *
 * <pre>
 * try {
 *   fooPluginItemContext.run(foo -> foo.doFoo(), MyException.class);
 * } catch (MyException e) {
 *   // handle the exception
 * }
 * </pre>
 *
 * <p>Example if return values should be handled:
 *
 * <pre>
 * Object result = fooPluginItemContext.call(foo -> foo.getFoo());
 * </pre>
 *
 * <p>Example if return values and a single exception should be handled:
 *
 * <pre>
 * Object result;
 * try {
 *   result = fooPluginItemContext.call(foo -> foo.getFoo(), MyException.class);
 * } catch (MyException e) {
 *   // handle the exception
 * }
 * </pre>
 *
 * <p>Example if several exceptions should be handled:
 *
 * <pre>
 * try (TraceContext traceContext = PluginContext.newTrace(fooDynamicItem.getEntry())) {
 *   fooDynamicItem.get().doFoo();
 * } catch (MyException1 | MyException2 | MyException3 e) {
 *   // handle the exception
 * }
 * </pre>
 */
public class PluginItemContext<T> {
  @Nullable private final DynamicItem<T> dynamicItem;
  private final PluginMetrics pluginMetrics;

  @VisibleForTesting
  @Inject
  public PluginItemContext(DynamicItem<T> dynamicItem, PluginMetrics pluginMetrics) {
    this.dynamicItem = dynamicItem;
    this.pluginMetrics = pluginMetrics;
  }

  /**
   * Checks if an implementation for this extension point has been registered.
   *
   * @return {@code true} if an implementation for this extension point has been registered,
   *     otherwise {@code false}
   */
  public boolean hasImplementation() {
    return dynamicItem.getEntry() != null;
  }

  /**
   * Returns the name of the plugin that registered the extension.
   *
   * @return the plugin name, {@code null} if no implementation is registered for this extension
   *     point
   */
  @Nullable
  public String getPluginName() {
    return dynamicItem.getPluginName();
  }

  /**
   * Invokes the plugin extension of the item. All exceptions from the plugin extension are caught
   * and logged.
   *
   * <p>The consumer gets the extension implementation provided that should be invoked.
   *
   * <p>No-op if no implementation is registered for this extension point.
   *
   * @param extensionImplConsumer consumer that invokes the extension
   */
  public void run(ExtensionImplConsumer<T> extensionImplConsumer) {
    Extension<T> extension = dynamicItem.getEntry();
    if (extension == null) {
      return;
    }
    PluginContext.runLogExceptions(pluginMetrics, extension, extensionImplConsumer);
  }

  /**
   * Invokes the plugin extension of the item. All exceptions from the plugin extension are caught
   * and logged.
   *
   * <p>The consumer gets the extension implementation provided that should be invoked.
   *
   * <p>No-op if no implementation is registered for this extension point.
   *
   * @param extensionImplConsumer consumer that invokes the extension
   * @param exceptionClass type of the exceptions that should be thrown
   * @throws X expected exception from the plugin extension
   */
  public <X extends Exception> void run(
      ExtensionImplConsumer<T> extensionImplConsumer, Class<X> exceptionClass) throws X {
    Extension<T> extension = dynamicItem.getEntry();
    if (extension == null) {
      return;
    }
    PluginContext.runLogExceptions(pluginMetrics, extension, extensionImplConsumer, exceptionClass);
  }

  /**
   * Calls the plugin extension of the item and returns the result from the plugin extension call.
   *
   * <p>The function gets the extension implementation provided that should be invoked.
   *
   * <p>Fails with {@link IllegalStateException} if no implementation is registered for the item.
   *
   * @param extensionImplFunction function that invokes the extension
   * @return the result from the plugin extension
   * @throws IllegalStateException if no implementation is registered for the item
   */
  public <R> R call(ExtensionImplFunction<T, R> extensionImplFunction) {
    Extension<T> extension = dynamicItem.getEntry();
    checkState(extension != null);
    return PluginContext.call(pluginMetrics, extension, extensionImplFunction);
  }

  /**
   * Calls the plugin extension of the item and returns the result from the plugin extension call.
   * Exceptions of the specified type are thrown and must be handled by the caller.
   *
   * <p>The function gets the extension implementation provided that should be invoked.
   *
   * <p>Fails with {@link IllegalStateException} if no implementation is registered for the item.
   *
   * @param checkedExtensionImplFunction function that invokes the extension
   * @param exceptionClass type of the exceptions that should be thrown
   * @return the result from the plugin extension
   * @throws X expected exception from the plugin extension
   * @throws IllegalStateException if no implementation is registered for the item
   */
  public <R, X extends Exception> R call(
      CheckedExtensionImplFunction<T, R, X> checkedExtensionImplFunction, Class<X> exceptionClass)
      throws X {
    Extension<T> extension = dynamicItem.getEntry();
    checkState(extension != null);
    return PluginContext.call(
        pluginMetrics, extension, checkedExtensionImplFunction, exceptionClass);
  }
}
