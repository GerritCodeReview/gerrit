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

package com.google.gerrit.sshd;

import static com.google.gerrit.extensions.registration.PrivateInternals_DynamicTypes.registerInParentInjectors;
import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.AsyncReceiveCommits;
import com.google.gerrit.server.git.QueueProvider;
import com.google.gerrit.server.plugins.ModuleGenerator;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.sshd.commands.QueryShell;
import com.google.inject.Inject;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.servlet.RequestScoped;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.eclipse.jgit.lib.Config;

/** Configures standard dependencies for {@link SshDaemon}. */
public class SshModule extends LifecycleModule {
  private final Map<String, String> aliases;

  @Inject
  SshModule(@GerritServerConfig Config cfg) {
    aliases = new HashMap<>();
    for (String name : cfg.getNames("ssh-alias", true)) {
      aliases.put(name, cfg.getString("ssh-alias", null, name));
    }
  }

  @Override
  protected void configure() {
    bindScope(RequestScoped.class, SshScope.REQUEST);
    bind(RequestScopePropagator.class).to(SshScope.Propagator.class);
    bind(SshScope.class).in(SINGLETON);

    configureRequestScope();
    install(new AsyncReceiveCommits.Module());
    configureAliases();

    bind(SshLog.class);
    bind(SshInfo.class).to(SshDaemon.class).in(SINGLETON);
    factory(DispatchCommand.Factory.class);
    factory(QueryShell.Factory.class);
    factory(PeerDaemonUser.Factory.class);

    bind(DispatchCommandProvider.class)
        .annotatedWith(Commands.CMD_ROOT)
        .toInstance(new DispatchCommandProvider(Commands.CMD_ROOT));
    bind(CommandFactoryProvider.class);
    bind(CommandFactory.class).toProvider(CommandFactoryProvider.class);
    bind(ScheduledThreadPoolExecutor.class)
        .annotatedWith(StreamCommandExecutor.class)
        .toProvider(StreamCommandExecutorProvider.class)
        .in(SINGLETON);
    bind(QueueProvider.class).to(CommandExecutorQueueProvider.class).in(SINGLETON);

    bind(GSSAuthenticator.class).to(GerritGSSAuthenticator.class);
    bind(PublickeyAuthenticator.class).to(CachingPublicKeyAuthenticator.class);

    bind(ModuleGenerator.class).to(SshAutoRegisterModuleGenerator.class);
    bind(SshPluginStarterCallback.class);
    bind(StartPluginListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(SshPluginStarterCallback.class);

    bind(ReloadPluginListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(SshPluginStarterCallback.class);

    DynamicMap.mapOf(binder(), DynamicOptions.DynamicBean.class);

    listener().toInstance(registerInParentInjectors());
    listener().to(SshLog.class);
    listener().to(SshDaemon.class);
    listener().to(CommandFactoryProvider.class);
  }

  private void configureAliases() {
    CommandName gerrit = Commands.named("gerrit");
    for (Map.Entry<String, String> e : aliases.entrySet()) {
      String name = e.getKey();
      String[] dest = e.getValue().split("[ \\t]+");
      CommandName cmd = Commands.named(dest[0]);
      for (int i = 1; i < dest.length; i++) {
        cmd = Commands.named(cmd, dest[i]);
      }
      bind(Commands.key(gerrit, name)).toProvider(new AliasCommandProvider(cmd));
    }
  }

  private void configureRequestScope() {
    bind(SshScope.Context.class).toProvider(SshScope.ContextProvider.class);

    bind(SshSession.class).toProvider(SshScope.SshSessionProvider.class).in(SshScope.REQUEST);
    bind(SocketAddress.class)
        .annotatedWith(RemotePeer.class)
        .toProvider(SshRemotePeerProvider.class)
        .in(SshScope.REQUEST);

    bind(ScheduledThreadPoolExecutor.class)
        .annotatedWith(CommandExecutor.class)
        .toProvider(CommandExecutorProvider.class)
        .in(SshScope.REQUEST);

    install(new GerritRequestModule());
  }
}
