// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes any other command as a different user identity.
 * <p>
 * The calling user must be authenticated as a {@link PeerDaemonUser}, which
 * usually requires public key authentication using this daemon's private host
 * key, or a key on this daemon's peer host key ring.
 */
public final class SuExec extends BaseCommand {
  private final DispatchCommandProvider dispatcher;

  private Provider<CurrentUser> caller;
  private Provider<SshSession> session;
  private IdentifiedUser.GenericFactory userFactory;
  private SshScope.Context callingContext;

  @Option(name = "--as", required = true)
  private Account.Id accountId;

  @Option(name = "--from")
  private SocketAddress peerAddress;

  @Argument(index = 0, multiValued = true, metaVar = "COMMAND")
  private List<String> args = new ArrayList<String>();

  private Command cmd;

  @Inject
  SuExec(@CommandName(Commands.ROOT) final DispatchCommandProvider dispatcher,
      final Provider<CurrentUser> caller, final Provider<SshSession> session,
      final IdentifiedUser.GenericFactory userFactory,
      final SshScope.Context callingContext) {
    this.dispatcher = dispatcher;
    this.caller = caller;
    this.session = session;
    this.userFactory = userFactory;
    this.callingContext = callingContext;
  }

  @Override
  public void start(Environment env) throws IOException {
    try {
      if (caller.get() instanceof PeerDaemonUser) {
        parseCommandLine();

        final Context ctx = callingContext.subContext(newSession(), join(args));
        final Context old = SshScope.set(ctx);
        try {
          final BaseCommand cmd = dispatcher.get();
          cmd.setArguments(args.toArray(new String[args.size()]));
          provideStateTo(cmd);

          synchronized (this) {
            this.cmd = cmd;
          }
          cmd.start(env);
        } finally {
          SshScope.set(old);
        }

      } else {
        throw new UnloggedFailure(1, "fatal: Not a peer daemon");
      }
    } catch (UnloggedFailure e) {
      String msg = e.getMessage();
      if (!msg.endsWith("\n")) {
        msg += "\n";
      }
      err.write(msg.getBytes("UTF-8"));
      err.flush();
      onExit(1);
    }
  }

  private SshSession newSession() {
    final SocketAddress peer;
    if (peerAddress == null) {
      peer = session.get().getRemoteAddress();
    } else {
      peer = peerAddress;
    }

    return new SshSession(session.get(), peer, userFactory.create(
        AccessPath.SSH_COMMAND, new Provider<SocketAddress>() {
          @Override
          public SocketAddress get() {
            return peer;
          }
        }, accountId));
  }

  private static String join(List<String> args) {
    StringBuilder r = new StringBuilder();
    for (String a : args) {
      if (r.length() > 0) {
        r.append(" ");
      }
      r.append(a);
    }
    return r.toString();
  }

  @Override
  public void destroy() {
    synchronized (this) {
      if (cmd != null) {
        cmd.destroy();
        cmd = null;
      }
    }
  }
}
