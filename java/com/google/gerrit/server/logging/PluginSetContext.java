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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.PluginEntry;
import com.google.gerrit.server.logging.PluginContext.PluginConsumer;
import com.google.inject.Inject;
import java.util.Iterator;

/**
 * Context to invoke {@link DynamicSet} plugins.
 *
 * <p>When the plugin is invoked a logging tag with the plugin name is set. This way any errors that
 * are triggered by the plugin (even if they happen in Gerrit code which is called by the plugin)
 * can be easily attributed to the plugin.
 */
public class PluginSetContext<T> implements Iterable<PluginSetEntryContext<T>> {
  private final DynamicSet<T> dynamicSet;

  @Inject
  public PluginSetContext(DynamicSet<T> dynamicSet) {
    this.dynamicSet = dynamicSet;
  }

  /**
   * Iterator that provides contexts for invoking the entries of this set.
   *
   * <p>This is useful if:
   *
   * <ul>
   *   <li>invoking of each entry returns a result that should be handled
   *   <li>a sequence of invocations should be done on each entry
   * </ul>
   */
  @Override
  public Iterator<PluginSetEntryContext<T>> iterator() {
    Iterator<PluginEntry<T>> pluginIterator = dynamicSet.entries().iterator();
    return new Iterator<PluginSetEntryContext<T>>() {

      @Override
      public boolean hasNext() {
        return pluginIterator.hasNext();
      }

      @Override
      public PluginSetEntryContext<T> next() {
        return new PluginSetEntryContext<>(pluginIterator.next());
      }
    };
  }

  public boolean isEmpty() {
    return !dynamicSet.iterator().hasNext();
  }

  /**
   * Invokes each entry of the set. All exceptions from the plugin are caught and logged.
   *
   * <p>The consumer gets the extension point provided that should be invoked.
   *
   * @param pluginConsumer consumer that invokes the extension point
   */
  public void runEach(PluginConsumer<T> pluginConsumer) {
    dynamicSet.entries().forEach(p -> PluginContext.runLogExceptions(p, pluginConsumer));
  }

  /**
   * Invokes each entry of the set. All exceptions from the plugin except exceptions of the
   * specified type are caught and logged.
   *
   * <p>The consumer gets the extension point provided that should be invoked.
   *
   * @param pluginConsumer consumer that invokes the extension point
   */
  public <X extends Exception> void runEach(
      PluginConsumer<T> pluginConsumer, Class<X> exceptionClass) throws X {
    for (PluginEntry<T> pluginEntry : dynamicSet.entries()) {
      PluginContext.runLogExceptions(pluginEntry, pluginConsumer, exceptionClass);
    }
  }
}
