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

package com.google.gerrit.acceptance;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.server.config.SitePaths;

import org.junit.AfterClass;
import org.junit.runner.Description;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class PluginDaemonTest extends AbstractDaemonTest {

  private static final String BUCKLC = "buck";
  private static final String BUCKOUT = "buck-out";
  private static final String BUILD = "build";
  private static final String EMPTY = "src/main/java/ForceJarIfMissing.java";
  private static final Path testSite =
      Paths.get(InMemoryTestingDatabaseModule.UNIT_TEST_GERRIT_SITE);

  private Path gen;
  private Path pluginRoot;
  private Path pluginsPath;
  private Path pluginSubPath;
  private Path pluginSource;
  private String pluginName;
  private boolean standalone;
  private boolean isMaven;

  @Override
  protected void beforeTest(Description description) throws Exception {
    deployPlugin();
    super.beforeTest(description);
  }

  @AfterClass
  public static void cleanUp() throws IOException {
    removeTestSite();
  }

  private static void removeTestSite() throws IOException {
    if (!Files.exists(testSite)) {
      return;
    }
    Files.walkFileTree(testSite, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
          throws IOException {
        Files.deleteIfExists(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc)
          throws IOException {
        Files.deleteIfExists(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private void deployPlugin() throws IOException, InterruptedException {
    locatePaths();
    retrievePluginName();
    buildPluginJar();
    createTestSite();
    copyJarToTestSite();
  }

  private void locatePaths() {
    URL pluginClassesUrl =
        getClass().getProtectionDomain().getCodeSource().getLocation();
    Path basePath = Paths.get(pluginClassesUrl.getPath()).getParent();

    int idx = 0;
    int buckOutIdx = 0;
    int pluginsIdx = 0;
    int gerritIdx = 0;
    int genIdx = 0;
    for (Path subPath : basePath) {
      if (subPath.endsWith("gerrit")) {
        gerritIdx = idx;
      }
      if (subPath.endsWith("plugins")) {
        pluginsIdx = idx;
      }
      if (subPath.endsWith(BUCKOUT)) {
        buckOutIdx = idx;
      }
      if (subPath.endsWith("gen")) {
        genIdx = idx;
      }
      idx++;
    }
    isMaven = buckOutIdx == 0;
    standalone = isStandalone(gerritIdx, pluginsIdx, genIdx);
    if (isMaven) {
      if (standalone) {
        pluginRoot = basePath.getParent();
      } else {
        pluginRoot = basePath.getRoot().resolve(basePath.subpath(0, pluginsIdx));
      }
    } else {
      pluginRoot = basePath.getRoot().resolve(basePath.subpath(0, buckOutIdx));
    }
    gen = pluginRoot.resolve(BUCKOUT).resolve("gen");

    if (standalone) {
      pluginSource = pluginRoot;
    } else {
      pluginSubPath = basePath.subpath(pluginsIdx, pluginsIdx + 2);
      pluginSource = pluginRoot.resolve(pluginSubPath);
    }
  }

  private void retrievePluginName() throws IOException {
    Path buckFile = pluginSource.resolve("BUCK");
    byte[] bytes = Files.readAllBytes(buckFile);
    String buckContent =
        new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s+", "");
    Matcher matcher =
        Pattern.compile("gerrit_plugin\\(name='(.*?)'").matcher(buckContent);
    if (matcher.find()) {
      pluginName = matcher.group(1);
    }
    if (Strings.isNullOrEmpty(pluginName)) {
      if (standalone) {
        pluginName = pluginRoot.getFileName().toString();
      } else {
        pluginName = pluginSubPath.getFileName().toString();
      }
    }
  }

  private void buildPluginJar() throws IOException, InterruptedException {
    Properties properties = loadBuckProperties();
    String buck =
        MoreObjects.firstNonNull(properties.getProperty(BUCKLC), BUCKLC);
    String target;
    if (standalone) {
      target = "//:" + pluginName;
    } else {
      target = pluginSubPath.toString();
    }

    ProcessBuilder processBuilder =
        new ProcessBuilder(buck, BUILD, target).directory(pluginRoot.toFile())
            .redirectErrorStream(true);

    Path forceJar = pluginSource.resolve(EMPTY);
    Files.createFile(forceJar);
    try {
      processBuilder.start().waitFor();
    } finally {
      Files.delete(forceJar);
      // otherwise jar not made next time if missing again:
      processBuilder.start().waitFor();
    }
  }

  private Properties loadBuckProperties() throws IOException {
    Properties properties = new Properties();
    Path propertiesPath = gen.resolve("tools").resolve("buck.properties");
    if (Files.exists(propertiesPath)) {
      try (InputStream in = Files.newInputStream(propertiesPath)) {
        properties.load(in);
      }
    }
    return properties;
  }

  private void createTestSite() throws IOException {
    SitePaths sitePath = new SitePaths(testSite);
    pluginsPath = Files.createDirectories(sitePath.plugins_dir);
    Files.createDirectories(sitePath.tmp_dir);
  }

  private void copyJarToTestSite() throws IOException {
    Path pluginOut;
    if (standalone) {
      pluginOut = gen;
    } else {
      pluginOut = gen.resolve(pluginSubPath);
    }
    Path jar = pluginOut.resolve(pluginName + ".jar");
    Path dest = pluginsPath.resolve(jar.getFileName());
    Files.copy(jar, dest, StandardCopyOption.REPLACE_EXISTING);
  }

  private boolean isStandalone(int gerritIdx, int pluginsIdx, int genIdx) {
    int dist = pluginsIdx - gerritIdx;
    return genIdx > 0 || !(dist == 1 || dist == 3);
  }
}
