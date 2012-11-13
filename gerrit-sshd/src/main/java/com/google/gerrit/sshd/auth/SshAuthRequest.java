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

import com.google.gerrit.server.auth.AuthRequest;

import java.net.SocketAddress;
import java.security.PublicKey;

import javax.annotation.Nullable;

/**
 * Authentication request for a remote user connecting through an SSH
 * connection.
 */
public class SshAuthRequest extends AuthRequest {
  private final SocketAddress clientAddress;
  private final String username;
  private PublicKey pubKey;
  private String password;

  /**
   * Create a new authentication request for a remote user.
   *
   * @param username remote SSH username
   * @param clientAddress remote SSH client address
   */
  public SshAuthRequest(String username, SocketAddress clientAddress) {
    this.username = username;
    this.clientAddress = clientAddress;
  }

  public void setPubKey(PublicKey pubKey) {
    this.pubKey = pubKey;
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
  public String getUsername() {
    return username;
  }

  /**
   * Return the client SSH password client credentials.
   *
   * @return user's password or null if not provided during SSH negotiation.
   */
  @Override
  @Nullable
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
