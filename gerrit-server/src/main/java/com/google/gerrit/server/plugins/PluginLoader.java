// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.extensions.webui.JavaScriptPlugin;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Singleton
public class PluginLoader implements LifecycleListener {
  static final String PLUGIN_TMP_PREFIX = "plugin_";
  static final Logger log = LoggerFactory.getLogger(PluginLoader.class);

  public static String getPluginName(File srcFile) throws IOException {
    return Objects.firstNonNull(getGerritPluginName(srcFile), nameOf(srcFile));
  }

  private final File pluginsDir;
  private final File dataDir;
  private final File tmpDir;
  private final PluginGuiceEnvironment env;
  private final ServerInformationImpl srvInfoImpl;
  private final PluginUser.Factory pluginUserFactory;
  private final ConcurrentMap<String, Plugin> running;
  private final ConcurrentMap<String, Plugin> disabled;
  private final Map<String, FileSnapshot> broken;
  private final Map<Plugin, CleanupHandle> cleanupHandles;
  private final Queue<Plugin> toCleanup;
  private final Provider<PluginCleanerTask> cleaner;
  private final PluginScannerThread scanner;
  private final Provider<String> urlProvider;
  private final PersistentCacheFactory persistentCacheFactory;
  private final boolean remoteAdmin;

  @Inject
  public PluginLoader(SitePaths sitePaths,
      PluginGuiceEnvironment pe,
      ServerInformationImpl sii,
      PluginUser.Factory puf,
      Provider<PluginCleanerTask> pct,
      @GerritServerConfig Config cfg,
      @CanonicalWebUrl Provider<String> provider,
      PersistentCacheFactory cacheFactory) {
    pluginsDir = sitePaths.plugins_dir;
    dataDir = sitePaths.data_dir;
    tmpDir = sitePaths.tmp_dir;
    env = pe;
    srvInfoImpl = sii;
    pluginUserFactory = puf;
    running = Maps.newConcurrentMap();
    disabled = Maps.newConcurrentMap();
    broken = Maps.newHashMap();
    toCleanup = Queues.newArrayDeque();
    cleanupHandles = Maps.newConcurrentMap();
    cleaner = pct;
    urlProvider = provider;
    persistentCacheFactory = cacheFactory;

    remoteAdmin =
        cfg.getBoolean("plugins", null, "allowRemoteAdmin", false);

    long checkFrequency = ConfigUtil.getTimeUnit(cfg,
        "plugins", null, "checkFrequency",
        TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS);
    if (checkFrequency > 0) {
      scanner = new PluginScannerThread(this, checkFrequency);
    } else {
      scanner = null;
    }
  }

  public boolean isRemoteAdminEnabled() {
    return remoteAdmin;
  }

  public Plugin get(String name) {
    Plugin p = running.get(name);
    if (p != null) {
      return p;
    }
    return disabled.get(name);
  }

  public Iterable<Plugin> getPlugins(boolean all) {
    if (!all) {
      return running.values();
    } else {
      List<Plugin> plugins = new ArrayList<>(running.values());
      plugins.addAll(disabled.values());
      return plugins;
    }
  }

  public void installPluginFromStream(String originalName, InputStream in)
      throws IOException, PluginInstallException {
    checkRemoteInstall();

    String fileName = originalName;
    if (!(fileName.endsWith(".jar") || fileName.endsWith(".js"))) {
      fileName += ".jar";
    }
    File tmp = asTemp(in, ".next_" + fileName + "_", ".tmp", pluginsDir);
    String name = Objects.firstNonNull(getGerritPluginName(tmp),
        nameOf(fileName));
    if (!originalName.equals(name)) {
      log.warn(String.format("Plugin provides its own name: <%s>,"
          + " use it instead of the input name: <%s>",
          name, originalName));
    }

    String fileExtension = getExtension(fileName);
    File dst = new File(pluginsDir, name + fileExtension);
    synchronized (this) {
      Plugin active = running.get(name);
      if (active != null) {
        fileName = active.getSrcFile().getName();
        log.info(String.format("Replacing plugin %s", active.getName()));
        File old = new File(pluginsDir, ".last_" + fileName);
        old.delete();
        active.getSrcFile().renameTo(old);
      }

      new File(pluginsDir, fileName + ".disabled").delete();
      tmp.renameTo(dst);
      try {
        Plugin plugin = runPlugin(name, dst, active);
        if (active == null) {
          log.info(String.format("Installed plugin %s", plugin.getName()));
        }
      } catch (PluginInstallException e) {
        dst.delete();
        throw e;
      }

      cleanInBackground();
    }
  }

