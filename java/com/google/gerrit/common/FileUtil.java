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

package com.google.gerrit.common;

import com.google.common.annotations.GwtIncompatible;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.IO;

@GwtIncompatible("Unemulated classes in java.io, java.nio and JGit")
public class FileUtil {
  public static boolean modified(FileBasedConfig cfg) throws IOException {
    byte[] curVers;
    try {
      curVers = IO.readFully(cfg.getFile());
    } catch (FileNotFoundException notFound) {
      return true;
    }

    byte[] newVers = Constants.encode(cfg.toText());
    return !Arrays.equals(curVers, newVers);
  }

  public static void mkdir(File path) {
    if (!path.isDirectory() && !path.mkdir()) {
      throw new Die("Cannot make directory " + path);
    }
  }

  public static void chmod(int mode, Path path) {
    // TODO(dborowitz): Is there a portable way to do this with NIO?
    chmod(mode, path.toFile());
  }

  public static void chmod(int mode, File path) {
    path.setReadable(false, false /* all */);
    path.setWritable(false, false /* all */);
    path.setExecutable(false, false /* all */);

    path.setReadable((mode & 0400) == 0400, true /* owner only */);
    path.setWritable((mode & 0200) == 0200, true /* owner only */);
    if (path.isDirectory() || (mode & 0100) == 0100) {
      path.setExecutable(true, true /* owner only */);
    }

    if ((mode & 0044) == 0044) {
      path.setReadable(true, false /* all */);
    }
    if ((mode & 0011) == 0011) {
      path.setExecutable(true, false /* all */);
    }
  }

  /**
   * Get the last modified time of a path.
   *
   * <p>Equivalent to {@code File#lastModified()}, returning 0 on errors, including file not found.
   * Callers that prefer exceptions can use {@link Files#getLastModifiedTime(Path,
   * java.nio.file.LinkOption...)}.
   *
   * @param p path.
   * @return last modified time, in milliseconds since epoch.
   */
  public static long lastModified(Path p) {
    try {
      return Files.getLastModifiedTime(p).toMillis();
    } catch (IOException e) {
      return 0;
    }
  }

  public static Path mkdirsOrDie(Path p, String errMsg) {
    try {
      if (!Files.isDirectory(p)) {
        Files.createDirectories(p);
      }
      return p;
    } catch (IOException e) {
      throw new Die(errMsg + ": " + p, e);
    }
  }

  private FileUtil() {}
}
