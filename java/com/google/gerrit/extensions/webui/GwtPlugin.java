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

/** Configures a web UI plugin compiled using GWT. */
public class GwtPlugin extends WebUiPlugin {
  private final String moduleName;

  /**
   * @param moduleName name of GWT module. The resource {@code static/$MODULE/$MODULE.nocache.js}
   *     will be used.
   */
  public GwtPlugin(String moduleName) {
    this.moduleName = moduleName;
  }

  @Override
  public String getJavaScriptResourcePath() {
    return String.format("static/%s/%s.nocache.js", moduleName, moduleName);
  }
}
