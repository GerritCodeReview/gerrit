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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

class CleanupHandle {
  private final Path tmp;
  private final JarFile jarFile;

  CleanupHandle(Path tmp, JarFile jarFile) {
    this.tmp = tmp;
    this.jarFile = jarFile;
  }

  void cleanup() {
    try {
      jarFile.close();
    } catch (IOException err) {
      PluginLoader.log.error("Cannot close " + jarFile.getName(), err);
    }
    try {
      Files.deleteIfExists(tmp);
      PluginLoader.log.info("Cleaned plugin " + tmp.getFileName());
    } catch (IOException e) {
      PluginLoader.log.warn(
          "Cannot delete "
              + tmp.toAbsolutePath()
              + ", retrying to delete it on termination of the virtual machine",
          e);
      tmp.toFile().deleteOnExit();
    }
  }
}
