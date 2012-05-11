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
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.jar.JarFile;

class CleanupHandle extends WeakReference<ClassLoader> {
  private final File tmpFile;
  private final JarFile jarFile;

  CleanupHandle(File tmpFile,
      JarFile jarFile,
      ClassLoader ref,
      ReferenceQueue<ClassLoader> queue) {
    super(ref, queue);
    this.tmpFile = tmpFile;
    this.jarFile = jarFile;
  }

  void cleanup() {
    try {
      jarFile.close();
    } catch (IOException err) {
    }
    if (!tmpFile.delete() && tmpFile.exists()) {
      PluginLoader.log.warn("Cannot delete " + tmpFile.getAbsolutePath());
    } else {
      PluginLoader.log.info("Cleaned plugin " + tmpFile.getName());
    }
  }
}
