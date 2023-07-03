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

import static com.google.common.flogger.LazyArgs.lazy;
import static com.google.gerrit.common.FileUtil.lastModified;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

public final class SiteLibraryLoaderUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static void loadSiteLib(Path libdir) {
    try {
      List<Path> jars = listJars(libdir);
      JARUtil.loadJARs(jars);
      logger.atFine().log("Loaded site libraries: %s", lazy(() -> jarList(jars)));
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Error scanning lib directory %s", libdir);
    }
  }

  private static String jarList(List<Path> jars) {
    return jars.stream().map(p -> p.getFileName().toString()).collect(joining(","));
  }

  public static List<Path> listJars(Path dir) throws IOException {
    DirectoryStream.Filter<Path> filter =
        entry -> {
          String name = entry.getFileName().toString();
          return (name.endsWith(".jar") || name.endsWith(".zip")) && Files.isRegularFile(entry);
        };
    try (DirectoryStream<Path> jars = Files.newDirectoryStream(dir, filter)) {
      return new Ordering<Path>() {
        @Override
        public int compare(Path a, Path b) {
          // Sort by reverse last-modified time so newer JARs are first.
          return ComparisonChain.start()
              .compare(lastModified(b), lastModified(a))
              .compare(a, b)
              .result();
        }
      }.sortedCopy(jars);
    } catch (NoSuchFileException nsfe) {
      return ImmutableList.of();
    }
  }

  private SiteLibraryLoaderUtil() {}
}
