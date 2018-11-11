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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.server.plugincontext.PluginContext.CheckedExtensionImplFunction;
import com.google.gerrit.server.plugincontext.PluginContext.ExtensionImplConsumer;
import com.google.gerrit.server.plugincontext.PluginContext.ExtensionImplFunction;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;

/**
 * Context to invoke an extension from {@link DynamicSet}.
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
 * <p>Example if all exceptions should be caught and logged:
 *
 * <pre>
 * fooPluginSetEntryContext.run(foo -> foo.doFoo());
 * </pre>
 *
 * <p>Example if all exceptions, but one, should be caught and logged:
 *
 * <pre>
 * try {
 *   fooPluginSetEntryContext.run(foo -> foo.doFoo(), MyException.class);
 * } catch (MyException e) {
 *   // handle the exception
 * }
 * </pre>
 *
 * <p>Example if return values should be handled:
 *
 * <pre>
 * Object result = fooPluginSetEntryContext.call(foo -> foo.getFoo());
 * </pre>
 *
 * <p>Example if return values and a single exception should be handled:
 *
 * <pre>
 * Object result;
 * try {
 *   result = fooPluginSetEntryContext.call(foo -> foo.getFoo(), MyException.class);
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
public class PluginSetEntryContext<T> {
  private final Extension<T> extension;
  private final PluginMetrics pluginMetrics;

  PluginSetEntryContext(Extension<T> extension, PluginMetrics pluginMetrics) {
    this.extension = requireNonNull(extension);
    this.pluginMetrics = pluginMetrics;
  }

  /**
   * Returns the name of the plugin that registered this extension.
   *
   * @return the plugin name
   */
  public String getPluginName() {
    return extension.getPluginName();
  }

  /**
   * Returns the implementation of this extension.
   *
   * <p>Should only be used in exceptional cases to get direct access to the extension
   * implementation. If possible the extension should be invoked through {@link
   * #run(PluginContext.ExtensionImplConsumer)}, {@link #run(PluginContext.ExtensionImplConsumer,
   * java.lang.Class)}, {@link #call(PluginContext.ExtensionImplFunction)} and {@link
   * #call(PluginContext.CheckedExtensionImplFunction, java.lang.Class)}.
   *
   * @return the implementation of this extension
   */
  public T get() {
    return extension.get();
  }

  /**
   * Invokes the plugin extension. All exceptions from the plugin extension are caught and logged.
   *
   * <p>The consumer gets the extension implementation provided that should be invoked.
   *
   * @param extensionImplConsumer consumer that invokes the extension
   */
  public void run(ExtensionImplConsumer<T> extensionImplConsumer) {
    PluginContext.runLogExceptions(pluginMetrics, extension, extensionImplConsumer);
  }

  /**
   * Invokes the plugin extension. All exceptions from the plugin extension are caught and logged.
   *
   * <p>The consumer gets the extension implementation provided that should be invoked.
   *
   * @param extensionImplConsumer consumer that invokes the extension
   * @param exceptionClass type of the exceptions that should be thrown
   * @throws X expected exception from the plugin extension
   */
  public <X extends Exception> void run(
      ExtensionImplConsumer<T> extensionImplConsumer, Class<X> exceptionClass) throws X {
    PluginContext.runLogExceptions(pluginMetrics, extension, extensionImplConsumer, exceptionClass);
  }

  /**
   * Calls the plugin extension and returns the result from the plugin extension call.
   *
   * <p>The function gets the extension point provided that should be invoked.
   *
   * @param extensionImplFunction function that invokes the extension
   * @return the result from the plugin extension
   */
  public <R> R call(ExtensionImplFunction<T, R> extensionImplFunction) {
    return PluginContext.call(pluginMetrics, extension, extensionImplFunction);
  }

  /**
   * Calls the plugin extension and returns the result from the plugin extension call. Exceptions of
   * the specified type are thrown and must be handled by the caller.
   *
   * <p>The function gets the extension implementation provided that should be invoked.
   *
   * @param checkedExtensionImplFunction function that invokes the extension
   * @param exceptionClass type of the exceptions that should be thrown
   * @return the result from the plugin extension
   * @throws X expected exception from the plugin extension
   */
  public <R, X extends Exception> R call(
      CheckedExtensionImplFunction<T, R, X> checkedExtensionImplFunction, Class<X> exceptionClass)
      throws X {
    return PluginContext.call(
        pluginMetrics, extension, checkedExtensionImplFunction, exceptionClass);
  }
}
