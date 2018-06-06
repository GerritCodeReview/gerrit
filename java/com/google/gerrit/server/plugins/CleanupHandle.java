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

import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

class CleanupHandle {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
      logger.atSevere().withCause(err).log("Cannot close %s", jarFile.getName());
    }
    try {
      Files.deleteIfExists(tmp);
      logger.atInfo().log("Cleaned plugin %s", tmp.getFileName());
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Cannot delete %s, retrying to delete it on termination of the virtual machine",
          tmp.toAbsolutePath());
      tmp.toFile().deleteOnExit();
    }
  }
}
