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

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.ssh.commands.DefaultCommandModule;
import com.google.inject.Provider;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.SessionScoped;

import org.apache.sshd.common.session.AbstractSession;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import java.net.SocketAddress;

/** Configures standard dependencies for {@link SshDaemon}. */
public class SshDaemonModule extends FactoryModule {
  private static final String NAME = "Gerrit Code Review";

  @Override
  protected void configure() {
    bindScope(SessionScoped.class, SshScopes.SESSION);
    bindScope(RequestScoped.class, SshScopes.REQUEST);

    configureSessionScope();
    configureRequestScope();

    bind(SshInfo.class).to(SshDaemon.class).in(SINGLETON);
    factory(DispatchCommand.Factory.class);

    bind(DispatchCommandProvider.class).annotatedWith(Commands.CMD_ROOT)
        .toInstance(new DispatchCommandProvider(NAME, Commands.CMD_ROOT));
    bind(CommandFactoryProvider.class);
    bind(CommandFactory.class).toProvider(CommandFactoryProvider.class);

    bind(PublickeyAuthenticator.class).to(DatabasePubKeyAuth.class);

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
}
