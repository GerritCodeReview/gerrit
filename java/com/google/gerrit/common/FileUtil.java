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
import java.nio.file.attribute.PosixFilePermissions;
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

  public static void chmod(String mode, Path path) throws IOException {
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode));
  }

  public static void chmod(String mode, File file) throws IOException {
    chmod(mode, file.toPath());
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
