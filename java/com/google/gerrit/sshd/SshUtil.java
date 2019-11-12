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

import com.google.common.io.BaseEncoding;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.sshd.SshScope.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.server.session.ServerSession;

/** Utilities to support SSH operations. */
public class SshUtil {
  /**
   * Parse a public key into its Java type.
   *
   * @param key the account key to parse.
   * @return the valid public key object.
   * @throws InvalidKeySpecException the key supplied is not a valid SSH key.
   * @throws NoSuchAlgorithmException the JVM is missing the key algorithm.
   * @throws NoSuchProviderException the JVM is missing the provider.
   */
  public static PublicKey parse(AccountSshKey key)
      throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
    try {
      final String s = key.encodedKey();
      if (s == null) {
        throw new InvalidKeySpecException("No key string");
      }
      final byte[] bin = BaseEncoding.base64().decode(s);
      return new ByteArrayBuffer(bin).getRawPublicKey();
    } catch (RuntimeException | SshException e) {
      throw new InvalidKeySpecException("Cannot parse key", e);
    }
  }

  /**
   * Convert an RFC 4716 style key to an OpenSSH style key.
   *
   * @param keyStr the key string to convert.
   * @return {@code keyStr} if conversion failed; otherwise the converted key, in OpenSSH key
   *     format.
   */
  public static String toOpenSshPublicKey(String keyStr) {
    try {
      final StringBuilder strBuf = new StringBuilder();
      final BufferedReader br = new BufferedReader(new StringReader(keyStr));
      String line = br.readLine(); // BEGIN SSH2 line...
      if (line == null || !line.equals("---- BEGIN SSH2 PUBLIC KEY ----")) {
        return keyStr;
      }

      while ((line = br.readLine()) != null) {
        if (line.indexOf(':') == -1) {
          strBuf.append(line);
          break;
        }
      }

      while ((line = br.readLine()) != null) {
        if (line.startsWith("---- ")) {
          break;
        }
        strBuf.append(line);
      }

      final PublicKey key =
          new ByteArrayBuffer(BaseEncoding.base64().decode(strBuf.toString())).getRawPublicKey();
      if (key instanceof RSAPublicKey) {
        strBuf.insert(0, KeyPairProvider.SSH_RSA + " ");

      } else if (key instanceof DSAPublicKey) {
        strBuf.insert(0, KeyPairProvider.SSH_DSS + " ");

      } else {
        return keyStr;
      }

      strBuf.append(' ');
      strBuf.append("converted-key");
      return strBuf.toString();
    } catch (IOException | RuntimeException e) {
      return keyStr;
    }
  }

  public static boolean success(
      final String username,
      final ServerSession session,
      final SshScope sshScope,
      final SshLog sshLog,
      final SshSession sd,
      final CurrentUser user) {
    if (sd.getUser() == null) {
      sd.authenticationSuccess(username, user);

      // If this is the first time we've authenticated this
      // session, record a login event in the log and add
      // a close listener to record a logout event.
      //
      Context ctx = sshScope.newContext(sd, null);
      Context old = sshScope.set(ctx);
      try {
        sshLog.onLogin();
      } finally {
        sshScope.set(old);
      }

      session.addCloseFutureListener(
          future -> {
            final Context ctx1 = sshScope.newContext(sd, null);
            final Context old1 = sshScope.set(ctx1);
            try {
              sshLog.onLogout();
            } finally {
              sshScope.set(old1);
            }
          });
    }

    return true;
  }

  public static IdentifiedUser createUser(
      final SshSession sd,
      final IdentifiedUser.GenericFactory userFactory,
      final Account.Id account) {
    return userFactory.create(sd.getRemoteAddress(), account);
  }
}
