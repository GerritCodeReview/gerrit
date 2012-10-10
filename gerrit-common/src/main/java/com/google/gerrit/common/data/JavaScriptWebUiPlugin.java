/*
 * Copyright 2012 CollabNet
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.gerrit.common.data;

/**
 * Configuration for JavaScript based Web UI plugins
 */
public class JavaScriptWebUiPlugin implements WebUiPlugin {

  private final String fileName;

  /**
   * @param fileName name of JavaScript plugin init file
   */
  public JavaScriptWebUiPlugin(String fileName) {
    this.fileName = fileName;
  }

  @Override
  public String getPath() {
    return "static/" + fileName;
  }

}
