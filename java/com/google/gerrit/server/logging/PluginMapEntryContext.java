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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PluginEntry;
import com.google.gerrit.server.logging.PluginContext.PluginEntryConsumer;
import com.google.gerrit.server.logging.PluginContext.PluginEntryFunction;
import com.google.gerrit.server.logging.PluginContext.PluginEntryFunctionAllowingException;

/**
 * Context to invoke an entry of {@link DynamicMap} plugins.
 *
 * <p>When the plugin is invoked a logging tag with the plugin name is set. This way any errors that
 * are triggered by the plugin (even if they happen in Gerrit code which is called by the plugin)
 * can be easily attributed to the plugin.
 */
public class PluginMapEntryContext<T> {
  private final PluginEntry<T> pluginEntry;

  PluginMapEntryContext(PluginEntry<T> pluginEntry) {
    checkNotNull(pluginEntry);
    checkNotNull(pluginEntry.getExportName(), "export name must be set for plugin map entries");
    this.pluginEntry = pluginEntry;
  }

  public String getPluginName() {
    return pluginEntry.getPluginName();
  }

  public String getExportName() {
    return pluginEntry.getExportName();
  }

  public void run(PluginEntryConsumer<T> pluginConsumer) {
    PluginContext.runLogExceptions(pluginEntry, pluginConsumer);
  }

  public <X extends Exception> void run(
      PluginEntryConsumer<T> pluginConsumer, Class<X> exceptionClass) throws X {
    PluginContext.runLogExceptions(pluginEntry, pluginConsumer, exceptionClass);
  }

  public <R> R call(PluginEntryFunction<T, R> function) {
    return PluginContext.call(pluginEntry, function);
  }

  public <R, X extends Exception> R call(
      PluginEntryFunctionAllowingException<T, R, X> function, Class<X> exceptionClass) throws X {
    return PluginContext.call(pluginEntry, function, exceptionClass);
  }
}
