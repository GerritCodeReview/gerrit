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

import com.google.common.base.Objects;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class JarPluginProvider implements ServerPluginProvider {
  static final String PLUGIN_TMP_PREFIX = "plugin_";
  static final String JAR_EXTENSION = ".jar";
  static final Logger log = LoggerFactory.getLogger(JarPluginProvider.class);

  private final File tmpDir;

  @Inject
  public JarPluginProvider(SitePaths sitePaths) {
    tmpDir = sitePaths.tmp_dir;
  }

  @Override
  public boolean handles(File srcFile) {
    String fileName = srcFile.getName();
    return fileName.endsWith(JAR_EXTENSION)
        || fileName.endsWith(JAR_EXTENSION + ".disabled");
  }

  @Override
  public String getPluginName(File srcFile) {
    try {
      return Objects.firstNonNull(getGerritJarPluginName(srcFile), PluginLoader.nameOf(srcFile));
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid plugin file " + srcFile
          + ": cannot get plugin name", e);
    }
  }

  public static String getGerritJarPluginName(File srcFile) throws IOException {
    JarFile jarFile = new JarFile(srcFile);
    try {
      return jarFile.getManifest().getMainAttributes()
          .getValue("Gerrit-PluginName");
    } finally {
      jarFile.close();
    }
  }

  @Override
  public ServerPlugin get(File srcFile, PluginUser pluginUser,
      FileSnapshot snapshot, String pluginCanonicalWebUrl, File pluginDataDir)
      throws InvalidPluginException {
    try {
      File tmp;
      String name = getPluginName(srcFile);
      FileInputStream in = new FileInputStream(srcFile);
      String extension = getExtension(srcFile);
      try {
        tmp = asTemp(in, tempNameFor(name), extension, tmpDir);
        return loadJarPlugin(name, srcFile, snapshot, tmp,
            pluginCanonicalWebUrl, pluginUser, pluginDataDir);
      } finally {
        in.close();
      }
    } catch (IOException | ClassNotFoundException e) {
      throw new InvalidPluginException("Cannot load Jar plugin " + srcFile, e);
    }
  }

  private static String getExtension(File file) {
    return getExtension(file.getName());
  }

  private static String getExtension(String name) {
    int ext = name.lastIndexOf('.');
    return 0 < ext ? name.substring(ext) : "";
  }

  private static String tempNameFor(String name) {
    SimpleDateFormat fmt = new SimpleDateFormat("yyMMdd_HHmm");
    return PLUGIN_TMP_PREFIX + name + "_" + fmt.format(new Date()) + "_";
  }

  public static File storeInTemp(String pluginName, InputStream in,
      SitePaths sitePaths) throws IOException {
    if (!sitePaths.tmp_dir.exists()) {
      sitePaths.tmp_dir.mkdirs();
    }
    return asTemp(in, tempNameFor(pluginName), ".jar", sitePaths.tmp_dir);
  }

  private ServerPlugin loadJarPlugin(String name, File srcJar,
      FileSnapshot snapshot, File tmp, String pluginCanonicalWebUrl,
      PluginUser pluginUser, File pluginDataDir) throws IOException,
      InvalidPluginException, MalformedURLException, ClassNotFoundException {
    JarFile jarFile = new JarFile(tmp);
    boolean keep = false;
    try {
      Manifest manifest = jarFile.getManifest();
      Plugin.ApiType type = Plugin.getApiType(manifest);

      List<URL> urls = new ArrayList<>(2);
      String overlay = System.getProperty("gerrit.plugin-classes");
      if (overlay != null) {
        File classes = new File(new File(new File(overlay), name), "main");
        if (classes.isDirectory()) {
          log.info(String.format("plugin %s: including %s", name,
              classes.getPath()));
          urls.add(classes.toURI().toURL());
        }
      }
      urls.add(tmp.toURI().toURL());

      ClassLoader pluginLoader =
          new URLClassLoader(urls.toArray(new URL[urls.size()]),
              PluginLoader.parentFor(type));

      ServerPlugin plugin =
          new ServerPlugin(name, pluginCanonicalWebUrl, pluginUser, srcJar,
              snapshot, new JarScanner(srcJar), pluginDataDir, pluginLoader);
      plugin.setCleanupHandle(new CleanupHandle(tmp, jarFile));
      keep = true;
      return plugin;
    } finally {
      if (!keep) {
        jarFile.close();
      }
    }
  }

  private static File asTemp(InputStream in, String prefix, String suffix,
      File dir) throws IOException {
    File tmp = File.createTempFile(prefix, suffix, dir);
    boolean keep = false;
    try {
      FileOutputStream out = new FileOutputStream(tmp);
      try {
        byte[] data = new byte[8192];
        int n;
        while ((n = in.read(data)) > 0) {
          out.write(data, 0, n);
        }
        keep = true;
        return tmp;
      } finally {
        out.close();
      }
    } finally {
      if (!keep) {
        tmp.delete();
      }
    }
  }
}
