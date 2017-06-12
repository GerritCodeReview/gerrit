// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.extensions.webui;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.inject.Inject;

/**
 * Specifies JavaScript to dynamically load into the web UI.
 *
 * <p>To automatically register (instead of writing a Guice module), declare the intention with
 * {@code @Listen}, extend the correct class and define a constructor to configure the correct
 * resource:
 *
 * <pre>
 * &#064;Listen
 * class MyJs extends JavaScriptPlugin {
 *   MyJs() {
 *     super(&quot;hello.js&quot;);
 *   }
 * }
 * </pre>
 *
 * @see GwtPlugin
 * @see JavaScriptPlugin
 */
@ExtensionPoint
public abstract class WebUiPlugin {
  public static final GwtPlugin gwt(String moduleName) {
    return new GwtPlugin(moduleName);
  }

  public static final JavaScriptPlugin js(String scriptName) {
    return new JavaScriptPlugin(scriptName);
  }

  private String pluginName;

  /** @return installed name of the plugin that provides this UI feature. */
  public final String getPluginName() {
    return pluginName;
  }

  @Inject
  void setPluginName(@PluginName String pluginName) {
    this.pluginName = pluginName;
  }

  /** @return path to initialization script within the plugin's JAR. */
  public abstract String getJavaScriptResourcePath();

  @Override
  public String toString() {
    return getJavaScriptResourcePath();
  }
}
