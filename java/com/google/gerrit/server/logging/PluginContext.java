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

package com.google.gerrit.server.logging;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Throwables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.PluginEntry;

/**
 * Context for invoking plugins.
 *
 * <p>Invoking a plugin through a PluginContext sets a logging tag with the plugin name is set. This
 * way any errors that are triggered by the plugin (even if they happen in Gerrit code which is
 * called by the plugin) can be easily attributed to the plugin.
 *
 * <p>If possible plugins should be invoked through:
 *
 * <ul>
 *   <li>{@link PluginItemContext} for {@link DynamicItem} plugins
 *   <li>{@link PluginSetContext} for {@link DynamicSet} plugins
 *   <li>{@link PluginMapContext} for {@link DynamicMap} plugins
 * </ul>
 *
 * <p>A plugin context can be manually opened by invoking the newTrace methods. This should only be
 * needed if an extension point throws multiple exceptions that need to be handled:
 *
 * <pre>
 * for (PluginEntry<Foo> pluginEntry : fooDynamicMap) {
 *   try (TraceContext traceContext = PluginContext.newTrace(pluginEntry)) {
 *     pluginEntry.get().doFoo();
 *   }
 * }
 * </pre>
 *
 * <p>This class hosts static methods with generic functionality to invoke plugins with a trace
 * context that are commonly used by {@link PluginItemContext}, {@link PluginSetContext} and {@link
 * PluginMapContext}.
 *
 * <p>The run* methods execute an extension point but don't deliver a result back to the caller.
 * Exceptions can be caught and logged.
 *
 * <p>The call* methods execute an extension point and deliver a result back to the caller.
 */
