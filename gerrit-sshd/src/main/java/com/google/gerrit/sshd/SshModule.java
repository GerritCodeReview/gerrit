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

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.ChangeUserName;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.git.QueueProvider;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.plugins.ModuleGenerator;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.sshd.args4j.AccountGroupIdHandler;
import com.google.gerrit.sshd.args4j.AccountGroupUUIDHandler;
import com.google.gerrit.sshd.args4j.AccountIdHandler;
import com.google.gerrit.sshd.args4j.ObjectIdHandler;
import com.google.gerrit.sshd.args4j.PatchSetIdHandler;
import com.google.gerrit.sshd.args4j.ProjectControlHandler;
import com.google.gerrit.sshd.args4j.SocketAddressHandler;
import com.google.gerrit.sshd.commands.DefaultCommandModule;
import com.google.gerrit.sshd.commands.QueryShell;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gerrit.util.cli.OptionHandlerUtil;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.servlet.RequestScoped;

import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.args4j.spi.OptionHandler;

import java.net.SocketAddress;

/** Configures standard dependencies for {@link SshDaemon}. */
public class SshModule extends FactoryModule {
  @Override
  protected void configure() {
    bindScope(RequestScoped.class, SshScope.REQUEST);
    bind(RequestScopePropagator.class).to(SshScope.Propagator.class);

    configureRequestScope();
    configureCmdLineParser();

    install(SshKeyCacheImpl.module());
    bind(SshLog.class);
    bind(SshInfo.class).to(SshDaemon.class).in(SINGLETON);
    factory(DispatchCommand.Factory.class);
    factory(QueryShell.Factory.class);
    factory(PeerDaemonUser.Factory.class);

    bind(DispatchCommandProvider.class).annotatedWith(Commands.CMD_ROOT)
        .toInstance(new DispatchCommandProvider("", Commands.CMD_ROOT));
    bind(CommandFactoryProvider.class);
    bind(CommandFactory.class).toProvider(CommandFactoryProvider.class);
    bind(WorkQueue.Executor.class).annotatedWith(StreamCommandExecutor.class)
        .toProvider(StreamCommandExecutorProvider.class).in(SINGLETON);
    bind(QueueProvider.class).to(CommandExecutorQueueProvider.class).in(SINGLETON);
    bind(AccountManager.class);
    factory(ChangeUserName.Factory.class);

    bind(PublickeyAuthenticator.class).to(DatabasePubKeyAuth.class);
    bind(KeyPairProvider.class).toProvider(HostKeyProvider.class).in(SINGLETON);

    install(new DefaultCommandModule());

    install(new LifecycleModule() {
      @Override
      protected void configure() {
        bind(ModuleGenerator.class).to(SshAutoRegisterModuleGenerator.class);
        bind(SshPluginStarterCallback.class);
        bind(StartPluginListener.class)
          .annotatedWith(UniqueAnnotations.create())
          .to(SshPluginStarterCallback.class);

        bind(ReloadPluginListener.class)
          .annotatedWith(UniqueAnnotations.create())
          .to(SshPluginStarterCallback.class);

        listener().to(SshLog.class);
        listener().to(SshDaemon.class);
      }
    });
  }

  private void configureRequestScope() {
    bind(SshScope.Context.class).toProvider(SshScope.ContextProvider.class);

    bind(SshSession.class).toProvider(SshScope.SshSessionProvider.class).in(
        SshScope.REQUEST);
    bind(SocketAddress.class).annotatedWith(RemotePeer.class).toProvider(
        SshRemotePeerProvider.class).in(SshScope.REQUEST);

    bind(CurrentUser.class).toProvider(SshCurrentUserProvider.class).in(
        SshScope.REQUEST);
    bind(IdentifiedUser.class).toProvider(SshIdentifiedUserProvider.class).in(
        SshScope.REQUEST);

    bind(WorkQueue.Executor.class).annotatedWith(CommandExecutor.class)
        .toProvider(CommandExecutorProvider.class).in(SshScope.REQUEST);

    install(new GerritRequestModule());
  }

  private void configureCmdLineParser() {
    factory(CmdLineParser.Factory.class);

    registerOptionHandler(Account.Id.class, AccountIdHandler.class);
    registerOptionHandler(AccountGroup.Id.class, AccountGroupIdHandler.class);
    registerOptionHandler(AccountGroup.UUID.class, AccountGroupUUIDHandler.class);
    registerOptionHandler(ObjectId.class, ObjectIdHandler.class);
    registerOptionHandler(PatchSet.Id.class, PatchSetIdHandler.class);
    registerOptionHandler(ProjectControl.class, ProjectControlHandler.class);
    registerOptionHandler(SocketAddress.class, SocketAddressHandler.class);
  }

  private <T> void registerOptionHandler(Class<T> type,
      Class<? extends OptionHandler<T>> impl) {
    install(OptionHandlerUtil.moduleFor(type, impl));
  }
}
