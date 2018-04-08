// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.config;

import static com.google.common.truth.Truth.assertThat;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.common.testing.PathSubject;
import com.google.gerrit.server.util.HostPlatform;
import com.google.gerrit.testing.GerritBaseTests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class SitePathsTest extends GerritBaseTests {
  @Test
  public void create_NotExisting() throws IOException {
    final Path root = random();
    final SitePaths site = new SitePaths(root);
    assertThat(site.isNew).isTrue();
    PathSubject.assertThat(site.site_path).isEqualTo(root);
    PathSubject.assertThat(site.etc_dir).isEqualTo(root.resolve("etc"));
  }

  @Test
  public void create_Empty() throws IOException {
    final Path root = random();
    try {
      Files.createDirectory(root);

      final SitePaths site = new SitePaths(root);
      assertThat(site.isNew).isTrue();
      PathSubject.assertThat(site.site_path).isEqualTo(root);
    } finally {
      Files.delete(root);
    }
  }

  @Test
  public void create_NonEmpty() throws IOException {
    final Path root = random();
    final Path txt = root.resolve("test.txt");
    try {
      Files.createDirectory(root);
      Files.createFile(txt);

      final SitePaths site = new SitePaths(root);
      assertThat(site.isNew).isFalse();
      PathSubject.assertThat(site.site_path).isEqualTo(root);
    } finally {
      Files.delete(txt);
      Files.delete(root);
    }
  }

  @Test
  public void create_NotDirectory() throws IOException {
    final Path root = random();
    try {
      Files.createFile(root);
      exception.expect(NotDirectoryException.class);
      new SitePaths(root);
    } finally {
      Files.delete(root);
    }
  }

  @Test
  public void resolve() throws IOException {
    final Path root = random();
    final SitePaths site = new SitePaths(root);

    PathSubject.assertThat(site.resolve(null)).isNull();
    PathSubject.assertThat(site.resolve("")).isNull();

    PathSubject.assertThat(site.resolve("a")).isNotNull();
    PathSubject.assertThat(site.resolve("a"))
        .isEqualTo(root.resolve("a").toAbsolutePath().normalize());

    final String pfx = HostPlatform.isWin32() ? "C:/" : "/";
    PathSubject.assertThat(site.resolve(pfx + "a")).isNotNull();
    PathSubject.assertThat(site.resolve(pfx + "a")).isEqualTo(Paths.get(pfx + "a"));
  }

  private static Path random() throws IOException {
    Path tmp = Files.createTempFile("gerrit_test_", "_site");
    Files.deleteIfExists(tmp);
    return tmp;
  }
}
