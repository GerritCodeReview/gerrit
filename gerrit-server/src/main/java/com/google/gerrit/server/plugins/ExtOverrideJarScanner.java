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

import com.google.common.base.Optional;
import com.google.gerrit.server.config.SitePaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ExtOverrideJarScanner extends JarScanner {
  private File overridePath;

  public ExtOverrideJarScanner(File srcFile, File overridePath)
      throws InvalidPluginException {
    super(srcFile);
    this.overridePath = overridePath;
  }

  @Override
  public Optional<PluginEntry> getEntry(String resourcePath) throws IOException {
    if (getStaticOverride(resourcePath).exists()) {
      return getEntryOf(resourcePath);
    } else {
      return super.getEntry(resourcePath);
    }
  }

  private File getStaticOverride(String resourcePath) {
    File staticOverrideFile = new File(overridePath, resourcePath);
    return staticOverrideFile;
  }

  private Optional<PluginEntry> getEntryOf(String resourcePath) {
    File staticOverrideFile = getStaticOverride(resourcePath);
    return Optional.of(new PluginEntry(resourcePath, staticOverrideFile
        .lastModified(), Optional.of(staticOverrideFile.length())));
  }

  @Override
  public InputStream getInputStream(PluginEntry entry) throws IOException {
    File staticOverrideFile = getStaticOverride(entry.getName());
    if (staticOverrideFile.exists()) {
      return new FileInputStream(staticOverrideFile);
    } else {
      return super.getInputStream(entry);
    }
  }
}
