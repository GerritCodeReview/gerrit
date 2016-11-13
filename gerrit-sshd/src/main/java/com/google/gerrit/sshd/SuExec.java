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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Atomics;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * Executes any other command as a different user identity.
 *
 * <p>The calling user must be authenticated as a {@link PeerDaemonUser}, which usually requires
 * public key authentication using this daemon's private host key, or a key on this daemon's peer
 * host key ring.
 */
public final class SuExec extends BaseCommand {
  private final SshScope sshScope;
  private final DispatchCommandProvider dispatcher;

  private boolean enableRunAs;
  private CurrentUser caller;
  private SshSession session;
  private IdentifiedUser.GenericFactory userFactory;
  private SshScope.Context callingContext;

  @Option(name = "--as", required = true)
  private Account.Id accountId;

  @Option(name = "--from")
  private SocketAddress peerAddress;

  @Argument(index = 0, multiValued = true, metaVar = "COMMAND")
  private List<String> args = new ArrayList<>();

  private final AtomicReference<Command> atomicCmd;

  @Inject
  SuExec(
      final SshScope sshScope,
      @CommandName(Commands.ROOT) final DispatchCommandProvider dispatcher,
      final CurrentUser caller,
      final SshSession session,
      final IdentifiedUser.GenericFactory userFactory,
      final SshScope.Context callingContext,
      AuthConfig config) {
    this.sshScope = sshScope;
    this.dispatcher = dispatcher;
    this.caller = caller;
    this.session = session;
    this.userFactory = userFactory;
    this.callingContext = callingContext;
    this.enableRunAs = config.isRunAsEnabled();
    atomicCmd = Atomics.newReference();
  }

  @Override
  public void start(Environment env) throws IOException {
    try {
      checkCanRunAs();
      parseCommandLine();

      final Context ctx = callingContext.subContext(newSession(), join(args));
      final Context old = sshScope.set(ctx);
      try {
        final BaseCommand cmd = dispatcher.get();
        cmd.setArguments(args.toArray(new String[args.size()]));
        provideStateTo(cmd);
        atomicCmd.set(cmd);
        cmd.start(env);
      } finally {
        sshScope.set(old);
      }
    } catch (UnloggedFailure e) {
      String msg = e.getMessage();
      if (!msg.endsWith("\n")) {
        msg += "\n";
      }
      err.write(msg.getBytes(UTF_8));
      err.flush();
      onExit(1);
    }
  }

  private void checkCanRunAs() throws UnloggedFailure {
    if (caller instanceof PeerDaemonUser) {
      // OK.
    } else if (!enableRunAs) {
      throw die("suexec disabled by auth.enableRunAs = false");
    } else if (!caller.getCapabilities().canRunAs()) {
      throw die("suexec not permitted");
    }
  }

  private SshSession newSession() {
    final SocketAddress peer;
    if (peerAddress == null) {
      peer = session.getRemoteAddress();
    } else {
      peer = peerAddress;
    }
    if (caller instanceof PeerDaemonUser) {
      caller = null;
    }
    return new SshSession(session, peer, userFactory.runAs(peer, accountId, caller));
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
    Command cmd = atomicCmd.getAndSet(null);
    if (cmd != null) {
      try {
        cmd.destroy();
      } catch (Exception e) {
        Throwables.throwIfUnchecked(e);
        throw new RuntimeException(e);
      }
    }
  }
}
