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

/** Configures a web UI plugin written using JavaScript. */
public class JavaScriptPlugin extends WebUiPlugin {
  /**
   * Name of default JavaScript plugin initialization file.
   *
   * Will be used when plugin archive (either zip or jar) do not provide binding
   * for client side code. Autoregistration will look for file 'static/init.js'
   * to complete registration process.
   */
  public static final String DEFAULT_INIT_FILE_NAME = "init.js";

  /**
   * Name of folder in which {@code fileName} is expected to be located
   */
  public static final String CONTAINER_NAME = "static/";

  private final String fileName;

  /**
   * @param fileName of JavaScript source file under {@code static/}
   *        subdirectory within the plugin's JAR.
   */
  public JavaScriptPlugin(String fileName) {
    this.fileName = fileName;
  }

  @Override
  public String getJavaScriptResourcePath() {
    return CONTAINER_NAME + fileName;
  }
}
