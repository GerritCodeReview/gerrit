// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.sshd.auth;

import com.google.common.base.Objects;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthRequest;
import com.google.gerrit.server.auth.InvalidCredentialsException;

import java.net.SocketAddress;
import java.security.PublicKey;

import javax.annotation.Nullable;

/**
 * Authentication request for a remote user connecting through an SSH
 * connection.
 */
public class SshAuthRequest extends AuthRequest {
  private final SocketAddress clientAddress;
  private final PublicKey pubKey;

  /**
   * Create a new authentication request for a remote user.
   *
   * @param username remote SSH username
   * @param clientAddress remote SSH client address
   */
  public SshAuthRequest(String username, SocketAddress clientAddress) {
    this(username, null, null, clientAddress);
  }

  /**
   * Create a new authentication request for a remote user.
   *
   * @param username remote SSH username
   * @param password remote user password
   * @param clientAddress remote SSH client address
   */
  public SshAuthRequest(String username, String password,
      SocketAddress clientAddress) {
    this(username, password, null, clientAddress);
  }

  /**
   * Create a new authentication request for a remote user.
   *
   * @param username remote SSH username
   * @param pubKey remote user public key
   * @param clientAddress remote SSH client address
   */
  public SshAuthRequest(String username, PublicKey pubKey,
      SocketAddress clientAddress) {
    this(username, null, pubKey, clientAddress);
  }

  /**
   * Create a new authentication request for a remote user.
   *
   * @param username remote SSH username
   * @param password remote user password
   * @param pubKey remote user puclick key
   * @param clientAddress remote SSH client address
   */
  public SshAuthRequest(String username, String password, PublicKey pubKey,
      SocketAddress clientAddress) {
    super(username, password);
    this.pubKey = pubKey;
    this.clientAddress = clientAddress;
  }

  /**
   * Returns the user's SSH PublicKey credentials.
   *
   * @return SSH PublicKey or null if was not provided during SSH negotiation.
   */
  @Nullable
  public PublicKey getPubKey() {
    return pubKey;
  }

  /**
   * Returns the client IP address where the connected user.
   *
   * @return client IP address of the remote user.
   */
  public SocketAddress getClientAddress() {
    return clientAddress;
  }

  @Override
  public void checkCredentials(Object credentials) throws AuthException {
    if (credentials instanceof String) {
      super.checkCredentials(credentials);
      return;
    } else if (credentials instanceof PublicKey
        && Objects.equal(getPubKey(), credentials)) {
      return;
    }
    throw new InvalidCredentialsException();
  }
}
