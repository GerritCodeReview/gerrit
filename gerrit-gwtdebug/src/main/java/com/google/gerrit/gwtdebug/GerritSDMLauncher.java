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

package com.google.gerrit.gwtdebug;

import com.google.gerrit.pgm.Daemon;
import com.google.gwt.dev.codeserver.CodeServer;
import com.google.gwt.dev.codeserver.Options;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

class GerritSDMLauncher {
  private static final Logger log = LoggerFactory.getLogger(GerritSDMLauncher.class);

  public static void main(String[] argv) throws Exception {
    GerritSDMLauncher launcher = new GerritSDMLauncher();
    launcher.mainImpl(argv);
  }

  private int mainImpl(String[] argv) {
    List<String> sdmLauncherOptions = new ArrayList<>();
    List<String> daemonLauncherOptions = new ArrayList<>();

    // Separator between Daemon and Codeserver parameters is "--"
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

    Options options = new Options();
    if (!options.parseArgs(sdmLauncherOptions.toArray(
            new String[sdmLauncherOptions.size()]))) {
      log.error("Fail to parse codeserver arguments");
      return 1;
    }

    CodeServer.main(options);

    try {
      if (startDaemon) {
        Daemon daemon = new Daemon();
        daemon.main(daemonLauncherOptions.toArray(
            new String[daemonLauncherOptions.size()]));
      }
    } catch (Exception e) {
      log.error("Codeserver: cannot start daemon", e);
      return 1;
    }
    return 0;
  }
}