  public static File storeInTemp(String pluginName, InputStream in,
      SitePaths sitePaths) throws IOException {
    if (!sitePaths.tmp_dir.exists()) {
      sitePaths.tmp_dir.mkdirs();
    }
    return asTemp(in, tempNameFor(pluginName), ".jar", sitePaths.tmp_dir);
  }

  private static File asTemp(InputStream in,
      String prefix, String suffix,
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

  synchronized private void unloadPlugin(Plugin plugin) {
    persistentCacheFactory.onStop(plugin);
    String name = plugin.getName();
    log.info(String.format("Unloading plugin %s", name));
    plugin.stop(env);
    env.onStopPlugin(plugin);
    running.remove(name);
    disabled.remove(name);
    toCleanup.add(plugin);
  }

  public void disablePlugins(Set<String> names) {
    if (!isRemoteAdminEnabled()) {
      log.warn("Remote plugin administration is disabled,"
          + " ignoring disablePlugins(" + names + ")");
      return;
    }

    synchronized (this) {
      for (String name : names) {
        Plugin active = running.get(name);
        if (active == null) {
          continue;
        }

        log.info(String.format("Disabling plugin %s", active.getName()));
        File off = new File(active.getSrcFile() + ".disabled");
        active.getSrcFile().renameTo(off);

        unloadPlugin(active);
        try {
          FileSnapshot snapshot = FileSnapshot.save(off);
          Plugin offPlugin = loadPlugin(name, off, snapshot);
          disabled.put(name, offPlugin);
        } catch (Throwable e) {
          // This shouldn't happen, as the plugin was loaded earlier.
          log.warn(String.format(
              "Cannot load disabled plugin %s", active.getName()),
              e.getCause());
        }
      }
      cleanInBackground();
    }
  }

  public void enablePlugins(Set<String> names) throws PluginInstallException {
    if (!isRemoteAdminEnabled()) {
      log.warn("Remote plugin administration is disabled,"
          + " ignoring enablePlugins(" + names + ")");
      return;
    }

    synchronized (this) {
      for (String name : names) {
        Plugin off = disabled.get(name);
        if (off == null) {
          continue;
        }

        log.info(String.format("Enabling plugin %s", name));
        String n = off.getSrcFile().getName();
        if (n.endsWith(".disabled")) {
          n = n.substring(0, n.lastIndexOf('.'));
        }
        File on = new File(pluginsDir, n);
        off.getSrcFile().renameTo(on);

        disabled.remove(name);
        runPlugin(name, on, null);
      }
      cleanInBackground();
    }
  }

  @Override
  public synchronized void start() {
    log.info("Loading plugins from " + pluginsDir.getAbsolutePath());
    srvInfoImpl.state = ServerInformation.State.STARTUP;
    rescan();
    srvInfoImpl.state = ServerInformation.State.RUNNING;
    if (scanner != null) {
      scanner.start();
    }
  }

  @Override
  public void stop() {
    if (scanner != null) {
      scanner.end();
    }
    srvInfoImpl.state = ServerInformation.State.SHUTDOWN;
    synchronized (this) {
      for (Plugin p : running.values()) {
        unloadPlugin(p);
      }
      running.clear();
      disabled.clear();
      broken.clear();
      if (!toCleanup.isEmpty()) {
        System.gc();
        processPendingCleanups();
      }
    }
  }

  public void reload(List<String> names)
      throws InvalidPluginException, PluginInstallException {
    synchronized (this) {
      List<Plugin> reload = Lists.newArrayListWithCapacity(names.size());
      List<String> bad = Lists.newArrayListWithExpectedSize(4);
      for (String name : names) {
        Plugin active = running.get(name);
        if (active != null) {
          reload.add(active);
        } else {
          bad.add(name);
        }
      }
      if (!bad.isEmpty()) {
        throw new InvalidPluginException(String.format(
            "Plugin(s) \"%s\" not running",
            Joiner.on("\", \"").join(bad)));
      }

      for (Plugin active : reload) {
        String name = active.getName();
        try {
          log.info(String.format("Reloading plugin %s", name));
          runPlugin(name, active.getSrcFile(), active);
        } catch (PluginInstallException e) {
          log.warn(String.format("Cannot reload plugin %s", name), e.getCause());
          throw e;
        }
      }

      cleanInBackground();
    }
  }

  public synchronized void rescan() {
    Multimap<String, File> jars = prunePlugins(pluginsDir);
    if (jars.isEmpty()) {
      return;
    }

    syncDisabledPlugins(jars);

    Map<String, File> activePlugins = filterDisabled(jars);
    for (Map.Entry<String, File> entry : activePlugins.entrySet()) {
      String name = entry.getKey();
      File jar = entry.getValue();
      FileSnapshot brokenTime = broken.get(name);
      if (brokenTime != null && !brokenTime.isModified(jar)) {
        continue;
      }

      Plugin active = running.get(name);
      if (active != null && !active.isModified(jar)) {
        continue;
      }

      if (active != null) {
        log.info(String.format("Reloading plugin %s, version %s",
            active.getName(), active.getVersion()));
      }

      try {
        Plugin loadedPlugin = runPlugin(name, jar, active);
        if (active == null && !loadedPlugin.isDisabled()) {
          log.info(String.format("Loaded plugin %s, version %s",
              loadedPlugin.getName(), loadedPlugin.getVersion()));
        }
      } catch (PluginInstallException e) {
        log.warn(String.format("Cannot load plugin %s", name), e.getCause());
      }
    }

    cleanInBackground();
  }

  private void syncDisabledPlugins(Multimap<String, File> jars) {
    stopRemovedPlugins(jars);
    dropRemovedDisabledPlugins(jars);
  }

  private Plugin runPlugin(String name, File plugin, Plugin oldPlugin)
      throws PluginInstallException {
    FileSnapshot snapshot = FileSnapshot.save(plugin);
    try {
      Plugin newPlugin = loadPlugin(name, plugin, snapshot);
      boolean reload = oldPlugin != null
          && oldPlugin.canReload()
          && newPlugin.canReload();
      if (!reload && oldPlugin != null) {
        unloadPlugin(oldPlugin);
      }
      if (!newPlugin.isDisabled()) {
        newPlugin.start(env);
      }
      if (reload) {
        env.onReloadPlugin(oldPlugin, newPlugin);
        unloadPlugin(oldPlugin);
      } else if (!newPlugin.isDisabled()) {
        env.onStartPlugin(newPlugin);
      }
      if (!newPlugin.isDisabled()) {
        running.put(name, newPlugin);
      } else {
        disabled.put(name, newPlugin);
      }
      broken.remove(name);
      return newPlugin;
    } catch (Throwable err) {
      broken.put(name, snapshot);
      throw new PluginInstallException(err);
    }
  }

  private void stopRemovedPlugins(Multimap<String, File> jars) {
    Set<String> unload = Sets.newHashSet(running.keySet());
    for (Map.Entry<String, Collection<File>> entry : jars.asMap().entrySet()) {
      for (File file : entry.getValue()) {
        if (!file.getName().endsWith(".disabled")) {
          unload.remove(entry.getKey());
        }
      }
    }
    for (String name : unload) {
      unloadPlugin(running.get(name));
    }
  }

  private void dropRemovedDisabledPlugins(Multimap<String, File> jars) {
    Set<String> unload = Sets.newHashSet(disabled.keySet());
    for (Map.Entry<String, Collection<File>> entry : jars.asMap().entrySet()) {
      for (File file : entry.getValue()) {
        if (file.getName().endsWith(".disabled")) {
          unload.remove(entry.getKey());
        }
      }
    }
    for (String name : unload) {
      disabled.remove(name);
    }
  }

  synchronized int processPendingCleanups() {
    Iterator<Plugin> iterator = toCleanup.iterator();
    while (iterator.hasNext()) {
      Plugin plugin = iterator.next();
      iterator.remove();

      CleanupHandle cleanupHandle = cleanupHandles.remove(plugin);
      if (cleanupHandle != null) {
        cleanupHandle.cleanup();
      }
    }
    return toCleanup.size();
  }

  private void cleanInBackground() {
    int cnt = toCleanup.size();
    if (0 < cnt) {
      cleaner.get().clean(cnt);
    }
  }

  public static String nameOf(File plugin) {
    return nameOf(plugin.getName());
  }

  private static String nameOf(String name) {
    if (name.endsWith(".disabled")) {
      name = name.substring(0, name.lastIndexOf('.'));
    }
    int ext = name.lastIndexOf('.');
    return 0 < ext ? name.substring(0, ext) : name;
  }

  private static String getExtension(File file) {
    return getExtension(file.getName());
  }

  private static String getExtension(String name) {
    int ext = name.lastIndexOf('.');
    return 0 < ext ? name.substring(ext) : "";
  }

  private Plugin loadPlugin(String name, File srcPlugin, FileSnapshot snapshot)
      throws IOException, ClassNotFoundException, InvalidPluginException {
    String pluginName = srcPlugin.getName();
    if (isJarPlugin(pluginName)) {
      File tmp;
      FileInputStream in = new FileInputStream(srcPlugin);
      String extension = getExtension(srcPlugin);
      try {
        tmp = asTemp(in, tempNameFor(name), extension, tmpDir);
      } finally {
        in.close();
      }
      return loadJarPlugin(name, srcPlugin, snapshot, tmp);
    } else if (isJsPlugin(pluginName)) {
      return loadJsPlugin(name, srcPlugin, snapshot);
    } else {
      throw new InvalidPluginException(String.format(
          "Unsupported plugin type: %s", srcPlugin.getName()));
    }
  }

  private Plugin loadJarPlugin(String name, File srcJar, FileSnapshot snapshot,
      File tmp) throws IOException, InvalidPluginException,
      MalformedURLException, ClassNotFoundException {
    JarFile jarFile = new JarFile(tmp);
    boolean keep = false;
    try {
      Manifest manifest = jarFile.getManifest();
      Plugin.ApiType type = Plugin.getApiType(manifest);
      Attributes main = manifest.getMainAttributes();
      String sysName = main.getValue("Gerrit-Module");
      String sshName = main.getValue("Gerrit-SshModule");
      String httpName = main.getValue("Gerrit-HttpModule");

      if (!Strings.isNullOrEmpty(sshName) && type != Plugin.ApiType.PLUGIN) {
        throw new InvalidPluginException(String.format(
            "Using Gerrit-SshModule requires Gerrit-ApiType: %s",
            Plugin.ApiType.PLUGIN));
      }

      List<URL> urls = new ArrayList<>(2);
      String overlay = System.getProperty("gerrit.plugin-classes");
      if (overlay != null) {
        File classes = new File(new File(new File(overlay), name), "main");
        if (classes.isDirectory()) {
          log.info(String.format(
              "plugin %s: including %s",
              name, classes.getPath()));
          urls.add(classes.toURI().toURL());
        }
      }
      urls.add(tmp.toURI().toURL());

      ClassLoader pluginLoader = new URLClassLoader(
          urls.toArray(new URL[urls.size()]),
          parentFor(type));
      Class<? extends Module> sysModule = load(sysName, pluginLoader);
      Class<? extends Module> sshModule = load(sshName, pluginLoader);
      Class<? extends Module> httpModule = load(httpName, pluginLoader);

      String url = String.format("%s/plugins/%s/",
          CharMatcher.is('/').trimTrailingFrom(urlProvider.get()),
          name);

      Plugin plugin = new ServerPlugin(name, url,
          pluginUserFactory.create(name),
          srcJar, snapshot,
          jarFile, manifest,
          new File(dataDir, name), type, pluginLoader,
          sysModule, sshModule, httpModule);
      cleanupHandles.put(plugin, new CleanupHandle(tmp, jarFile));
      keep = true;
      return plugin;
    } finally {
      if (!keep) {
        jarFile.close();
      }
    }
  }

  private Plugin loadJsPlugin(String name, File srcJar, FileSnapshot snapshot) {
    return new JsPlugin(name, srcJar, pluginUserFactory.create(name), snapshot);
  }

  private static ClassLoader parentFor(Plugin.ApiType type)
      throws InvalidPluginException {
    switch (type) {
      case EXTENSION:
        return PluginName.class.getClassLoader();
      case PLUGIN:
        return PluginLoader.class.getClassLoader();
      case JS:
        return JavaScriptPlugin.class.getClassLoader();
      default:
        throw new InvalidPluginException("Unsupported ApiType " + type);
    }
  }

  private static String tempNameFor(String name) {
    SimpleDateFormat fmt = new SimpleDateFormat("yyMMdd_HHmm");
    return PLUGIN_TMP_PREFIX + name + "_" + fmt.format(new Date()) + "_";
  }

  private static Class<? extends Module> load(String name, ClassLoader pluginLoader)
      throws ClassNotFoundException {
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

  // Only one active plugin per plugin name can exist for each plugin name.
  // Filter out disabled plugins and transform the multimap to a map
  private static Map<String, File> filterDisabled(
      Multimap<String, File> jars) {
    Map<String, File> activePlugins = Maps.newHashMapWithExpectedSize(
        jars.keys().size());
    for (String name : jars.keys()) {
      for (File jar : jars.asMap().get(name)) {
        if (!jar.getName().endsWith(".disabled")) {
          assert(!activePlugins.containsKey(name));
          activePlugins.put(name, jar);
        }
      }
    }
    return activePlugins;
  }

  // Scan the $site_path/plugins directory and fetch all files that end
  // with *.jar. The Key in returned multimap is the plugin name. Values are
  // the files. Plugins can optionally provide their name in MANIFEST file.
  // If multiple plugin files provide the same plugin name, then only
  // the first plugin remains active and all other plugins with the same
  // name are disabled.
  public static Multimap<String, File> prunePlugins(File pluginsDir) {
    List<File> jars = scanJarsInPluginsDirectory(pluginsDir);
    Multimap<String, File> map;
    try {
      map = asMultimap(jars);
      for (String plugin : map.keySet()) {
        Collection<File> files = map.asMap().get(plugin);
        if (files.size() == 1) {
          continue;
        }
        // retrieve enabled plugins
        Iterable<File> enabled = filterDisabledPlugins(
            files);
        // If we have only one (the winner) plugin, nothing to do
        if (!Iterables.skip(enabled, 1).iterator().hasNext()) {
          continue;
        }
        File winner = Iterables.getFirst(enabled, null);
        assert(winner != null);
        // Disable all loser plugins by renaming their file names to
        // "file.disabled" and replace the disabled files in the multimap.
        Collection<File> elementsToRemove = Lists.newArrayList();
        Collection<File> elementsToAdd = Lists.newArrayList();
        for (File loser : Iterables.skip(enabled, 1)) {
          log.warn(String.format("Plugin <%s> was disabled, because"
               + " another plugin <%s>"
               + " with the same name <%s> already exists",
               loser, winner, plugin));
          File disabledPlugin = new File(loser + ".disabled");
          elementsToAdd.add(disabledPlugin);
          elementsToRemove.add(loser);
          loser.renameTo(disabledPlugin);
        }
        Iterables.removeAll(files, elementsToRemove);
        Iterables.addAll(files, elementsToAdd);
      }
    } catch (IOException e) {
      log.warn("Cannot prune plugin list",
          e.getCause());
      return LinkedHashMultimap.create();
    }
    return map;
  }

  private static List<File> scanJarsInPluginsDirectory(File pluginsDir) {
    if (pluginsDir == null || !pluginsDir.exists()) {
      return Collections.emptyList();
    }
    File[] matches = pluginsDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        String n = pathname.getName();
        return (isJarPlugin(n) || isJsPlugin(n))
            && !n.startsWith(".last_")
            && !n.startsWith(".next_")
            && pathname.isFile();
      }
    });
    if (matches == null) {
      log.error("Cannot list " + pluginsDir.getAbsolutePath());
      return Collections.emptyList();
    }
    return Arrays.asList(matches);
  }

  private static Iterable<File> filterDisabledPlugins(
      Collection<File> files) {
    return Iterables.filter(files, new Predicate<File>() {
      @Override
      public boolean apply(File file) {
        return !file.getName().endsWith(".disabled");
      }
    });
  }

  public static String getGerritPluginName(File srcFile) throws IOException {
    String fileName = srcFile.getName();
    if (isJarPlugin(fileName)) {
      JarFile jarFile = new JarFile(srcFile);
      try {
        return jarFile.getManifest().getMainAttributes()
            .getValue("Gerrit-PluginName");
      } finally {
        jarFile.close();
      }
    }
    if (isJsPlugin(fileName)) {
      return fileName.substring(0, fileName.length() - 3);
    }
    return null;
  }

  private static Multimap<String, File> asMultimap(List<File> plugins)
      throws IOException {
    Multimap<String, File> map = LinkedHashMultimap.create();
    for (File srcFile : plugins) {
      map.put(getPluginName(srcFile), srcFile);
    }
    return map;
  }

  private static boolean isJarPlugin(String name) {
    return isPlugin(name, "jar");
  }

  private static boolean isJsPlugin(String name) {
    return isPlugin(name, "js");
  }

  private static boolean isPlugin(String fileName, String ext) {
    String fullExt = "." + ext;
    return fileName.endsWith(fullExt) || fileName.endsWith(fullExt + ".disabled");
  }

  private void checkRemoteInstall() throws PluginInstallException {
    if (!isRemoteAdminEnabled()) {
      throw new PluginInstallException("remote installation is disabled");
    }
  }
}
