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
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.config.CanonicalWebUrl;
import com.google.gerrit.config.ConfigUtil;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.plugins.ServerPluginProvider.PluginDescription;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PluginLoader implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(PluginLoader.class);

  public String getPluginName(Path srcPath) {
    return MoreObjects.firstNonNull(getGerritPluginName(srcPath), PluginUtil.nameOf(srcPath));
  }

  private final Path pluginsDir;
  private final Path dataDir;
  private final Path tempDir;
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
  private final UniversalServerPluginProvider serverPluginFactory;

  @Inject
  public PluginLoader(
      SitePaths sitePaths,
      PluginGuiceEnvironment pe,
      ServerInformationImpl sii,
      PluginUser.Factory puf,
      Provider<PluginCleanerTask> pct,
      @GerritServerConfig Config cfg,
      @CanonicalWebUrl Provider<String> provider,
      PersistentCacheFactory cacheFactory,
      UniversalServerPluginProvider pluginFactory) {
    pluginsDir = sitePaths.plugins_dir;
    dataDir = sitePaths.data_dir;
    tempDir = sitePaths.tmp_dir;
    env = pe;
    srvInfoImpl = sii;
    pluginUserFactory = puf;
    running = Maps.newConcurrentMap();
    disabled = Maps.newConcurrentMap();
    broken = new HashMap<>();
    toCleanup = new ArrayDeque<>();
    cleanupHandles = Maps.newConcurrentMap();
    cleaner = pct;
    urlProvider = provider;
    persistentCacheFactory = cacheFactory;
    serverPluginFactory = pluginFactory;

    remoteAdmin = cfg.getBoolean("plugins", null, "allowRemoteAdmin", false);

    long checkFrequency =
        ConfigUtil.getTimeUnit(
            cfg,
            "plugins",
            null,
            "checkFrequency",
            TimeUnit.MINUTES.toMillis(1),
            TimeUnit.MILLISECONDS);
    if (checkFrequency > 0) {
      scanner = new PluginScannerThread(this, checkFrequency);
    } else {
      scanner = null;
    }
  }

  public boolean isRemoteAdminEnabled() {
    return remoteAdmin;
  }

  public void checkRemoteAdminEnabled() throws MethodNotAllowedException {
    if (!remoteAdmin) {
      throw new MethodNotAllowedException("remote plugin administration is disabled");
    }
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
    }
    List<Plugin> plugins = new ArrayList<>(running.values());
    plugins.addAll(disabled.values());
    return plugins;
  }

  public String installPluginFromStream(String originalName, InputStream in)
      throws IOException, PluginInstallException {
    checkRemoteInstall();

    String fileName = originalName;
    Path tmp = PluginUtil.asTemp(in, ".next_" + fileName + "_", ".tmp", pluginsDir);
    String name = MoreObjects.firstNonNull(getGerritPluginName(tmp), PluginUtil.nameOf(fileName));
    if (!originalName.equals(name)) {
      log.warn(
          String.format(
              "Plugin provides its own name: <%s>, use it instead of the input name: <%s>",
              name, originalName));
    }

    String fileExtension = getExtension(fileName);
    Path dst = pluginsDir.resolve(name + fileExtension);
    synchronized (this) {
      Plugin active = running.get(name);
      if (active != null) {
        fileName = active.getSrcFile().getFileName().toString();
        log.info(String.format("Replacing plugin %s", active.getName()));
        Path old = pluginsDir.resolve(".last_" + fileName);
        Files.deleteIfExists(old);
        Files.move(active.getSrcFile(), old);
      }

      Files.deleteIfExists(pluginsDir.resolve(fileName + ".disabled"));
      Files.move(tmp, dst);
      try {
        Plugin plugin = runPlugin(name, dst, active);
        if (active == null) {
          log.info(String.format("Installed plugin %s", plugin.getName()));
        }
      } catch (PluginInstallException e) {
        Files.deleteIfExists(dst);
        throw e;
      }

      cleanInBackground();
    }

    return name;
  }

  private synchronized void unloadPlugin(Plugin plugin) {
    persistentCacheFactory.onStop(plugin.getName());
    String name = plugin.getName();
    log.info(String.format("Unloading plugin %s, version %s", name, plugin.getVersion()));
    plugin.stop(env);
    env.onStopPlugin(plugin);
    running.remove(name);
    disabled.remove(name);
    toCleanup.add(plugin);
  }

  public void disablePlugins(Set<String> names) {
    if (!isRemoteAdminEnabled()) {
      log.warn("Remote plugin administration is disabled, ignoring disablePlugins(" + names + ")");
      return;
    }

    synchronized (this) {
      for (String name : names) {
        Plugin active = running.get(name);
        if (active == null) {
          continue;
        }

        log.info(String.format("Disabling plugin %s", active.getName()));
        Path off =
            active.getSrcFile().resolveSibling(active.getSrcFile().getFileName() + ".disabled");
        try {
          Files.move(active.getSrcFile(), off);
        } catch (IOException e) {
          log.error("Failed to disable plugin", e);
          // In theory we could still unload the plugin even if the rename
          // failed. However, it would be reloaded on the next server startup,
          // which is probably not what the user expects.
          continue;
        }

        unloadPlugin(active);
        try {
          FileSnapshot snapshot = FileSnapshot.save(off.toFile());
          Plugin offPlugin = loadPlugin(name, off, snapshot);
          disabled.put(name, offPlugin);
        } catch (Throwable e) {
          // This shouldn't happen, as the plugin was loaded earlier.
          log.warn(String.format("Cannot load disabled plugin %s", active.getName()), e.getCause());
        }
      }
      cleanInBackground();
    }
  }

  public void enablePlugins(Set<String> names) throws PluginInstallException {
    if (!isRemoteAdminEnabled()) {
      log.warn("Remote plugin administration is disabled, ignoring enablePlugins(" + names + ")");
      return;
    }

    synchronized (this) {
      for (String name : names) {
        Plugin off = disabled.get(name);
        if (off == null) {
          continue;
        }

        log.info(String.format("Enabling plugin %s", name));
        String n = off.getSrcFile().toFile().getName();
        if (n.endsWith(".disabled")) {
          n = n.substring(0, n.lastIndexOf('.'));
        }
        Path on = pluginsDir.resolve(n);
        try {
          Files.move(off.getSrcFile(), on);
        } catch (IOException e) {
          log.error("Failed to move plugin " + name + " into place", e);
          continue;
        }
        disabled.remove(name);
        runPlugin(name, on, null);
      }
      cleanInBackground();
    }
  }

  private void removeStalePluginFiles() {
    DirectoryStream.Filter<Path> filter =
        new DirectoryStream.Filter<Path>() {
          @Override
          public boolean accept(Path entry) throws IOException {
            return entry.getFileName().toString().startsWith("plugin_");
          }
        };
    try (DirectoryStream<Path> files = Files.newDirectoryStream(tempDir, filter)) {
      for (Path file : files) {
        log.info("Removing stale plugin file: " + file.toFile().getName());
        try {
          Files.delete(file);
        } catch (IOException e) {
          log.error(
              String.format(
                  "Failed to remove stale plugin file %s: %s",
                  file.toFile().getName(), e.getMessage()));
        }
      }
    } catch (IOException e) {
      log.warn("Unable to discover stale plugin files: " + e.getMessage());
    }
  }

  @Override
  public synchronized void start() {
    removeStalePluginFiles();
    Path absolutePath = pluginsDir.toAbsolutePath();
    if (!Files.exists(absolutePath)) {
      log.info(absolutePath + " does not exist; creating");
      try {
        Files.createDirectories(absolutePath);
      } catch (IOException e) {
        log.error(String.format("Failed to create %s: %s", absolutePath, e.getMessage()));
      }
    }
    log.info("Loading plugins from " + absolutePath);
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

  public void reload(List<String> names) throws InvalidPluginException, PluginInstallException {
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
        throw new InvalidPluginException(
            String.format("Plugin(s) \"%s\" not running", Joiner.on("\", \"").join(bad)));
      }

      for (Plugin active : reload) {
        String name = active.getName();
        try {
          log.info(String.format("Reloading plugin %s", name));
          Plugin newPlugin = runPlugin(name, active.getSrcFile(), active);
          log.info(
              String.format(
                  "Reloaded plugin %s, version %s", newPlugin.getName(), newPlugin.getVersion()));
        } catch (PluginInstallException e) {
          log.warn(String.format("Cannot reload plugin %s", name), e.getCause());
          throw e;
        }
      }

      cleanInBackground();
    }
  }

  public synchronized void rescan() {
    SetMultimap<String, Path> pluginsFiles = prunePlugins(pluginsDir);
    if (pluginsFiles.isEmpty()) {
      return;
    }

    syncDisabledPlugins(pluginsFiles);

    Map<String, Path> activePlugins = filterDisabled(pluginsFiles);
    for (Map.Entry<String, Path> entry : jarsFirstSortedPluginsSet(activePlugins)) {
      String name = entry.getKey();
      Path path = entry.getValue();
      String fileName = path.getFileName().toString();
      if (!isUiPlugin(fileName) && !serverPluginFactory.handles(path)) {
        log.warn("No Plugin provider was found that handles this file format: {}", fileName);
        continue;
      }

      FileSnapshot brokenTime = broken.get(name);
      if (brokenTime != null && !brokenTime.isModified(path.toFile())) {
        continue;
      }

      Plugin active = running.get(name);
      if (active != null && !active.isModified(path)) {
        continue;
      }

      if (active != null) {
        log.info(String.format("Reloading plugin %s", active.getName()));
      }

      try {
        Plugin loadedPlugin = runPlugin(name, path, active);
        if (!loadedPlugin.isDisabled()) {
          log.info(
              String.format(
                  "%s plugin %s, version %s",
                  active == null ? "Loaded" : "Reloaded",
                  loadedPlugin.getName(),
                  loadedPlugin.getVersion()));
        }
      } catch (PluginInstallException e) {
        log.warn(String.format("Cannot load plugin %s", name), e.getCause());
      }
    }

    cleanInBackground();
  }

  private void addAllEntries(Map<String, Path> from, TreeSet<Entry<String, Path>> to) {
    Iterator<Entry<String, Path>> it = from.entrySet().iterator();
    while (it.hasNext()) {
      Entry<String, Path> entry = it.next();
      to.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue()));
    }
  }

  private TreeSet<Entry<String, Path>> jarsFirstSortedPluginsSet(Map<String, Path> activePlugins) {
    TreeSet<Entry<String, Path>> sortedPlugins =
        Sets.newTreeSet(
            new Comparator<Entry<String, Path>>() {
              @Override
              public int compare(Entry<String, Path> e1, Entry<String, Path> e2) {
                Path n1 = e1.getValue().getFileName();
                Path n2 = e2.getValue().getFileName();
                return ComparisonChain.start()
                    .compareTrueFirst(isJar(n1), isJar(n2))
                    .compare(n1, n2)
                    .result();
              }

              private boolean isJar(Path n1) {
                return n1.toString().endsWith(".jar");
              }
            });

    addAllEntries(activePlugins, sortedPlugins);
    return sortedPlugins;
  }

  private void syncDisabledPlugins(SetMultimap<String, Path> jars) {
    stopRemovedPlugins(jars);
    dropRemovedDisabledPlugins(jars);
  }

  private Plugin runPlugin(String name, Path plugin, Plugin oldPlugin)
      throws PluginInstallException {
    FileSnapshot snapshot = FileSnapshot.save(plugin.toFile());
    try {
      Plugin newPlugin = loadPlugin(name, plugin, snapshot);
      if (newPlugin.getCleanupHandle() != null) {
        cleanupHandles.put(newPlugin, newPlugin.getCleanupHandle());
      }
      /*
       * Pluggable plugin provider may have assigned a plugin name that could be
       * actually different from the initial one assigned during scan. It is
       * safer then to reassign it.
       */
      name = newPlugin.getName();
      boolean reload = oldPlugin != null && oldPlugin.canReload() && newPlugin.canReload();
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

  private void stopRemovedPlugins(SetMultimap<String, Path> jars) {
    Set<String> unload = Sets.newHashSet(running.keySet());
    for (Map.Entry<String, Collection<Path>> entry : jars.asMap().entrySet()) {
      for (Path path : entry.getValue()) {
        if (!path.getFileName().toString().endsWith(".disabled")) {
          unload.remove(entry.getKey());
        }
      }
    }
    for (String name : unload) {
      unloadPlugin(running.get(name));
    }
  }

  private void dropRemovedDisabledPlugins(SetMultimap<String, Path> jars) {
    Set<String> unload = Sets.newHashSet(disabled.keySet());
    for (Map.Entry<String, Collection<Path>> entry : jars.asMap().entrySet()) {
      for (Path path : entry.getValue()) {
        if (path.getFileName().toString().endsWith(".disabled")) {
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

  private String getExtension(String name) {
    int ext = name.lastIndexOf('.');
    return 0 < ext ? name.substring(ext) : "";
  }

  private Plugin loadPlugin(String name, Path srcPlugin, FileSnapshot snapshot)
      throws InvalidPluginException {
    String pluginName = srcPlugin.getFileName().toString();
    if (isUiPlugin(pluginName)) {
      return loadJsPlugin(name, srcPlugin, snapshot);
    } else if (serverPluginFactory.handles(srcPlugin)) {
      return loadServerPlugin(srcPlugin, snapshot);
    } else {
      throw new InvalidPluginException(
          String.format("Unsupported plugin type: %s", srcPlugin.getFileName()));
    }
  }

  private Path getPluginDataDir(String name) {
    return dataDir.resolve(name);
  }

  private String getPluginCanonicalWebUrl(String name) {
    String canonicalWebUrl = urlProvider.get();
    if (Strings.isNullOrEmpty(canonicalWebUrl)) {
      return "/plugins/" + name;
    }

    String url =
        String.format(
            "%s/plugins/%s/", CharMatcher.is('/').trimTrailingFrom(canonicalWebUrl), name);
    return url;
  }

  private Plugin loadJsPlugin(String name, Path srcJar, FileSnapshot snapshot) {
    return new JsPlugin(name, srcJar, pluginUserFactory.create(name), snapshot);
  }

  private ServerPlugin loadServerPlugin(Path scriptFile, FileSnapshot snapshot)
      throws InvalidPluginException {
    String name = serverPluginFactory.getPluginName(scriptFile);
    return serverPluginFactory.get(
        scriptFile,
        snapshot,
        new PluginDescription(
            pluginUserFactory.create(name),
            getPluginCanonicalWebUrl(name),
            getPluginDataDir(name)));
  }

  // Only one active plugin per plugin name can exist for each plugin name.
  // Filter out disabled plugins and transform the multimap to a map
  private Map<String, Path> filterDisabled(SetMultimap<String, Path> pluginPaths) {
    Map<String, Path> activePlugins = Maps.newHashMapWithExpectedSize(pluginPaths.keys().size());
    for (String name : pluginPaths.keys()) {
      for (Path pluginPath : pluginPaths.asMap().get(name)) {
        if (!pluginPath.getFileName().toString().endsWith(".disabled")) {
          assert !activePlugins.containsKey(name);
          activePlugins.put(name, pluginPath);
        }
      }
    }
    return activePlugins;
  }

  // Scan the $site_path/plugins directory and fetch all files and directories.
  // The Key in returned multimap is the plugin name initially assigned from its filename.
  // Values are the files. Plugins can optionally provide their name in MANIFEST file.
  // If multiple plugin files provide the same plugin name, then only
  // the first plugin remains active and all other plugins with the same
  // name are disabled.
  //
  // NOTE: Bear in mind that the plugin name can be reassigned after load by the
  //       Server plugin provider.
  public SetMultimap<String, Path> prunePlugins(Path pluginsDir) {
    List<Path> pluginPaths = scanPathsInPluginsDirectory(pluginsDir);
    SetMultimap<String, Path> map;
    map = asMultimap(pluginPaths);
    for (String plugin : map.keySet()) {
      Collection<Path> files = map.asMap().get(plugin);
      if (files.size() == 1) {
        continue;
      }
      // retrieve enabled plugins
      Iterable<Path> enabled = filterDisabledPlugins(files);
      // If we have only one (the winner) plugin, nothing to do
      if (!Iterables.skip(enabled, 1).iterator().hasNext()) {
        continue;
      }
      Path winner = Iterables.getFirst(enabled, null);
      assert winner != null;
      // Disable all loser plugins by renaming their file names to
      // "file.disabled" and replace the disabled files in the multimap.
      Collection<Path> elementsToRemove = new ArrayList<>();
      Collection<Path> elementsToAdd = new ArrayList<>();
      for (Path loser : Iterables.skip(enabled, 1)) {
        log.warn(
            String.format(
                "Plugin <%s> was disabled, because"
                    + " another plugin <%s>"
                    + " with the same name <%s> already exists",
                loser, winner, plugin));
        Path disabledPlugin = Paths.get(loser + ".disabled");
        elementsToAdd.add(disabledPlugin);
        elementsToRemove.add(loser);
        try {
          Files.move(loser, disabledPlugin);
        } catch (IOException e) {
          log.warn("Failed to fully disable plugin " + loser, e);
        }
      }
      Iterables.removeAll(files, elementsToRemove);
      Iterables.addAll(files, elementsToAdd);
    }
    return map;
  }

  private List<Path> scanPathsInPluginsDirectory(Path pluginsDir) {
    try {
      return PluginUtil.listPlugins(pluginsDir);
    } catch (IOException e) {
      log.error("Cannot list " + pluginsDir.toAbsolutePath(), e);
      return ImmutableList.of();
    }
  }

  private Iterable<Path> filterDisabledPlugins(Collection<Path> paths) {
    return Iterables.filter(paths, p -> !p.getFileName().toString().endsWith(".disabled"));
  }

  public String getGerritPluginName(Path srcPath) {
    String fileName = srcPath.getFileName().toString();
    if (isUiPlugin(fileName)) {
      return fileName.substring(0, fileName.lastIndexOf('.'));
    }
    if (serverPluginFactory.handles(srcPath)) {
      return serverPluginFactory.getPluginName(srcPath);
    }
    return null;
  }

  private SetMultimap<String, Path> asMultimap(List<Path> plugins) {
    SetMultimap<String, Path> map = LinkedHashMultimap.create();
    for (Path srcPath : plugins) {
      map.put(getPluginName(srcPath), srcPath);
    }
    return map;
  }

  private boolean isUiPlugin(String name) {
    return isPlugin(name, "js") || isPlugin(name, "html");
  }

  private boolean isPlugin(String fileName, String ext) {
    String fullExt = "." + ext;
    return fileName.endsWith(fullExt) || fileName.endsWith(fullExt + ".disabled");
  }

  private void checkRemoteInstall() throws PluginInstallException {
    if (!isRemoteAdminEnabled()) {
      throw new PluginInstallException("remote installation is disabled");
    }
  }
}
