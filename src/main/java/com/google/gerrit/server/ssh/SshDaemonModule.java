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

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.ssh.SshScopes.Context;
import com.google.gerrit.server.ssh.commands.DefaultCommandModule;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.SessionScoped;

import org.apache.sshd.common.session.AbstractSession;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.CommandFactory.Command;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;

/** Configures standard dependencies for {@link GerritSshDaemon}. */
public class SshDaemonModule extends AbstractModule {
  static final Logger log = LoggerFactory.getLogger(SshDaemonModule.class);

  @Override
  protected void configure() {
    bindScope(SessionScoped.class, SshScopes.SESSION);
    bindScope(RequestScoped.class, SshScopes.REQUEST);

    configureSessionScope();
    configureRequestScope();

    bind(Sshd.class).to(GerritSshDaemon.class).in(SINGLETON);
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

    bind(IdentifiedUser.class).toProvider(new Provider<IdentifiedUser>() {
      @Override
      public IdentifiedUser get() {
        final Context ctx = SshScopes.getContext();
        final Account.Id id = ctx.session.getAttribute(SshUtil.CURRENT_ACCOUNT);
        if (id == null) {
          throw new ProvisionException("User not yet authenticated");
        }
        return new IdentifiedUser(id);
      }
    }).in(SshScopes.SESSION);
    bind(CurrentUser.class).to(IdentifiedUser.class);
  }

  private void configureRequestScope() {
    bind(Command.class).toProvider(new Provider<Command>() {
      @Override
      public Command get() {
        return SshScopes.getContext().command;
      }
    }).in(SshScopes.REQUEST);
  }
}
