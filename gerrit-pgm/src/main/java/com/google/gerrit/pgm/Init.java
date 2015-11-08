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

package com.google.gerrit.pgm;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gerrit.common.IoUtil;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.PluginData;
import com.google.gerrit.pgm.init.BaseInit;
import com.google.gerrit.pgm.init.Browser;
import com.google.gerrit.pgm.init.InitPlugins;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.util.ErrorLogFile;
import com.google.gerrit.server.config.GerritServerConfigModule;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.securestore.SecureStoreClassName;
import com.google.gerrit.server.util.HostPlatform;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.util.Providers;

import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Initialize a new Gerrit installation. */
public class Init extends BaseInit {
  @Option(name = "--batch", usage = "Batch mode; skip interactive prompting")
  private boolean batchMode;

  @Option(name = "--no-auto-start", usage = "Don't automatically start daemon after init")
  private boolean noAutoStart;

  @Option(name = "--skip-plugins", usage = "Don't install plugins")
  private boolean skipPlugins = false;

  @Option(name = "--list-plugins", usage = "List available plugins")
  private boolean listPlugins;

  @Option(name = "--install-plugin", usage = "Install given plugin without asking")
  private List<String> installPlugins;

  @Option(name = "--secure-store-lib",
      usage = "Path to jar providing SecureStore implementation class")
  private String secureStoreLib;

  @Option(name = "--dev",
      usage = "Setup site with default options suitable for developers")
  private boolean dev;

  @Inject
  Browser browser;

  public Init() {
    super(new WarDistribution(), null);
  }

  public Init(Path sitePath) {
    super(sitePath, true, true, new WarDistribution(), null);
    batchMode = true;
    noAutoStart = true;
  }

  @Override
  protected boolean beforeInit(SiteInit init) throws Exception {
    ErrorLogFile.errorOnlyConsole();

    if (!skipPlugins) {
      final List<PluginData> plugins =
          InitPlugins.listPluginsAndRemoveTempFiles(init.site, pluginsDistribution);
      ConsoleUI ui = ConsoleUI.getInstance(false);
      verifyInstallPluginList(ui, plugins);
      if (listPlugins) {
        if (!plugins.isEmpty()) {
          ui.message("Available plugins:\n");
          for (PluginData plugin : plugins) {
            ui.message(" * %s version %s\n", plugin.name, plugin.version);
          }
        } else {
          ui.message("No plugins found.\n");
        }
        return true;
      }
    }
    return false;
  }

  @Override
  protected void afterInit(SiteRun run) throws Exception {
    List<Module> modules = Lists.newArrayList();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Path.class).annotatedWith(SitePath.class).toInstance(getSitePath());
        bind(Browser.class);
        bind(String.class).annotatedWith(SecureStoreClassName.class)
            .toProvider(Providers.of(getConfiguredSecureStoreClass()));
      }
    });
    modules.add(new GerritServerConfigModule());
    Guice.createInjector(modules).injectMembers(this);
    start(run);
  }

  @Override
  protected List<String> getInstallPlugins() {
    return installPlugins;
  }

  @Override
  protected ConsoleUI getConsoleUI() {
    return ConsoleUI.getInstance(batchMode);
  }

  @Override
  protected boolean getAutoStart() {
    return !noAutoStart;
  }

  @Override
  protected boolean skipPlugins() {
    return skipPlugins;
  }

  @Override
  protected boolean isDev() {
    return dev;
  }

  @Override
  protected String getSecureStoreLib() {
    return secureStoreLib;
  }

  void start(SiteRun run) throws Exception {
    if (run.flags.autoStart) {
      if (HostPlatform.isWin32()) {
        System.err.println("Automatic startup not supported on Win32.");

      } else {
        startDaemon(run);
        if (!run.ui.isBatch()) {
          browser.open(PageLinks.ADMIN_PROJECTS);
        }
      }
    }
  }

  void startDaemon(SiteRun run) {
    String[] argv = {run.site.gerrit_sh.toAbsolutePath().toString(), "start"};
    Process proc;
    try {
      System.err.println("Executing " + argv[0] + " " + argv[1]);
      proc = Runtime.getRuntime().exec(argv);
    } catch (IOException e) {
      System.err.println("error: cannot start Gerrit: " + e.getMessage());
      return;
    }

    try {
      proc.getOutputStream().close();
    } catch (IOException e) {
      // Ignored
    }

    IoUtil.copyWithThread(proc.getInputStream(), System.err);
    IoUtil.copyWithThread(proc.getErrorStream(), System.err);

    for (;;) {
      try {
        int rc = proc.waitFor();
        if (rc != 0) {
          System.err.println("error: cannot start Gerrit: exit status " + rc);
        }
        break;
      } catch (InterruptedException e) {
        // retry
      }
    }
  }

  private void verifyInstallPluginList(ConsoleUI ui, List<PluginData> plugins) {
    if (nullOrEmpty(installPlugins) || nullOrEmpty(plugins)) {
      return;
    }
    ArrayList<String> copy = Lists.newArrayList(installPlugins);
    List<String> pluginNames = Lists.transform(plugins, new Function<PluginData, String>() {
      @Override
      public String apply(PluginData input) {
        return input.name;
      }
    });
    copy.removeAll(pluginNames);
    if (!copy.isEmpty()) {
      ui.message("Cannot find plugin(s): %s\n", Joiner.on(", ").join(copy));
      listPlugins = true;
    }
  }

  private static boolean nullOrEmpty(List<?> list) {
    return list == null || list.isEmpty();
  }
}
