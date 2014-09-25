// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.git.archive;

import static com.google.common.base.Preconditions.checkState;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectLoader;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Wrapping ZipFormat, to allow configuring the compression level.
 * The compression level is stored in a thread local storage, which must be
 * set before createArchiveOutputStream().
 */
public class ZipFormat implements ArchiveCommand.Format<ArchiveOutputStream> {
  private static final ThreadLocal<Integer> COMPRESSION_LEVEL = new ThreadLocal<>();

  private final org.eclipse.jgit.archive.ZipFormat zipFormat =
      new org.eclipse.jgit.archive.ZipFormat();

  @Override
  public ArchiveOutputStream createArchiveOutputStream(OutputStream s) {
    ArchiveOutputStream archiveStream = zipFormat.createArchiveOutputStream(s);
    checkState(archiveStream instanceof ZipArchiveOutputStream,
        "ZipArchiveOutputStream instance expected");
    Integer level = COMPRESSION_LEVEL.get();
    if (level != null) {
      ((ZipArchiveOutputStream) archiveStream).setLevel(level);
      COMPRESSION_LEVEL.remove();
    }
    return archiveStream;
  }

  @Override
  public void putEntry(ArchiveOutputStream out, String path, FileMode mode,
      ObjectLoader loader) throws IOException {
    zipFormat.putEntry(out, path, mode, loader);
  }

  @Override
  public Iterable<String> suffixes() {
    return zipFormat.suffixes();
  }

  @Override
  public boolean equals(Object other) {
    return zipFormat.equals(other);
  }

  @Override
  public int hashCode() {
    return zipFormat.hashCode();
  }

  public static void setLevel(int value) {
    COMPRESSION_LEVEL.set(value);
  }
}
