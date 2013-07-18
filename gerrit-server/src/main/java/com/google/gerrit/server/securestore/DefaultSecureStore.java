// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.securestore;

import com.google.gerrit.common.FileUtil;
import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;

@Singleton
@Export(DefaultSecureStore.NAME)
public class DefaultSecureStore implements SecureStore {
  public static final String NAME = "default";

  private final FileBasedConfig sec;

  @Inject
  DefaultSecureStore(SitePaths site) {
    File secureConfig = new File(site.etc_dir, "secure.config");
    sec = new FileBasedConfig(secureConfig, FS.DETECTED);
    try {
      sec.load();
    } catch (Exception e) {
      throw new RuntimeException("Cannot load secure.config", e);
    }
  }

  @Override
  public String get(String section, String subsection, String name) {
    return sec.getString(section, subsection, name);
  }

  @Override
  public void set(String section, String subsection, String name, String value) {
    if (value != null) {
      sec.setString(section, subsection, name, value);
    } else {
      sec.unset(section, subsection, name);
    }
    save();
  }

  @Override
  public void unset(String section, String subsection, String name) {
    sec.unset(section, subsection, name);
    save();
  }

  private void save() {
    try {
      saveSecure(sec);
    } catch (IOException e) {
      throw new RuntimeException("Cannot save secure.config", e);
    }
  }

  private static void saveSecure(final FileBasedConfig sec) throws IOException {
    if (FileUtil.modified(sec)) {
      final byte[] out = Constants.encode(sec.toText());
      final File path = sec.getFile();
      final LockFile lf = new LockFile(path, FS.DETECTED);
      if (!lf.lock()) {
        throw new IOException("Cannot lock " + path);
      }
      try {
        FileUtil.chmod(0600, new File(path.getParentFile(), path.getName()
            + ".lock"));
        lf.write(out);
        if (!lf.commit()) {
          throw new IOException("Cannot commit write to " + path);
        }
      } finally {
        lf.unlock();
      }
    }
  }
}
