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

import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.server.GerritServerModule;
import com.google.gerrit.server.ssh.GerritSshDaemon;
import com.google.gerrit.server.ssh.SshDaemonModule;
import com.google.inject.Guice;
import com.google.inject.Injector;


/** Run only the SSH daemon portions of Gerrit. */
public class Daemon extends AbstractProgram {
  @Override
  public int run() throws Exception {
    final Injector injector =
        Guice.createInjector(new GerritServerModule(), new SshDaemonModule());

    // This is a hack to force the GerritConfig to install itself into
    // Common.setGerritConfig. If we don't do this here in the daemon
    // it won't inject in time for things that demand it. This must die.
    //
    Common.setGerritConfig(injector.getInstance(GerritConfig.class));

    injector.getInstance(GerritSshDaemon.class).start();
    return never();
  }
}
