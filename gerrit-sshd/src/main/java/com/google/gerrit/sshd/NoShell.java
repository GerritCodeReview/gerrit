// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.sshd.common.Factory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.SystemReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Dummy shell which prints a message and terminates.
 * <p>
 * This implementation is used to ensure clients who try to SSH directly to this
 * server without supplying a command will get a reasonable error message, but
 * cannot continue further.
 */
class NoShell implements Factory<Command> {
  private final Provider<SendMessage> shell;

  @Inject
  NoShell(Provider<SendMessage> shell) {
    this.shell = shell;
  }

  @Override
  public Command create() {
    return shell.get();
  }

  static class SendMessage implements Command, SessionAware {
    private final Provider<MessageFactory> messageFactory;
    private final SchemaFactory<ReviewDb> schemaFactory;
    private final SshScope sshScope;

    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback exit;
    private Context context;

    @Inject
    SendMessage(Provider<MessageFactory> messageFactory,
        SchemaFactory<ReviewDb> sf, SshScope sshScope) {
      this.messageFactory = messageFactory;
      this.schemaFactory = sf;
      this.sshScope = sshScope;
    }

    @Override
    public void setInputStream(final InputStream in) {
      this.in = in;
    }

    @Override
    public void setOutputStream(final OutputStream out) {
      this.out = out;
    }

    @Override
    public void setErrorStream(final OutputStream err) {
      this.err = err;
    }

    @Override
    public void setExitCallback(final ExitCallback callback) {
      this.exit = callback;
    }

    @Override
    public void setSession(final ServerSession session) {
      SshSession s = session.getAttribute(SshSession.KEY);
      this.context = sshScope.newContext(schemaFactory, s, "");
    }

    @Override
    public void start(final Environment env) throws IOException {
      Context old = sshScope.set(context);
      String message;
      try {
        message = messageFactory.get().getMessage();
      } finally {
        sshScope.set(old);
      }
      err.write(Constants.encode(message));
      err.flush();

      in.close();
      out.close();
      err.close();
      exit.onExit(127);
    }

    @Override
    public void destroy() {
    }
  }

  static class MessageFactory {
    private final IdentifiedUser user;
    private final SshInfo sshInfo;
    private final Provider<String> urlProvider;

    @Inject
    MessageFactory(IdentifiedUser user, SshInfo sshInfo,
        @CanonicalWebUrl Provider<String> urlProvider) {
      this.user = user;
      this.sshInfo = sshInfo;
      this.urlProvider = urlProvider;
    }

    String getMessage() {
      StringBuilder msg = new StringBuilder();

      msg.append("\r\n");
      msg.append("  ****    Welcome to Gerrit Code Review    ****\r\n");
      msg.append("\r\n");

      Account account = user.getAccount();
      String name = account.getFullName();
      if (name == null || name.isEmpty()) {
        name = user.getUserName();
      }
      msg.append("  Hi ");
      msg.append(name);
      msg.append(", you have successfully connected over SSH.");
      msg.append("\r\n");
      msg.append("\r\n");

      msg.append("  Unfortunately, interactive shells are disabled.\r\n");
      msg.append("  To clone a hosted Git repository, use:\r\n");
      msg.append("\r\n");

      if (!sshInfo.getHostKeys().isEmpty()) {
        String host = sshInfo.getHostKeys().get(0).getHost();
        if (host.startsWith("*:")) {
          host = getGerritHost() + host.substring(1);
        }

        msg.append("  git clone ssh://");
        msg.append(user.getUserName());
        msg.append("@");
        msg.append(host);
        msg.append("/");
        msg.append("REPOSITORY_NAME.git");
        msg.append("\r\n");
      }

      msg.append("\r\n");
      return msg.toString();
    }

    private String getGerritHost() {
      String url = urlProvider.get();
      if (url != null) {
        try {
          return new URL(url).getHost();
        } catch (MalformedURLException e) {
        }
      }
      return SystemReader.getInstance().getHostname();
    }
  }
}
