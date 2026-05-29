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

import static java.util.Objects.requireNonNull;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.server.plugincontext.PluginContext.CheckedExtensionFunction;
import com.google.gerrit.server.plugincontext.PluginContext.ExtensionConsumer;
import com.google.gerrit.server.plugincontext.PluginContext.ExtensionFunction;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;

/**
 * Context to invoke an extension from {@link DynamicMap}.
 *
 * <p>When the plugin extension is invoked a logging tag with the plugin name is set. This way any
 * errors that are triggered by the plugin extension (even if they happen in Gerrit code which is
 * called by the plugin extension) can be easily attributed to the plugin.
 *
 * <p>The run* methods execute the extension but don't deliver a result back to the caller.
 * Exceptions can be caught and logged.
 *
 * <p>The call* methods execute the extension and deliver a result back to the caller.
 *
 * <pre>{@code
 * Map<String, Object> results = new HashMap<>();
 * fooPluginMapEntryContext.run(
 *     extension -> results.put(extension.getExportName(), extension.get().getFoo());
 * }</pre>
 *
 * <p>Example if all exceptions, but one, should be caught and logged:
 *
 * <pre>{@code
 * Map<String, Object> results = new HashMap<>();
 * try {
 *   fooPluginMapEntryContext.run(
 *     extension -> results.put(extension.getExportName(), extension.get().getFoo(),
 *     MyException.class);
 * } catch (MyException e) {
 *   // handle the exception
 * }
 * }</pre>
 *
 * <p>Example if return values should be handled:
 *
 * <pre>{@code
 * Object result = fooPluginMapEntryContext.call(extension -> extension.get().getFoo());
 * }</pre>
 *
 * <p>Example if return values and a single exception should be handled:
 *
 * <pre>{@code
 * Object result;
 * try {
 *   result = fooPluginMapEntryContext.call(extension -> extension.get().getFoo(), MyException.class);
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
public class PluginMapEntryContext<T> {
  private final Extension<T> extension;
  private final PluginMetrics pluginMetrics;

  PluginMapEntryContext(Extension<T> extension, PluginMetrics pluginMetrics) {
    requireNonNull(extension);
    requireNonNull(extension.getExportName(), "export name must be set for plugin map entries");
    this.extension = extension;
    this.pluginMetrics = pluginMetrics;
  }

  /**
   * Returns the name of the plugin that registered this map entry.
   *
   * @return the plugin name
   */
  public String getPluginName() {
    return extension.getPluginName();
  }

  /**
   * Returns the export name for which this map entry was registered.
   *
   * @return the export name
   */
  public String getExportName() {
    return extension.getExportName();
  }

  /**
   * Invokes the plugin extension. All exceptions from the plugin extension are caught and logged.
   *
   * <p>The consumer get the {@link Extension} provided that should be invoked. The extension
   * provides access to the plugin name and the export name.
   *
   * @param extensionConsumer consumer that invokes the extension
   */
  public void run(ExtensionConsumer<Extension<T>> extensionConsumer) {
    PluginContext.runLogExceptions(pluginMetrics, extension, extensionConsumer);
  }

  /**
   * Invokes the plugin extension. All exceptions from the plugin extension are caught and logged.
   *
   * <p>The consumer get the {@link Extension} provided that should be invoked. The extension
   * provides access to the plugin name and the export name.
   *
   * @param extensionConsumer consumer that invokes the extension
   * @param exceptionClass type of the exceptions that should be thrown
   * @throws X expected exception from the plugin extension
   */
  public <X extends Exception> void run(
      ExtensionConsumer<Extension<T>> extensionConsumer, Class<X> exceptionClass) throws X {
    PluginContext.runLogExceptions(pluginMetrics, extension, extensionConsumer, exceptionClass);
  }

  /**
   * Calls the plugin extension and returns the result from the plugin extension call.
   *
   * <p>The consumer get the {@link Extension} provided that should be invoked. The extension
   * provides access to the plugin name and the export name.
   *
   * @param extensionFunction function that invokes the extension
   * @return the result from the plugin extension
   */
  public <R> R call(ExtensionFunction<Extension<T>, R> extensionFunction) {
    return PluginContext.call(pluginMetrics, extension, extensionFunction);
  }

  /**
   * Calls the plugin extension and returns the result from the plugin extension call. Exceptions of
   * the specified type are thrown and must be handled by the caller.
   *
   * <p>The consumer get the {@link Extension} provided that should be invoked. The extension
   * provides access to the plugin name and the export name.
   *
   * @param checkedExtensionFunction function that invokes the extension
   * @param exceptionClass type of the exceptions that should be thrown
   * @return the result from the plugin extension
   * @throws X expected exception from the plugin extension
   */
  public <R, X extends Exception> R call(
      CheckedExtensionFunction<Extension<T>, R, X> checkedExtensionFunction,
      Class<X> exceptionClass)
      throws X {
    return PluginContext.call(pluginMetrics, extension, checkedExtensionFunction, exceptionClass);
  }
}
