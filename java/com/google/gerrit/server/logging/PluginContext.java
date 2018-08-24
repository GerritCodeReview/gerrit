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

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;

/**
 * Context that sets a logging tag with the plugin name for invoking a plugin extension point.
 *
 * <p>This way any errors that are triggered by the plugin (even if they happen in Gerrit code which
 * is called by the plugin) can be easily attributed to the plugin.
 */
public class PluginContext {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Opens a new trace context for invoking a plugin.
   *
   * <p>While the trace context is open a logging tag with the plugin name is set which is included
   * into all logs that a triggered from the trace context. While the trace context is open only the
   * provided extension point should be invoked. This way any errors that are triggered by the
   * plugin (even if they happen in Gerrit code which is called by the plugin) can be easily
   * attributed to the plugin.
   *
   * <p>Using this method it's possible to invoke a extension point that throws exceptions that need
   * to be handled or which should be handled somewhere else.
   *
   * @param dynamicItem dynamic item that holds the extension point implementation that is being
   *     invoked from within the trace context
   * @return the created trace context
   */
  public static <T> TraceContext newTrace(DynamicItem<T> dynamicItem) {
    return newTrace(dynamicItem.getPluginName(), dynamicItem.get());
  }

  /**
   * Opens a new trace context for invoking a plugin.
   *
   * <p>While the trace context is open a logging tag with the plugin name is set which is included
   * into all logs that a triggered from the trace context. While the trace context is open only the
   * provided extension point should be invoked. This way any errors that are triggered by the
   * plugin (even if they happen in Gerrit code which is called by the plugin) can be easily
   * attributed to the plugin.
   *
   * <p>Using this method it's possible to invoke a extension point that throws exceptions that need
   * to be handled or which should be handled somewhere else.
   *
   * @param dynamicSetEntry dynamic set entry that holds the extension point implementation that is
   *     being invoked from within the trace context
   * @return the created trace context
   */
  public static <T> TraceContext newTrace(DynamicSet.Entry<T> dynamicSetEntry) {
    return newTrace(dynamicSetEntry.getPluginName(), dynamicSetEntry.getProvider().get());
  }

  /**
   * Opens a new trace context for invoking a plugin.
   *
   * <p>While the trace context is open a logging tag with the plugin name is set which is included
   * into all logs that a triggered from the trace context. While the trace context is open only the
   * provided extension point should be invoked. This way any errors that are triggered by the
   * plugin (even if they happen in Gerrit code which is called by the plugin) can be easily
   * attributed to the plugin.
   *
   * <p>Using this method it's possible to invoke a extension point that throws exceptions that need
   * to be handled or which should be handled somewhere else.
   *
   * @param dynamicMapEntry dynamic map entry that holds the extension point implementation that is
   *     being invoked from within the trace context
   * @return the created trace context
   */
  public static <T> TraceContext newTrace(DynamicMap.Entry<T> dynamicMapEntry) {
    return newTrace(dynamicMapEntry.getPluginName(), dynamicMapEntry.getProvider().get());
  }

  private static <T> TraceContext newTrace(@Nullable String pluginName, T extensionPoint) {
    String logablePluginName = getLoggablePluginName(pluginName, extensionPoint);
    return TraceContext.open().addTag("PLUGIN", logablePluginName);
  }

  /**
   * Invokes an extension point, ignoring any exception that occurs during the invocation of the
   * extension point.
   *
   * <p>While the plugin is being invoked a logging tag with the plugin name is set which is
   * included into all logs that a triggered by the plugin execution. This way any errors that are
   * triggered by the plugin (even if they happen in Gerrit code which is called by the plugin) can
   * be easily attributed to the plugin.
   *
   * <p>Any exception that occurs during the plugin invocation is logged as a warning. The log entry
   * contains the plugin name so that the error can be easily attributed to the plugin.
   *
   * @param dynamicItem dynamic item that holds the extension point implementation
   * @param consumer consumer that invokes the extension point
   */
  public static <T> void invokeIgnoreExceptions(
      DynamicItem<T> dynamicItem, PluginConsumer<T> consumer) {
    tracePluginIgnoreExceptions(dynamicItem.getPluginName(), dynamicItem.get(), consumer);
  }

  /**
   * Invokes an extension point, ignoring any exception that occurs during the invocation of the
   * extension point.
   *
   * <p>While the plugin is being invoked a logging tag with the plugin name is set which is
   * included into all logs that a triggered by the plugin execution. This way any errors that are
   * triggered by the plugin (even if they happen in Gerrit code which is called by the plugin) can
   * be easily attributed to the plugin.
   *
   * <p>Any exception that occurs during the plugin invocation is logged as a warning. The log entry
   * contains the plugin name so that the error can be easily attributed to the plugin.
   *
   * @param dynamicSet dynamic set that holds the extension point implementations
   * @param consumer consumer that invokes the extension point
   */
  public static <T> void invokeIgnoreExceptions(
      DynamicSet<T> dynamicSet, PluginConsumer<T> consumer) {
    dynamicSet
        .entries()
        .forEach(
            entry ->
                tracePluginIgnoreExceptions(
                    entry.getPluginName(), entry.getProvider().get(), consumer));
  }

