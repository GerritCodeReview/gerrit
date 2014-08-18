/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.gerrit.gwtdebug;

import com.google.gerrit.pgm.Daemon;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gerrit.util.cli.OptionHandlers;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.dev.codeserver.AppSpace;
import com.google.gwt.dev.codeserver.ModuleState;
import com.google.gwt.dev.codeserver.Modules;
import com.google.gwt.dev.codeserver.Options;
import com.google.gwt.dev.codeserver.Recompiler;
import com.google.gwt.dev.codeserver.SourceHandler;
import com.google.gwt.dev.codeserver.WebServer;
import com.google.gwt.dev.util.log.PrintWriterTreeLogger;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class GerritSDMLauncher {
  private static final Logger log = LoggerFactory.getLogger(GerritSDMLauncher.class);

  @Option(name="-noprecompile")
  boolean noPrecompile;

  @Option(name="-src")
  File srcDir;

  @Option(name="-workDir")
  File workDir;

  @Argument
  List<String> moduleNames;

  /**
   * Starts the code server with the given command line options. To shut it down, see
   * {@link WebServer#stop}.
   *
   * <p>Only one code server should be started at a time because the GWT compiler uses
   * a lot of static variables.</p>
   */
  private WebServer startServer(Options options) throws IOException, UnableToCompleteException {
    PrintWriterTreeLogger logger = new PrintWriterTreeLogger();
    logger.setMaxDetail(TreeLogger.Type.INFO);

    Modules modules = makeModules(options, logger);

    SourceHandler sourceHandler = new SourceHandler(modules, logger);

    WebServer webServer = new WebServer(sourceHandler, modules,
        options.getBindAddress(), options.getPort(), logger);
    webServer.start();

    return webServer;
  }

  /**
   * Configures and compiles all the modules NoPrecompile option is false).
   */
  private Modules makeModules(Options options, PrintWriterTreeLogger logger)
      throws IOException, UnableToCompleteException {

    File workDir = ensureWorkDir();
    log.info("workDir: " + workDir);

    Modules modules = new Modules();
    for (String moduleName : moduleNames) {
      AppSpace appSpace = AppSpace.create(new File(workDir, moduleName));

      Recompiler recompiler = new Recompiler(appSpace, moduleName, options, logger);
      modules.addModuleState(new ModuleState(recompiler, logger, options.getNoPrecompile()));
    }
    return modules;
  }

  /**
   * Ensures that we have a work directory. If specified via a flag, the
   * directory must already exist. Otherwise, create a temp directory.
   */
  private File ensureWorkDir() throws IOException {
    if (!workDir.isDirectory()) {
      throw new IOException("workspace directory doesn't exist: " + workDir);
    }
    return workDir;
  }

  public static void main(String[] argv) throws Exception {
    GerritSDMLauncher launcher = new GerritSDMLauncher();
    launcher.mainImpl(argv);
  }

  private int mainImpl(String[] argv) {
    final CmdLineParser clp = new CmdLineParser(OptionHandlers.empty(), this);
    List<String> sdmLauncherOptions = new ArrayList<>();
    List<String> daemonLauncherOptions = new ArrayList<>();

    // TODO(davdo): figure out how to do that with args4j
    boolean startDaemon = false;
    int i = 0;
    for (; i < argv.length; i++) {
      if (!argv[i].equals("--")) {
        sdmLauncherOptions.add(argv[i]);
      } else {
        startDaemon = true;
        break;
      }
    }
    if (startDaemon) {
      ++i;
      for (; i < argv.length; i++) {
        daemonLauncherOptions.add(argv[i]);
      }
    }

    try {
      clp.parseArgument(sdmLauncherOptions.toArray(
          new String[sdmLauncherOptions.size()]));
    } catch (CmdLineException err) {
      if (!clp.wasHelpRequestedByOption()) {
        log.error("fatal: " + err.getMessage());
        return 1;
      }
    }

    Options options = new Options();
    options.setNoPrecompile(noPrecompile);
    options.setModuleNames(moduleNames);
    options.setWorkDir(workDir);
    options.setSourcePath(Collections.singletonList(srcDir));

    if (options.isCompileTest()) {
      PrintWriterTreeLogger logger = new PrintWriterTreeLogger();
      logger.setMaxDetail(options.getLogLevel());

      Modules modules;

      try {
        modules = makeModules(options, logger);
      } catch (Throwable t) {
        log.error("Codeserver: cannot load modules", t);
        return 1;
      }

      int retries = options.getCompileTestRecompiles();
      for (int j = 0; i < retries; i++) {
        log.info("\n### Recompile " + (j + 1) + "\n");
        try {
          modules.defaultCompileAll(options.getNoPrecompile());
        } catch (Throwable t) {
          log.error("FAIL", t);
          return 1;
        }
      }

      log.info("PASS");
      return 0;
    }

    try {
      startServer(options);
      log.info("The code server is ready.");
      log.info("Next, visit: " + "http://" + options.getPreferredHost()
          + ":" + options.getPort());
    } catch (Throwable e) {
      log.error("Codeserver: unable to start", e);
      return 1;
    }

    try {
      if (startDaemon) {
        Daemon daemon = new Daemon();
        daemon.main(daemonLauncherOptions.toArray(
            new String[daemonLauncherOptions.size()]));
      }
    } catch (Exception e) {
      log.error("Codeserver: cannot start daemon", e);
    }
    return 0;
  }
}
