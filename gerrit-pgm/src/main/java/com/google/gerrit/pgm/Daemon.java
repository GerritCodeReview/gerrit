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

import com.google.gerrit.server.cache.CachePool;
import com.google.gerrit.server.config.GerritGlobalModule;
import com.google.gerrit.sshd.SshDaemon;
import com.google.gerrit.sshd.SshModule;
import com.google.gerrit.sshd.commands.MasterCommandModule;
import com.google.gerrit.sshd.commands.SlaveCommandModule;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;

/** Run only the SSH daemon portions of Gerrit. */
public class Daemon extends AbstractProgram {

  @Option(name = "--slave", usage = "support fetch only")
  boolean slave;

  @Override
  public int run() throws Exception {
    Injector sysInjector = GerritGlobalModule.createInjector();
    Injector sshInjector = createSshInjector(sysInjector);
    sysInjector.getInstance(CachePool.class).start();
    sshInjector.getInstance(SshDaemon.class).start();
    return never();
  }

  private Injector createSshInjector(final Injector sysInjector) {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(new SshModule());
    if (slave) {
      modules.add(new SlaveCommandModule());
    } else {
      modules.add(new MasterCommandModule());
    }
    return sysInjector.createChildInjector(modules);
  }
}