  /**
   * Invokes an extension point, ignoring any exception that occurs during the invocation of the
   * extension point.
   *
   * <p>While the plugin is being invoked a logging tag with the plugin name is set which is
   * included into all logs that a triggered by the plugin execution. This way any errors that are
   * triggered by the plugin (even if they happen in Gerrit code which is called by the plugin) can
   * be easily attributed to the plugin.
   *
   * <p>Any exception that occurs during the plugin invocation is logged as a warning. The log entry
   * contains the plugin name so that the error can be easily attributed to the plugin.
   *
   * @param dynamicMap dynamic map that holds the extension point implementations
   * @param consumer consumer that invokes the extension point
   */
  public static <T> void invokeIgnoreExceptions(
      DynamicMap<T> dynamicMap, PluginConsumer<T> consumer) {
    dynamicMap
        .iterator()
        .forEachRemaining(
            entry ->
                tracePluginIgnoreExceptions(
                    entry.getPluginName(), entry.getProvider().get(), consumer));
  }

  private static <T> void tracePluginIgnoreExceptions(
      @Nullable String pluginName, T extensionPoint, PluginConsumer<T> c) {
    String loggablePluginName = getLoggablePluginName(pluginName, extensionPoint);
    try (TraceContext traceContext = TraceContext.open().addTag("PLUGIN", loggablePluginName)) {
      c.invoke(extensionPoint);
    } catch (Throwable e) {
      logger.atWarning().withCause(e).log(
          "Failure in %s of plugin %s", extensionPoint.getClass(), loggablePluginName);
    }
  }

  /**
   * Invokes an extension point and returns the result of invoking it.
   *
   * <p>While the plugin is being invoked a logging tag with the plugin name is set which is
   * included into all logs that a triggered by the plugin execution. This way any errors that are
   * triggered by the plugin (even if they happen in Gerrit code which is called by the plugin) can
   * be easily attributed to the plugin.
   *
   * @param dynamicItem dynamic item that holds the extension point implementation
   * @param function function that invokes the extension point and returns its result
   * @return the result of invoking the extension point
   */
  public static <T, R> R invoke(DynamicItem<T> dynamicItem, Function<T, R> function) {
    return tracePlugin(dynamicItem.getPluginName(), dynamicItem.get(), function);
  }

  /**
   * Invokes an extension point and returns the result of invoking it.
   *
   * <p>While the plugin is being invoked a logging tag with the plugin name is set which is
   * included into all logs that a triggered by the plugin execution. This way any errors that are
   * triggered by the plugin (even if they happen in Gerrit code which is called by the plugin) can
   * be easily attributed to the plugin.
   *
   * @param dynamicSetEntry dynamic set entry that holds the extension point implementation
   * @param function function that invokes the extension point and returns its result
   * @return the result of invoking the extension point
   */
  public static <T, R> R invoke(DynamicSet.Entry<T> dynamicSetEntry, Function<T, R> function) {
    return tracePlugin(
        dynamicSetEntry.getPluginName(), dynamicSetEntry.getProvider().get(), function);
  }

  /**
   * Invokes an extension point and returns the result of invoking it.
   *
   * <p>While the plugin is being invoked a logging tag with the plugin name is set which is
   * included into all logs that a triggered by the plugin execution. This way any errors that are
   * triggered by the plugin (even if they happen in Gerrit code which is called by the plugin) can
   * be easily attributed to the plugin.
   *
   * @param dynamicMapEntry dynamic map entry that holds the extension point implementation
   * @param function function that invokes the extension point and returns its result
   * @return the result of invoking the extension point
   */
  public static <T, R> R invoke(DynamicMap.Entry<T> dynamicMapEntry, Function<T, R> function) {
    return tracePlugin(
        dynamicMapEntry.getPluginName(), dynamicMapEntry.getProvider().get(), function);
  }

  private static <T, R> R tracePlugin(
      @Nullable String pluginName, T extensionPoint, Function<T, R> function) {
    String loggablePluginName = getLoggablePluginName(pluginName, extensionPoint);
    try (TraceContext traceContext = TraceContext.open().addTag("PLUGIN", loggablePluginName)) {
      return function.apply(extensionPoint);
    }
  }

  /**
   * Returns a plugin name that can be included into logs.
   *
   * <p>If the provided plugin name is non-null it is returned unmodified.
   *
   * <p>If the provided plugin name is {@code null} this method tries to guess the plugin name from
   * the package name of the provided extension point implementation and returns it as
   * "<plugin-name> (guessed)".
   *
   * <p>If a plugin name could not be guessed "n/a" is returned.
   *
   * @param pluginName the plugin name, may be {@code null}
   * @param extensionPoint the extension point implementation that should be invoked
   * @return plugin name that can be included into logs, guaranteed to be non-null, but may be "n/a"
   *     if a plugin name cannot be found
   */
  private static <T> String getLoggablePluginName(@Nullable String pluginName, T extensionPoint) {
    if (pluginName != null) {
      return pluginName;
    }

    // Try to guess plugin name from package name.
    // For most plugins the package name contains the plugin name, e.g.:
    //   com.googlesource.gerrit.plugins.<pluginName>.foo.bar
    // Use the part of the package that follows 'plugins' as plugin name.
    boolean foundPluginsPackage = false;
    for (String part : Splitter.on('.').split(extensionPoint.getClass().getName())) {
      if (foundPluginsPackage) {
        return String.format("%s (guessed)", part);
      }
      if (part.equals("plugins")) {
        foundPluginsPackage = true;
      }
    }

    return "n/a";
  }

  public interface PluginConsumer<T> {
    void invoke(T t) throws Throwable;
  }
}
