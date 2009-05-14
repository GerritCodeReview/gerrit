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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountSshKey;

import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.session.AttributeKey;
import org.apache.sshd.common.util.Buffer;
import org.spearce.jgit.lib.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.SocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

/** Utilities to support SSH operations. */
public class SshUtil {
  /** Server session attribute holding the {@link Account.Id}. */
  static final AttributeKey<Account.Id> CURRENT_ACCOUNT =
      new AttributeKey<Account.Id>();

  /** Server session attribute holding the remote {@link SocketAddress}. */
  static final AttributeKey<SocketAddress> REMOTE_PEER =
      new AttributeKey<SocketAddress>();

  /** Server session attribute holding the current commands. */
  static final AttributeKey<List<AbstractCommand>> ACTIVE =
      new AttributeKey<List<AbstractCommand>>();

  /**
   * Parse a public key into its Java type.
   * 
   * @param key the account key to parse.
   * @return the valid public key object.
   * @throws InvalidKeySpecException the key supplied is not a valid SSH key.
   * @throws NoSuchAlgorithmException the JVM is missing the key algorithm.
   * @throws NoSuchProviderException the JVM is missing the provider.
   */
  public static PublicKey parse(final AccountSshKey key)
      throws NoSuchAlgorithmException, InvalidKeySpecException,
      NoSuchProviderException {
    try {
      final String s = key.getEncodedKey();
      if (s == null) {
        throw new InvalidKeySpecException("No key string");
      }
      final byte[] bin = Base64.decodeBase64(Constants.encodeASCII(s));
      return new Buffer(bin).getPublicKey();
    } catch (RuntimeException re) {
      throw new InvalidKeySpecException("Cannot parse key", re);
    }
  }

  /**
   * Convert an RFC 4716 style key to an OpenSSH style key.
   * 
   * @param keyStr the key string to convert.
   * @return <code>keyStr</code> if conversion failed; otherwise the converted
   *         key, in OpenSSH key format.
   */
  public static String toOpenSshPublicKey(final String keyStr) {
    try {
      final StringBuilder strBuf = new StringBuilder();
      final BufferedReader br = new BufferedReader(new StringReader(keyStr));
      String line = br.readLine(); // BEGIN SSH2 line...
      if (!line.equals("---- BEGIN SSH2 PUBLIC KEY ----")) {
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
          new Buffer(Base64.decodeBase64(Constants.encodeASCII(strBuf
              .toString()))).getPublicKey();
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
    } catch (IOException e) {
      return keyStr;
    } catch (RuntimeException re) {
      return keyStr;
    } catch (NoSuchAlgorithmException e) {
      return keyStr;
    } catch (InvalidKeySpecException e) {
      return keyStr;
    } catch (NoSuchProviderException e) {
      return keyStr;
    }
  }
}
