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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PluginEntry;
import com.google.gerrit.server.plugincontext.PluginContext.PluginEntryConsumer;
import com.google.gerrit.server.plugincontext.PluginContext.PluginEntryFunction;
import com.google.gerrit.server.plugincontext.PluginContext.PluginEntryFunctionAllowingException;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;

/**
 * Context to invoke an entry of {@link DynamicMap} plugins.
 *
 * <p>When the plugin is invoked a logging tag with the plugin name is set. This way any errors that
 * are triggered by the plugin (even if they happen in Gerrit code which is called by the plugin)
 * can be easily attributed to the plugin.
 *
 * <p>The run* methods execute an extension point but don't deliver a result back to the caller.
 * Exceptions can be caught and logged.
 *
 * <p>The call* methods execute an extension point and deliver a result back to the caller.
 *
 * <pre>
 * Map<String, Object> results = new HashMap<>();
 * fooPluginMapEntryContext.run(
 *     pluginEntry -> results.put(pluginEntry.getExportName(), pluginEntry.get().getFoo());
 * </pre>
 *
 * <p>Example if all exceptions, but one, should be caught and logged:
 *
 * <pre>
 * Map<String, Object> results = new HashMap<>();
 * try {
 *   fooPluginMapEntryContext.run(
 *     pluginEntry -> results.put(pluginEntry.getExportName(), pluginEntry.get().getFoo(),
 *     MyException.class);
 * } catch (MyException e) {
 *   // handle the exception
 * }
 * </pre>
 *
 * <p>Example if return values should be handled:
 *
 * <pre>
 * Object result = fooPluginMapEntryContext.call(pluginEntry -> pluginEntry.get().getFoo());
 * </pre>
 *
 * <p>Example if return values and a single exception should be handled:
 *
 * <pre>
 * Object result;
 * try {
 *   result = fooPluginMapEntryContext.call(pluginEntry -> pluginEntry.get().getFoo(), MyException.class);
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
public class PluginMapEntryContext<T> {
  private final PluginEntry<T> pluginEntry;
  private final PluginMetrics pluginMetrics;

  PluginMapEntryContext(PluginEntry<T> pluginEntry, PluginMetrics pluginMetrics) {
    checkNotNull(pluginEntry);
    checkNotNull(pluginEntry.getExportName(), "export name must be set for plugin map entries");
    this.pluginEntry = pluginEntry;
    this.pluginMetrics = pluginMetrics;
  }

  /**
   * Returns the name of the plugin that registered this map entry.
   *
   * @return the plugin name
   */
  public String getPluginName() {
    return pluginEntry.getPluginName();
  }

  /**
   * Returns the export name for which this map entry was registered.
   *
   * @return the export name
   */
  public String getExportName() {
    return pluginEntry.getExportName();
  }

  /**
   * Invokes the entry. All exceptions from the plugin are caught and logged.
   *
   * <p>The consumer get the {@link PluginEntry} provided that should be invoked. The plugin entry
   * provides access to the plugin name and the export name.
   *
   * @param pluginConsumer consumer that invokes the extension point
   */
  public void run(PluginEntryConsumer<T> pluginConsumer) {
    PluginContext.runLogExceptions(pluginMetrics, pluginEntry, pluginConsumer);
  }

  /**
   * Invokes the entry. All exceptions from the plugin are caught and logged.
   *
   * <p>The consumer get the {@link PluginEntry} provided that should be invoked. The plugin entry
   * provides access to the plugin name and the export name.
   *
   * @param pluginConsumer consumer that invokes the extension point
   * @param exceptionClass type of the exceptions that should be thrown
   * @throws X expected exception from the plugin
   */
  public <X extends Exception> void run(
      PluginEntryConsumer<T> pluginConsumer, Class<X> exceptionClass) throws X {
    PluginContext.runLogExceptions(pluginMetrics, pluginEntry, pluginConsumer, exceptionClass);
  }

  /**
   * Calls the entry and returns the result from the plugin call.
   *
   * <p>The consumer get the {@link PluginEntry} provided that should be invoked. The plugin entry
   * provides access to the plugin name and the export name.
   *
   * @param function function that invokes the extension point
   * @return the result from the plugin
   */
  public <R> R call(PluginEntryFunction<T, R> function) {
    return PluginContext.call(pluginEntry, function);
  }

  /**
   * Calls the entry and returns the result from the plugin call. Exceptions of the specified type
   * are thrown and must be handled by the caller.
   *
   * <p>The consumer get the {@link PluginEntry} provided that should be invoked. The plugin entry
   * provides access to the plugin name and the export name.
   *
   * @param function function that invokes the extension point
   * @param exceptionClass type of the exceptions that should be thrown
   * @return the result from the plugin
   * @throws X expected exception from the plugin
   */
  public <R, X extends Exception> R call(
      PluginEntryFunctionAllowingException<T, R, X> function, Class<X> exceptionClass) throws X {
    return PluginContext.call(pluginEntry, function, exceptionClass);
  }
}
