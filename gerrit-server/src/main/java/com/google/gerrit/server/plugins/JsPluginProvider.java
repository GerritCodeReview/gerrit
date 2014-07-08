// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;

import java.io.File;

public class JsPluginProvider implements ServerPluginProvider {
  private static final String JS_SUFFIX = ".js";

  @Override
  public boolean handles(File srcFile) {
    return srcFile.getName().endsWith(JS_SUFFIX);
  }

  @Override
  public String getPluginName(File srcFile) {
    checkArgument(handles(srcFile), "{} is not a valid JavaScript plugin file",
        srcFile);
    return StringUtils.substringBeforeLast(srcFile.getName(), JS_SUFFIX);
  }

  @Override
  public Plugin get(File srcFile, FileSnapshot snapshot,
      PluginDescription pluginDescription) throws InvalidPluginException {
    String name = getPluginName(srcFile);
    return new JsPlugin(name, srcFile, pluginDescription.user, snapshot);
  }

  @Override
  public String getProviderPluginName() {
    return "gerrit";
  }
}
