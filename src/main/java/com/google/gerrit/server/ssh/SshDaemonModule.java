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

package com.google.gerrit.server.ssh;

import static com.google.inject.Scopes.SINGLETON;

import com.google.inject.AbstractModule;
import com.google.inject.binder.LinkedBindingBuilder;

import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.PublickeyAuthenticator;

/** Configures standard dependencies for {@link GerritSshDaemon}. */
public class SshDaemonModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(Sshd.class).to(GerritSshDaemon.class).in(SINGLETON);
    bind(CommandFactory.class).toProvider(CommandFactoryProvider.class);
    bind(PublickeyAuthenticator.class).to(DatabasePubKeyAuth.class);

    command("gerrit-upload-pack").to(Upload.class);
    command("gerrit-receive-pack").to(Receive.class);
    command("gerrit-flush-caches").to(AdminFlushCaches.class);
    command("gerrit-ls-projects").to(ListProjects.class);
    command("gerrit-show-caches").to(AdminShowCaches.class);
    command("gerrit-show-connections").to(AdminShowConnections.class);
    command("gerrit-show-queue").to(AdminShowQueue.class);
    command("gerrit-replicate").to(AdminReplicate.class);
    command("scp").to(ScpCommand.class);

    alias("git-upload-pack", "gerrit-upload-pack");
    alias("git-receive-pack", "gerrit-receive-pack");
  }

  private LinkedBindingBuilder<AbstractCommand> command(final String name) {
    return bind(Commands.key(name));
  }

  private void alias(final String from, final String to) {
    bind(Commands.key(from)).toProvider(getProvider(Commands.key(to)));
  }
}
