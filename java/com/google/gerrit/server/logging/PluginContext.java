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

public class PluginContext {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static <T> TraceContext newTrace(DynamicItem<T> dynamicItem) {
    return newTrace(dynamicItem.getPluginName(), dynamicItem.get());
  }

  public static <T> TraceContext newTrace(DynamicSet.Entry<T> dynamicSetEntry) {
    return newTrace(dynamicSetEntry.getPluginName(), dynamicSetEntry.getProvider().get());
  }

  public static <T> TraceContext newTrace(DynamicMap.Entry<T> dynamicMapEntry) {
    return newTrace(dynamicMapEntry.getPluginName(), dynamicMapEntry.getProvider().get());
  }

  private static <T> TraceContext newTrace(@Nullable String pluginName, T extensionPoint) {
    String pluginNameForTracing = getPluginNameForTracing(pluginName, extensionPoint);
    return TraceContext.open().addTag("PLUGIN", pluginNameForTracing);
  }

  public static <T> void invokeIgnoreExceptions(DynamicItem<T> dynamicItem, PluginConsumer<T> c) {
    tracePluginIgnoreExceptions(dynamicItem.getPluginName(), dynamicItem.get(), c);
  }

  public static <T> void invokeIgnoreExceptions(DynamicSet<T> dynamicSet, PluginConsumer<T> c) {
    dynamicSet
        .entries()
        .forEach(
            entry ->
                tracePluginIgnoreExceptions(entry.getPluginName(), entry.getProvider().get(), c));
  }

  public static <T> void invokeIgnoreExceptions(DynamicMap<T> dynamicMap, PluginConsumer<T> c) {
    dynamicMap
        .iterator()
        .forEachRemaining(
            entry ->
                tracePluginIgnoreExceptions(entry.getPluginName(), entry.getProvider().get(), c));
  }

  private static <T> void tracePluginIgnoreExceptions(
      @Nullable String pluginName, T extensionPoint, PluginConsumer<T> c) {
    String pluginNameForTracing = getPluginNameForTracing(pluginName, extensionPoint);
    try (TraceContext traceContext = TraceContext.open().addTag("PLUGIN", pluginNameForTracing)) {
      c.invoke(extensionPoint);
    } catch (Throwable e) {
      logger.atWarning().withCause(e).log(
          "Failure in %s of plugin %s", extensionPoint.getClass(), pluginNameForTracing);
    }
  }

  public static <T, R> R invoke(DynamicItem<T> dynamicItem, Function<T, R> function) {
    return tracePlugin(dynamicItem.getPluginName(), dynamicItem.get(), function);
  }

  public static <T, R> R invoke(DynamicSet.Entry<T> dynamicSetEntry, Function<T, R> function) {
    return tracePlugin(
        dynamicSetEntry.getPluginName(), dynamicSetEntry.getProvider().get(), function);
  }

  public static <T, R> R invoke(DynamicMap.Entry<T> dynamicMapEntry, Function<T, R> function) {
    return tracePlugin(
        dynamicMapEntry.getPluginName(), dynamicMapEntry.getProvider().get(), function);
  }

  private static <T, R> R tracePlugin(
      @Nullable String pluginName, T extensionPoint, Function<T, R> function) {
    String pluginNameForTracing = getPluginNameForTracing(pluginName, extensionPoint);
    try (TraceContext traceContext = TraceContext.open().addTag("PLUGIN", pluginNameForTracing)) {
      return function.apply(extensionPoint);
    }
  }

  private static <T> String getPluginNameForTracing(String pluginName, T extensionPoint) {
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
