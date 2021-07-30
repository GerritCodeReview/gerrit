// Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.server.plugincontext.PluginContext.ExtensionImplConsumer;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;
import com.google.inject.Inject;

/**
 * Context to invoke extensions from a {@link DynamicSet} that implement {@link AutoCloseable}.
 *
 * <p>The extensions must be opened / called by {@link #openEach(ExtensionImplConsumer)}, the
 * returned {@link ExtensionCallContext} that is an {@link AutoCloseable} ensures that the {@code
 * close()} method is invoked for each of the extensions.
 */
public class AutoCloseablePluginSetContext<T extends AutoCloseable> extends PluginSetContext<T> {
  @Inject
  public AutoCloseablePluginSetContext(DynamicSet<T> dynamicSet, PluginMetrics pluginMetrics) {
    super(dynamicSet, pluginMetrics);
  }

  /**
   * Not supported for extensions that implement {@link AutoCloseable}. Use {@link
   * #openEach(ExtensionImplConsumer)} instead.
   */
  @Override
  public void runEach(ExtensionImplConsumer<T> extensionImplConsumer) {
    throw new UnsupportedOperationException();
  }

  /**
   * Not supported for extensions that implement {@link AutoCloseable}. Use {@link
   * #openEach(ExtensionImplConsumer)} instead.
   */
  @Override
  public <X extends Exception> void runEach(
      ExtensionImplConsumer<T> extensionImplConsumer, Class<X> exceptionClass) throws X {
    throw new UnsupportedOperationException();
  }

  /**
   * Opens all extensions and returns an {@link AutoCloseable} that ensures closing them.
   *
   * @param extensionImplConsumer consumer that invokes the extension
   * @return {@link AutoCloseable} that closes all opened extension on close
   */
  public ExtensionCallContext openEach(ExtensionImplConsumer<T> extensionImplConsumer) {
    ImmutableList<Extension<T>> entries = ImmutableList.copyOf(dynamicSet.entries());
    ImmutableList.Builder<ExtensionCallContext> extensionCallContexts = ImmutableList.builder();
    entries.forEach(
        extension -> {
          // extension.get() creates a new instance of the extension (unless the extension point
          // implementation is bound as a singleton). We must ensure that we call the close() method
          // on this instance when the ExtensionCallContext is closed (and not on a new instance).
          T extensionImpl = extension.get();
          PluginContext.runLogExceptions(
              pluginMetrics,
              extension.getPluginName(),
              extensionImpl,
              extension.getExportName(),
              extensionImplConsumer);
          extensionCallContexts.add(
              () ->
                  PluginContext.runLogExceptions(
                      pluginMetrics,
                      extension.getPluginName(),
                      extensionImpl,
                      extension.getExportName(),
                      t -> t.close()));
        });
    return () -> extensionCallContexts.build().forEach(ExtensionCallContext::close);
  }

  /** Context the close opened extensions. */
  @FunctionalInterface
  public interface ExtensionCallContext extends AutoCloseable {
    @Override
    public void close();
  }
}
