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

import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.apache.sshd.common.AttributeStore.AttributeKey;

/** Global data related to an active SSH connection. */
public class SshSession {
  /** ServerSession attribute key for this object instance. */
  public static final AttributeKey<SshSession> KEY = new AttributeKey<>();

  private final int sessionId;
  private final SocketAddress remoteAddress;
  private final String remoteAsString;

  private volatile CurrentUser identity;
  private volatile String username;
  private volatile String authError;
  private volatile String peerAgent;

  SshSession(int sessionId, SocketAddress peer) {
    this.sessionId = sessionId;
    this.remoteAddress = peer;
    this.remoteAsString = format(remoteAddress);
  }

  SshSession(SshSession parent, SocketAddress peer, CurrentUser user) {
    user.setAccessPath(AccessPath.SSH_COMMAND);
    this.sessionId = parent.sessionId;
    this.remoteAddress = peer;
    if (parent.remoteAddress == peer) {
      this.remoteAsString = parent.remoteAsString;
    } else {
      this.remoteAsString = format(peer) + "/" + parent.remoteAsString;
    }
    this.identity = user;
  }

  /** Unique session number, assigned during connect. */
  public int getSessionId() {
    return sessionId;
  }

  /** Identity of the authenticated user account on the socket. */
  public CurrentUser getUser() {
    return identity;
  }

  public SocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  public String getRemoteAddressAsString() {
    return remoteAsString;
  }

  public String getPeerAgent() {
    return peerAgent;
  }

  public void setPeerAgent(String agent) {
    peerAgent = agent;
  }

  String getUsername() {
    return username;
  }

  String getAuthenticationError() {
    return authError;
  }

  void authenticationSuccess(String user, CurrentUser id) {
    username = user;
    identity = id;
    identity.setAccessPath(AccessPath.SSH_COMMAND);
    authError = null;
  }

  void authenticationError(String user, String error) {
    username = user;
    identity = null;
    authError = error;
  }

  void setAccessPath(AccessPath path) {
    identity.setAccessPath(path);
  }

  /** @return {@code true} if the authentication did not succeed. */
  boolean isAuthenticationError() {
    return authError != null;
  }

  private static String format(SocketAddress remote) {
    if (remote instanceof InetSocketAddress) {
      final InetSocketAddress sa = (InetSocketAddress) remote;

      final InetAddress in = sa.getAddress();
      if (in != null) {
        return in.getHostAddress();
      }

      final String hostName = sa.getHostName();
      if (hostName != null) {
        return hostName;
      }
    }
    return remote.toString();
  }
}
