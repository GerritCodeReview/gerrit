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

package com.google.gerrit.server.plugins;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

class CleanupHandle {
  private final File tmpFile;
  private final JarFile jarFile;

  CleanupHandle(File tmpFile,
      JarFile jarFile) {
    this.tmpFile = tmpFile;
    this.jarFile = jarFile;
  }

  void cleanup() {
    try {
      jarFile.close();
    } catch (IOException err) {
      PluginLoader.log.error("Cannot close " + jarFile.getName());
    }
    if (!tmpFile.delete() && tmpFile.exists()) {
      PluginLoader.log.warn("Cannot delete " + tmpFile.getAbsolutePath()
          + ", retrying to delete it on termination of the virtual machine");
      tmpFile.deleteOnExit();
    } else {
      PluginLoader.log.info("Cleaned plugin " + tmpFile.getName());
    }
  }
}
