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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.sshd.args4j.AccountGroupIdHandler;
import com.google.gerrit.sshd.args4j.AccountIdHandler;
import com.google.gerrit.sshd.args4j.PatchSetIdHandler;
import com.google.gerrit.sshd.args4j.ProjectControlHandler;
import com.google.gerrit.sshd.commands.DefaultCommandModule;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gerrit.util.cli.OptionHandlerFactory;
import com.google.gerrit.util.cli.OptionHandlerUtil;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryProvider;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.SessionScoped;

import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.session.AbstractSession;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.kohsuke.args4j.spi.OptionHandler;

import java.net.SocketAddress;

/** Configures standard dependencies for {@link SshDaemon}. */
public class SshModule extends FactoryModule {
  private static final String NAME = "Gerrit Code Review";

  @Override
  protected void configure() {
    bindScope(SessionScoped.class, SshScopes.SESSION);
    bindScope(RequestScoped.class, SshScopes.REQUEST);

    configureSessionScope();
    configureRequestScope();
    configureCmdLineParser();

    install(SshKeyCacheImpl.module());
    bind(SshInfo.class).to(SshDaemon.class).in(SINGLETON);
    factory(DispatchCommand.Factory.class);

    bind(DispatchCommandProvider.class).annotatedWith(Commands.CMD_ROOT)
        .toInstance(new DispatchCommandProvider(NAME, Commands.CMD_ROOT));
    bind(CommandFactoryProvider.class);
    bind(CommandFactory.class).toProvider(CommandFactoryProvider.class);

    bind(PublickeyAuthenticator.class).to(DatabasePubKeyAuth.class);
    bind(KeyPairProvider.class).toProvider(HostKeyProvider.class).in(SINGLETON);

    install(new DefaultCommandModule());
  }

  private void configureSessionScope() {
    bind(ServerSession.class).toProvider(new Provider<ServerSession>() {
      @Override
      public ServerSession get() {
        return SshScopes.getContext().session;
      }
    }).in(SshScopes.SESSION);
    bind(AbstractSession.class).to(ServerSession.class).in(SshScopes.SESSION);

    bind(SocketAddress.class).annotatedWith(RemotePeer.class).toProvider(
        new Provider<SocketAddress>() {
          @Override
          public SocketAddress get() {
            return SshScopes.getContext().session
                .getAttribute(SshUtil.REMOTE_PEER);
          }
        }).in(SshScopes.SESSION);
  }

  private void configureRequestScope() {
    install(new GerritRequestModule());
    bind(IdentifiedUser.class).toProvider(SshCurrentUserProvider.class).in(
        SshScopes.REQUEST);
    bind(CurrentUser.class).to(IdentifiedUser.class);
  }

  private void configureCmdLineParser() {
    factory(CmdLineParser.Factory.class);

    registerOptionHandler(Account.Id.class, AccountIdHandler.class);
    registerOptionHandler(AccountGroup.Id.class, AccountGroupIdHandler.class);
    registerOptionHandler(PatchSet.Id.class, PatchSetIdHandler.class);
    registerOptionHandler(ProjectControl.class, ProjectControlHandler.class);
  }

  private <T> void registerOptionHandler(Class<T> type,
      Class<? extends OptionHandler<T>> impl) {
    final Key<OptionHandlerFactory<T>> key = OptionHandlerUtil.keyFor(type);

    final TypeLiteral<OptionHandlerFactory<T>> factoryType =
        new TypeLiteral<OptionHandlerFactory<T>>() {};

    final TypeLiteral<? extends OptionHandler<T>> implType =
        TypeLiteral.get(impl);

    bind(key).toProvider(FactoryProvider.newFactory(factoryType, implType));
  }
}