public class PluginContext<T> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @FunctionalInterface
  public interface PluginConsumer<T> {
    void run(T t) throws Exception;
  }

  @FunctionalInterface
  public interface PluginFunction<T, R> {
    R call(T input);
  }

  @FunctionalInterface
  public interface PluginFunctionAllowingException<T, R, X extends Exception> {
    R call(T input) throws X;
  }

  @FunctionalInterface
  public interface PluginEntryConsumer<T> {
    void run(PluginEntry<T> pluginEntry) throws Exception;
  }

  @FunctionalInterface
  public interface PluginEntryFunction<T, R> {
    R call(PluginEntry<T> pluginEntry);
  }

  @FunctionalInterface
  public interface PluginEntryFunctionAllowingException<T, R, X extends Exception> {
    R call(PluginEntry<T> pluginEntry) throws X;
  }

  /**
   * Opens a new trace context for invoking a plugin.
   *
   * @param dynamicItem dynamic item that holds the extension point implementation that is being
   *     invoked from within the trace context
   * @return the created trace context
   */
  public static <T> TraceContext newTrace(DynamicItem<T> dynamicItem) {
    PluginEntry<T> pluginEntry = dynamicItem.getEntry();
    if (pluginEntry == null) {
      return TraceContext.open();
    }
    return newTrace(pluginEntry);
  }

  /**
   * Opens a new trace context for invoking a plugin.
   *
   * @param pluginEntry plugin entry that holds the extension point implementation that is being
   *     invoked from within the trace context
   * @return the created trace context
   */
  public static <T> TraceContext newTrace(PluginEntry<T> pluginEntry) {
    return TraceContext.open().addPluginTag(checkNotNull(pluginEntry).getLoggableName());
  }

  /**
   * Runs a plugin. All exceptions from the plugin are caught and logged.
   *
   * <p>The consumer gets the extension point provided that should be invoked.
   *
   * @param pluginEntry plugin entry that holds the extension point implementation that is being
   *     invoked
   * @param pluginConsumer the consumer that invokes the extension point
   */
  static <T> void runLogExceptions(PluginEntry<T> pluginEntry, PluginConsumer<T> pluginConsumer) {
    T extensionPoint = pluginEntry.get();
    if (extensionPoint == null) {
      return;
    }

    try (TraceContext traceContext = newTrace(pluginEntry)) {
      pluginConsumer.run(extensionPoint);
    } catch (Throwable e) {
      logger.atWarning().withCause(e).log(
          "Failure in %s of plugin %s", extensionPoint.getClass(), pluginEntry.getLoggableName());
    }
  }

  /**
   * Runs a plugin. All exceptions from the plugin are caught and logged.
   *
   * <p>The consumer get the {@link PluginEntry} provided that should be invoked. The plugin entry
   * provides access to the plugin name and the export name.
   *
   * @param pluginEntry plugin entry that holds the extension point implementation that is being
   *     invoked
   * @param pluginConsumer the consumer that invokes the extension point
   */
  static <T> void runLogExceptions(
      PluginEntry<T> pluginEntry, PluginEntryConsumer<T> pluginConsumer) {
    T extensionPoint = pluginEntry.get();
    if (extensionPoint == null) {
      return;
    }

    try (TraceContext traceContext = newTrace(pluginEntry)) {
      pluginConsumer.run(pluginEntry);
    } catch (Throwable e) {
      logger.atWarning().withCause(e).log(
          "Failure in %s of plugin %s", extensionPoint.getClass(), pluginEntry.getLoggableName());
    }
  }

  /**
   * Runs a plugin. All exceptions from the plugin except exceptions of the specified type are
   * caught and logged. Exceptions of the specified type are thrown and must be handled by the
   * caller.
   *
   * <p>The consumer gets the extension point provided that should be invoked.
   *
   * @param pluginEntry plugin entry that holds the extension point implementation that is being
   *     invoked
   * @param pluginConsumer the consumer that invokes the extension point
   * @param exceptionClass type of the exceptions that should be thrown
   */
  static <T, X extends Exception> void runLogExceptions(
      PluginEntry<T> pluginEntry, PluginConsumer<T> pluginConsumer, Class<X> exceptionClass)
      throws X {
    T extensionPoint = pluginEntry.get();
    if (extensionPoint == null) {
      return;
    }

    try (TraceContext traceContext = newTrace(pluginEntry)) {
      pluginConsumer.run(extensionPoint);
    } catch (Throwable e) {
      Throwables.throwIfInstanceOf(e, exceptionClass);
      logger.atWarning().withCause(e).log(
          "Failure in %s of plugin invoke%s",
          extensionPoint.getClass(), pluginEntry.getLoggableName());
    }
  }

  /**
   * Runs a plugin. All exceptions from the plugin except exceptions of the specified type are
   * caught and logged. Exceptions of the specified type are thrown and must be handled by the
   * caller.
   *
   * <p>The consumer get the {@link PluginEntry} provided that should be invoked. The plugin entry
   * provides access to the plugin name and the export name.
   *
   * @param pluginEntry plugin entry that holds the extension point implementation that is being
   *     invoked
   * @param pluginConsumer the consumer that invokes the extension point
   * @param exceptionClass type of the exceptions that should be thrown
   */
  static <T, X extends Exception> void runLogExceptions(
      PluginEntry<T> pluginEntry, PluginEntryConsumer<T> pluginConsumer, Class<X> exceptionClass)
      throws X {
    T extensionPoint = pluginEntry.get();
    if (extensionPoint == null) {
      return;
    }

    try (TraceContext traceContext = newTrace(pluginEntry)) {
      pluginConsumer.run(pluginEntry);
    } catch (Throwable e) {
      Throwables.throwIfInstanceOf(e, exceptionClass);
      logger.atWarning().withCause(e).log(
          "Failure in %s of plugin %s", extensionPoint.getClass(), pluginEntry.getLoggableName());
    }
  }

  /**
   * Calls a plugin and returns the result from the plugin call.
   *
   * <p>The function gets the extension point provided that should be invoked.
   *
   * @param pluginEntry plugin entry that holds the extension point implementation that is being
   *     invoked
   * @param function function that invokes the extension point
   * @return the result from the plugin
   */
  static <T, R> R call(PluginEntry<T> pluginEntry, PluginFunction<T, R> function) {
    try (TraceContext traceContext = newTrace(pluginEntry)) {
      return function.call(pluginEntry.get());
    }
  }

  /**
   * Calls a plugin and returns the result from the plugin call. Exceptions of the specified type
   * are thrown and must be handled by the caller.
   *
   * <p>The function gets the extension point provided that should be invoked.
   *
   * @param pluginEntry plugin entry that holds the extension point implementation that is being
   *     invoked
   * @param function function that invokes the extension point
   * @param exceptionClass type of the exceptions that should be thrown
   * @return the result from the plugin
   */
  static <T, R, X extends Exception> R call(
      PluginEntry<T> pluginEntry,
      PluginFunctionAllowingException<T, R, X> function,
      Class<X> exceptionClass)
      throws X {
    try (TraceContext traceContext = newTrace(pluginEntry)) {
      try {
        return function.call(pluginEntry.get());
      } catch (Exception e) {
        // The only exception that can be thrown is X, but we cannot catch X since it is a generic
        // type.
        Throwables.throwIfInstanceOf(e, exceptionClass);
        throw new IllegalStateException("unexpected exception: " + e.getMessage(), e);
      }
    }
  }

  /**
   * Calls a plugin and returns the result from the plugin call.
   *
   * <p>The function get the {@link PluginEntry} provided that should be invoked. The plugin entry
   * provides access to the plugin name and the export name.
   *
   * @param pluginEntry plugin entry that holds the extension point implementation that is being
   *     invoked
   * @param function function that invokes the extension point
   * @return the result from the plugin
   */
  static <T, R> R call(PluginEntry<T> pluginEntry, PluginEntryFunction<T, R> function) {
    try (TraceContext traceContext = newTrace(pluginEntry)) {
      return function.call(pluginEntry);
    }
  }

  /**
   * Calls a plugin and returns the result from the plugin call. Exceptions of the specified type
   * are thrown and must be handled by the caller.
   *
   * <p>The function get the {@link PluginEntry} provided that should be invoked. The plugin entry
   * provides access to the plugin name and the export name.
   *
   * @param pluginEntry plugin entry that holds the extension point implementation that is being
   *     invoked
   * @param function function that invokes the extension point
   * @param exceptionClass type of the exceptions that should be thrown
   * @return the result from the plugin
   */
  static <T, R, X extends Exception> R call(
      PluginEntry<T> pluginEntry,
      PluginEntryFunctionAllowingException<T, R, X> function,
      Class<X> exceptionClass)
      throws X {
    try (TraceContext traceContext = newTrace(pluginEntry)) {
      try {
        return function.call(pluginEntry);
      } catch (Exception e) {
        // The only exception that can be thrown is X, but we cannot catch X since it is a generic
        // type.
        Throwables.throwIfInstanceOf(e, exceptionClass);
        throw new IllegalStateException("unexpected exception: " + e.getMessage(), e);
      }
    }
  }
}
