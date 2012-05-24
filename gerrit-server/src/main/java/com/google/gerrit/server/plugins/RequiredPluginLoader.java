// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.gerrit.server.plugins;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import org.eclipse.jgit.lib.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Set;

@Singleton
public class RequiredPluginLoader {
  private static final Logger log =
      LoggerFactory.getLogger(RequiredPluginLoader.class);

  /**
   * Load required plugins into an injector.
   * <p>
   * For each plugin specified in {@code plugins.required}, load the plugin's
   * {@code Gerrit-StartupModule} (if applicable), and wrap the given injector
   * with one containing all the startup modules.
   *
   * @param cfgInjector original config injector
   * @return new child injector containing required plugins' startup modules.
   */
  public static Injector loadAndInjectPluginModules(Injector cfgInjector) {
    return cfgInjector.createChildInjector(
        cfgInjector.getInstance(LoadPluginsModule.class));
  }

  static class LoadPluginsModule extends AbstractModule {
    private final ImmutableCollection<Plugin> plugins;
    private final List<Module> modules;

    @Inject
    LoadPluginsModule(Injector cfgInjector, RequiredPluginLoader loader)
        throws PluginInstallException {
      plugins = loader.load();
      modules = Lists.newArrayListWithCapacity(plugins.size());
      for (Plugin plugin : plugins) {
        plugin.loadStartup();
        log.info(String.format("Loaded %s for plugin %s",
            Plugin.ModuleType.STARTUP, plugin.getName()));
        modules.add(cfgInjector.getInstance(plugin.getStartupModuleClass()));
      }
    }

    @Override
    protected void configure() {
      bind(new TypeLiteral<ImmutableCollection<Plugin>>() {})
          .annotatedWith(RequiredPlugins.class).toInstance(plugins);
      for (Module module : modules) {
        install(module);
      }
    }
  }

  private final SitePaths sitePaths;
  private final Set<String> names;

  @Inject
  RequiredPluginLoader(SitePaths sp,
      @GerritServerConfig Config cfg) {
    sitePaths = sp;
    names = Sets.newHashSet(cfg.getStringList("plugins", null, "required"));
  }

  private ImmutableCollection<Plugin> load() throws PluginInstallException {
    if (sitePaths.plugins_dir == null || !sitePaths.plugins_dir.exists()) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<Plugin> plugins = ImmutableList.builder();
    for (String name : names) {
      plugins.add(load(name));
    }
    return plugins.build();
  }

  private Plugin load(String name) throws PluginInstallException {
    File file = new File(sitePaths.plugins_dir, name + ".jar");
    if (!file.exists() || !file.isFile()) {
      throw new PluginInstallException(String.format(
          "%s/%s.jar not found for required plugin %s",
          sitePaths.plugins_dir.getPath(), name, name));
    }
    return new Plugin(sitePaths, name, file, true);
  }
}
