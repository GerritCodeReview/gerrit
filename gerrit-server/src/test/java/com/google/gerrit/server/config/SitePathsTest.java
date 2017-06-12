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

package com.google.gerrit.server.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.server.util.HostPlatform;
import com.google.gerrit.testutil.GerritBaseTests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class SitePathsTest extends GerritBaseTests {
  @Test
  public void testCreate_NotExisting() throws IOException {
    final Path root = random();
    final SitePaths site = new SitePaths(root);
    assertTrue(site.isNew);
    assertEquals(root, site.site_path);
    assertEquals(root.resolve("etc"), site.etc_dir);
  }

  @Test
  public void testCreate_Empty() throws IOException {
    final Path root = random();
    try {
      Files.createDirectory(root);

      final SitePaths site = new SitePaths(root);
      assertTrue(site.isNew);
      assertEquals(root, site.site_path);
    } finally {
      Files.delete(root);
    }
  }

  @Test
  public void testCreate_NonEmpty() throws IOException {
    final Path root = random();
    final Path txt = root.resolve("test.txt");
    try {
      Files.createDirectory(root);
      Files.createFile(txt);

      final SitePaths site = new SitePaths(root);
      assertFalse(site.isNew);
      assertEquals(root, site.site_path);
    } finally {
      Files.delete(txt);
      Files.delete(root);
    }
  }

  @Test
  public void testCreate_NotDirectory() throws IOException {
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
  public void testResolve() throws IOException {
    final Path root = random();
    final SitePaths site = new SitePaths(root);

    assertNull(site.resolve(null));
    assertNull(site.resolve(""));

    assertNotNull(site.resolve("a"));
    assertEquals(root.resolve("a").toAbsolutePath().normalize(), site.resolve("a"));

    final String pfx = HostPlatform.isWin32() ? "C:/" : "/";
    assertNotNull(site.resolve(pfx + "a"));
    assertEquals(Paths.get(pfx + "a"), site.resolve(pfx + "a"));
  }

  private static Path random() throws IOException {
    Path tmp = Files.createTempFile("gerrit_test_", "_site");
    Files.deleteIfExists(tmp);
    return tmp;
  }
}
