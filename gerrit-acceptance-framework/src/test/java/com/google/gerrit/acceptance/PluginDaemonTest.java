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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.server.config.SitePaths;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.runner.Description;

public abstract class PluginDaemonTest extends AbstractDaemonTest {

  private static final String BUCKLC = "buck";
  private static final String BUCKOUT = "buck-out";
  private static final String ECLIPSE = "eclipse-out";

  private Path gen;
  private Path pluginRoot;
  private Path pluginsSitePath;
  private Path pluginSubPath;
  private Path pluginSource;
  private boolean standalone;

  protected String pluginName;
  protected Path testSite;

  @Override
  protected void beforeTest(Description description) throws Exception {
    locatePaths();
    retrievePluginName();
    buildPluginJar();
    createTestSiteDirs();
    copyJarToTestSite();
    beforeTestServerStarts();
    super.beforeTest(description);
  }

  protected void beforeTestServerStarts() throws Exception {}

  protected void setPluginConfigString(String name, String value)
      throws IOException, ConfigInvalidException {
    SitePaths sitePath = new SitePaths(testSite);
    FileBasedConfig cfg = getGerritConfigFile(sitePath);
    cfg.load();
    cfg.setString("plugin", pluginName, name, value);
    cfg.save();
  }

  private FileBasedConfig getGerritConfigFile(SitePaths sitePath) throws IOException {
    FileBasedConfig cfg = new FileBasedConfig(sitePath.gerrit_config.toFile(), FS.DETECTED);
    if (!cfg.getFile().exists()) {
      Path etc_path = Files.createDirectories(sitePath.etc_dir);
      Files.createFile(etc_path.resolve("gerrit.config"));
    }
    return cfg;
  }

  private void locatePaths() {
    URL pluginClassesUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
    Path basePath = Paths.get(pluginClassesUrl.getPath()).getParent();

    int idx = 0;
    int buckOutIdx = 0;
    int pluginsIdx = 0;
    for (Path subPath : basePath) {
      if (subPath.endsWith("plugins")) {
        pluginsIdx = idx;
      }
      if (subPath.endsWith(BUCKOUT) || subPath.endsWith(ECLIPSE)) {
        buckOutIdx = idx;
      }
      idx++;
    }
    standalone = checkStandalone(basePath);
    pluginRoot = basePath.getRoot().resolve(basePath.subpath(0, buckOutIdx));
    gen = pluginRoot.resolve(BUCKOUT).resolve("gen");

    if (standalone) {
      pluginSource = pluginRoot;
    } else {
      pluginSubPath = basePath.subpath(pluginsIdx, pluginsIdx + 2);
      pluginSource = pluginRoot.resolve(pluginSubPath);
    }
  }

  private boolean checkStandalone(Path basePath) {
    String pathCharStringOrNone = "[a-zA-Z0-9._-]*?";
    Pattern pattern = Pattern.compile(pathCharStringOrNone + "gerrit" + pathCharStringOrNone);
    Path partialPath = basePath;
    for (int i = basePath.getNameCount(); i > 0; i--) {
      int count = partialPath.getNameCount();
      if (count > 1) {
        String gerritDirCandidate = partialPath.subpath(count - 2, count - 1).toString();
        if (pattern.matcher(gerritDirCandidate).matches()) {
          if (partialPath.endsWith(gerritDirCandidate + "/" + BUCKOUT)
              || partialPath.endsWith(gerritDirCandidate + "/" + ECLIPSE)) {
            return false;
          }
        }
      }
      partialPath = partialPath.getParent();
    }
    return true;
  }

  private void retrievePluginName() throws IOException {
    Path buckFile = pluginSource.resolve("BUCK");
    byte[] bytes = Files.readAllBytes(buckFile);
    String buckContent = new String(bytes, UTF_8).replaceAll("\\s+", "");
    Matcher matcher = Pattern.compile("gerrit_plugin\\(name='(.*?)'").matcher(buckContent);
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
    String buck = MoreObjects.firstNonNull(properties.getProperty(BUCKLC), BUCKLC);
    String target;
    if (standalone) {
      target = "//:" + pluginName;
    } else {
      target = pluginSubPath.toString();
    }

    ProcessBuilder processBuilder =
        new ProcessBuilder(buck, "build", target)
            .directory(pluginRoot.toFile())
            .redirectErrorStream(true);
    // otherwise plugin jar creation fails:
    processBuilder.environment().put("NO_BUCKD", "1");

    Path forceJar = pluginSource.resolve("src/main/java/ForceJarIfMissing.java");
    // if exists after cancelled test:
    Files.deleteIfExists(forceJar);

    Files.createFile(forceJar);
    testSite = tempSiteDir.getRoot().toPath();

    // otherwise process often hangs:
    Path log = testSite.resolve("log");
    processBuilder.redirectErrorStream(true);
    processBuilder.redirectOutput(Redirect.appendTo(log.toFile()));

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
    Path propertiesPath = gen.resolve(Paths.get("tools/buck/buck.properties"));
    if (Files.exists(propertiesPath)) {
      try (InputStream in = Files.newInputStream(propertiesPath)) {
        properties.load(in);
      }
    }
    return properties;
  }

  private void createTestSiteDirs() throws IOException {
    SitePaths sitePath = new SitePaths(testSite);
    pluginsSitePath = Files.createDirectories(sitePath.plugins_dir);
    Files.createDirectories(sitePath.tmp_dir);
    Files.createDirectories(sitePath.etc_dir);
  }

  private void copyJarToTestSite() throws IOException {
    Path pluginOut;
    if (standalone) {
      pluginOut = gen;
    } else {
      pluginOut = gen.resolve(pluginSubPath);
    }
    Path jar = pluginOut.resolve(pluginName + ".jar");
    Path dest = pluginsSitePath.resolve(jar.getFileName());
    Files.copy(jar, dest, StandardCopyOption.REPLACE_EXISTING);
  }
}
