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

import java.io.File;
import java.util.List;
import java.util.Set;

@Singleton
public class RequiredPluginLoader {
  public static Injector loadAndInjectPluginModules(Injector cfgInjector) {
    return cfgInjector.createChildInjector(
        cfgInjector.getInstance(LoadPluginsModule.class));
  }

  static class LoadPluginsModule extends AbstractModule {
    private final ImmutableCollection<PluginBuilder> plugins;
    private final List<Module> modules;

    @Inject
    LoadPluginsModule(Injector cfgInjector, RequiredPluginLoader loader)
        throws PluginInstallException {
      plugins = loader.load();
      modules = Lists.newArrayListWithCapacity(plugins.size());
      for (PluginBuilder plugin : plugins) {
        plugin.setup();
        modules.add(cfgInjector.getInstance(plugin.getSysModuleClass()));
      }
    }

    @Override
    protected void configure() {
      bind(new TypeLiteral<ImmutableCollection<PluginBuilder>>() {})
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

  private ImmutableCollection<PluginBuilder> load()
      throws PluginInstallException {
    if (sitePaths.plugins_dir == null || !sitePaths.plugins_dir.exists()) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<PluginBuilder> plugins = ImmutableList.builder();
    for (String name : names) {
      plugins.add(load(name));
    }
    return plugins.build();
  }

  private PluginBuilder load(String name) throws PluginInstallException {
    File file = new File(sitePaths.plugins_dir, name + ".jar");
    if (!file.exists() || !file.isFile()) {
      throw new PluginInstallException(String.format(
          "%s/%s.jar not found for required plugin %s",
          sitePaths.plugins_dir.getPath(), name, name));
    }

    return new PluginBuilder(sitePaths, name, file, true);
  }
}
