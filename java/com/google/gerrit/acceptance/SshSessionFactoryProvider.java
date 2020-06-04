// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.session.SessionContext;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;

class SshSessionFactoryProvider {
  static SshdSessionFactory create(KeyPair keyPair, File userhome, File sshDir) {
    return new SshdSessionFactoryBuilder()
        .setConfigStoreFactory((h, f, u) -> null)
        .setDefaultKeysProvider(f -> new KeyAuthenticator(keyPair))
        // TODO(davido): Prefer config store with: "StrictHostKeyChecking: no" mode
        .setServerKeyDatabase(
            (h, s) ->
                new ServerKeyDatabase() {

                  @Override
                  public List<PublicKey> lookup(
                      String connectAddress,
                      InetSocketAddress remoteAddress,
                      Configuration config) {
                    return Collections.emptyList();
                  }

                  @Override
                  public boolean accept(
                      String connectAddress,
                      InetSocketAddress remoteAddress,
                      PublicKey serverKey,
                      Configuration config,
                      CredentialsProvider provider) {
                    return true;
                  }
                })
        .setPreferredAuthentications("publickey")
        .setHomeDirectory(userhome)
        .setSshDirectory(sshDir)
        .build(new JGitKeyCache());
  }

  private static class KeyAuthenticator implements KeyIdentityProvider, Iterable<KeyPair> {
    KeyPair keyPair;

    KeyAuthenticator(KeyPair keyPair) {
      this.keyPair = keyPair;
    }

    @Override
    public Iterator<KeyPair> iterator() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<KeyPair> loadKeys(SessionContext session)
        throws IOException, GeneralSecurityException {
      return Collections.singletonList(keyPair);
    }
  }
}
