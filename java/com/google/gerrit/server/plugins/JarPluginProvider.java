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

package com.google.gerrit.server.plugins;

import com.google.common.base.MoreObjects;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JarPluginProvider implements ServerPluginProvider {
  static final String PLUGIN_TMP_PREFIX = "plugin_";
  static final String JAR_EXTENSION = ".jar";
  static final Logger log = LoggerFactory.getLogger(JarPluginProvider.class);

  private final Path tmpDir;
  private final PluginConfigFactory configFactory;

  @Inject
  JarPluginProvider(SitePaths sitePaths, PluginConfigFactory configFactory) {
    this.tmpDir = sitePaths.tmp_dir;
    this.configFactory = configFactory;
  }

  @Override
  public boolean handles(Path srcPath) {
    String fileName = srcPath.getFileName().toString();
    return fileName.endsWith(JAR_EXTENSION) || fileName.endsWith(JAR_EXTENSION + ".disabled");
  }

  @Override
  public String getPluginName(Path srcPath) {
    try {
      return MoreObjects.firstNonNull(getJarPluginName(srcPath), PluginUtil.nameOf(srcPath));
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Invalid plugin file " + srcPath + ": cannot get plugin name", e);
    }
  }

  public static String getJarPluginName(Path srcPath) throws IOException {
    try (JarFile jarFile = new JarFile(srcPath.toFile())) {
      return jarFile.getManifest().getMainAttributes().getValue("Gerrit-PluginName");
    }
  }

  @Override
  public ServerPlugin get(Path srcPath, FileSnapshot snapshot, PluginDescription description)
      throws InvalidPluginException {
    try {
      String name = getPluginName(srcPath);
      String extension = getExtension(srcPath);
      try (InputStream in = Files.newInputStream(srcPath)) {
        Path tmp = PluginUtil.asTemp(in, tempNameFor(name), extension, tmpDir);
        return loadJarPlugin(name, srcPath, snapshot, tmp, description);
      }
    } catch (IOException e) {
      throw new InvalidPluginException("Cannot load Jar plugin " + srcPath, e);
    }
  }

  @Override
  public String getProviderPluginName() {
    return "gerrit";
  }

  private static String getExtension(Path path) {
    return getExtension(path.getFileName().toString());
  }

  private static String getExtension(String name) {
    int ext = name.lastIndexOf('.');
    return 0 < ext ? name.substring(ext) : "";
  }

  private static String tempNameFor(String name) {
    SimpleDateFormat fmt = new SimpleDateFormat("yyMMdd_HHmm");
    return PLUGIN_TMP_PREFIX + name + "_" + fmt.format(new Date()) + "_";
  }

  public static Path storeInTemp(String pluginName, InputStream in, SitePaths sitePaths)
      throws IOException {
    if (!Files.exists(sitePaths.tmp_dir)) {
      Files.createDirectories(sitePaths.tmp_dir);
    }
    return PluginUtil.asTemp(in, tempNameFor(pluginName), ".jar", sitePaths.tmp_dir);
  }

  private ServerPlugin loadJarPlugin(
      String name, Path srcJar, FileSnapshot snapshot, Path tmp, PluginDescription description)
      throws IOException, InvalidPluginException, MalformedURLException {
    JarFile jarFile = new JarFile(tmp.toFile());
    boolean keep = false;
    try {
      Manifest manifest = jarFile.getManifest();
      Plugin.ApiType type = Plugin.getApiType(manifest);

      List<URL> urls = new ArrayList<>(2);
      String overlay = System.getProperty("gerrit.plugin-classes");
      if (overlay != null) {
        Path classes = Paths.get(overlay).resolve(name).resolve("main");
        if (Files.isDirectory(classes)) {
          log.info(String.format("plugin %s: including %s", name, classes));
          urls.add(classes.toUri().toURL());
        }
      }
      urls.add(tmp.toUri().toURL());

      ClassLoader pluginLoader =
          new URLClassLoader(urls.toArray(new URL[urls.size()]), PluginUtil.parentFor(type));

      JarScanner jarScanner = createJarScanner(tmp);
      PluginConfig pluginConfig = configFactory.getFromGerritConfig(name);

      ServerPlugin plugin =
          new ServerPlugin(
              name,
              description.canonicalUrl,
              description.user,
              srcJar,
              snapshot,
              jarScanner,
              description.dataDir,
              pluginLoader,
              pluginConfig.getString("metricsPrefix", null));
      plugin.setCleanupHandle(new CleanupHandle(tmp, jarFile));
      keep = true;
      return plugin;
    } finally {
      if (!keep) {
        jarFile.close();
      }
    }
  }

  private JarScanner createJarScanner(Path srcJar) throws InvalidPluginException {
    try {
      return new JarScanner(srcJar);
    } catch (IOException e) {
      throw new InvalidPluginException("Cannot scan plugin file " + srcJar, e);
    }
  }
}
