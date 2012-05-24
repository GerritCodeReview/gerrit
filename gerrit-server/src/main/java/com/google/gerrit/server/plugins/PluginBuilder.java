// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.gerrit.server.plugins;

import static com.google.gerrit.server.plugins.PluginLoader.asTemp;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugins.Plugin.ModuleName;
import com.google.inject.Module;

import org.eclipse.jgit.storage.file.FileSnapshot;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * State of a required plugin, which is loaded before other plugins.
 * <p>
 * {@link RequiredPluginLoader} starts the plugin loading process, which is
 * later finished by {@link PluginLoader}. This class stores state in between
 * the two loading phases.
 */
class PluginBuilder {
  private final SitePaths sitePaths;

  private final String name;
  private final File file;
  private final FileSnapshot snapshot;
  private final boolean required;

  private JarFile jar;
  private File tmp;
  private ClassLoader pluginLoader;
  private Manifest manifest;
  private Class<? extends Module> sysModule;

  PluginBuilder(SitePaths sitePaths, String name, File file, boolean required) {
    this.sitePaths = sitePaths;
    this.name = name;
    this.file = file;
    this.snapshot = FileSnapshot.save(file);
    this.required = required;
  }

  void setup() throws PluginInstallException {
    if (this.jar != null) {
      return;
    }
    boolean keep = false;
    JarFile jar = null;
    try {
      FileInputStream in = new FileInputStream(file);
      try {
        tmp = asTemp(in, tempNameFor(name), ".jar", sitePaths.tmp_dir);
      } finally {
        in.close();
      }

      jar = new JarFile(tmp);
      manifest = jar.getManifest();

      if (required) {
        if (Plugin.getApiType(manifest) != Plugin.ApiType.PLUGIN) {
          throw new InvalidPluginException(String.format(
              "Required plugins must have Gerrit-ApiType: %s",
              Plugin.ApiType.PLUGIN));
        }
        String moduleName = ModuleName.CORE.from(manifest);
        if (Strings.isNullOrEmpty(moduleName)) {
          throw new InvalidPluginException(
              "Required plugins must specify a " + ModuleName.CORE);
        }
      }

      Plugin.ApiType type = Plugin.getApiType(manifest);
      URL[] urls = {tmp.toURI().toURL()};
      ClassLoader parentLoader = parentFor(type);
      pluginLoader = new URLClassLoader(urls, parentLoader);
      sysModule = load(ModuleName.CORE);
      this.jar = jar;
      keep = true;
    } catch (Throwable t) {
      throw new PluginInstallException(t);
    } finally {
      if (!keep) {
        try {
          jar.close();
        } catch (IOException e) {
          throw new PluginInstallException(e);
        }
      }
    }
  }

  String getName() {
    return name;
  }

  FileSnapshot getSnapshot() {
    return snapshot;
  }

  Class<? extends Module> getSysModuleClass() {
    return sysModule;
  }

  Plugin build(PluginLoader loader)
      throws IOException, ClassNotFoundException, InvalidPluginException,
      PluginInstallException {
    setup();
    boolean keep = false;
    try {
      String sshName = ModuleName.SSH.from(manifest);
      String httpName = ModuleName.HTTP.from(manifest);

      Plugin.ApiType type = Plugin.getApiType(manifest);
      if (!Strings.isNullOrEmpty(sshName) && type != Plugin.ApiType.PLUGIN) {
        throw new InvalidPluginException(String.format(
            "Using %s requires Gerrit-ApiType: %s", ModuleName.SSH,
            Plugin.ApiType.PLUGIN));
      }

      if (loader != null) {
        loader.addCleanupHandle(tmp, jar, pluginLoader);
      }
      Class<? extends Module> sshModule = load(ModuleName.SSH);
      Class<? extends Module> httpModule = load(ModuleName.HTTP);
      keep = true;
      return new Plugin(name, file, snapshot, jar, manifest,
          new File(sitePaths.data_dir, name), type, pluginLoader, sysModule, sshModule,
          httpModule);
    } finally {
      if (!keep) {
        jar.close();
      }
    }
  }

  private Class<? extends Module> load(ModuleName type)
      throws ClassNotFoundException {
    String name = type.from(manifest);
    if (Strings.isNullOrEmpty(name)) {
      return null;
    }

    @SuppressWarnings("unchecked")
    Class<? extends Module> clazz =
        (Class<? extends Module>) Class.forName(name, false, pluginLoader);
    if (!Module.class.isAssignableFrom(clazz)) {
      throw new ClassCastException(String.format(
          "Class %s does not implement %s",
          name, Module.class.getName()));
    }
    return clazz;
  }

  private static String tempNameFor(String name) {
    SimpleDateFormat fmt = new SimpleDateFormat("yyMMdd_HHmm");
    return "plugin_" + name + "_" + fmt.format(new Date()) + "_";
  }

  private static ClassLoader parentFor(Plugin.ApiType type)
      throws InvalidPluginException {
    switch (type) {
      case EXTENSION:
        return PluginName.class.getClassLoader();
      case PLUGIN:
        return PluginLoader.class.getClassLoader();
      default:
        throw new InvalidPluginException("Unsupported ApiType " + type);
    }
  }
}
