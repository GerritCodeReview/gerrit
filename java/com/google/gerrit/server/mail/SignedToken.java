// Copyright 2008 Google Inc.
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

package com.google.gerrit.server.mail;

import com.google.common.io.BaseEncoding;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility function to compute and verify XSRF tokens.
 *
 * <p>{@link SignedTokenEmailTokenVerifier} uses this class to verify tokens appearing in the custom
 * <code>xsrfKey
 * </code> JSON request property. The tokens protect against cross-site request forgery by depending
 * upon the browser's security model. The classic browser security model prohibits a script from
 * site A from reading any data received from site B. By sending unforgeable tokens from the server
 * and asking the client to return them to us, the client script must have had read access to the
 * token at some point and is therefore also from our server.
 */
public class SignedToken {
  private static final int INT_SZ = 4;
  private static final String MAC_ALG = "HmacSHA1";

  /**
   * Generate a random key for use with the XSRF library.
   *
   * @return a new private key, base 64 encoded.
   */
  public static String generateRandomKey() {
    final byte[] r = new byte[26];
    new SecureRandom().nextBytes(r);
    return encodeBase64(r);
  }

  private final int maxAge;
  private final SecretKeySpec key;
  private final SecureRandom rng;
  private final int tokenLength;

  /**
   * Create a new utility, using the specific key.
   *
   * @param age the number of seconds a token may remain valid.
   * @param keyBase64 base 64 encoded representation of the key.
   * @throws XsrfException the JVM doesn't support the necessary algorithms.
   */
  public SignedToken(final int age, final String keyBase64) throws XsrfException {
    maxAge = age > 5 ? age / 5 : age;
    key = new SecretKeySpec(decodeBase64(keyBase64), MAC_ALG);
    rng = new SecureRandom();
    tokenLength = 2 * INT_SZ + newMac().getMacLength();
  }

  /**
   * Generate a new signed token.
   *
   * @param text the text string to sign. Typically this should be some user-specific string, to
   *     prevent replay attacks. The text must be safe to appear in whatever context the token
   *     itself will appear, as the text is included on the end of the token.
   * @return the signed token. The text passed in <code>text</code> will appear after the first ','
   *     in the returned token string.
   * @throws XsrfException the JVM doesn't support the necessary algorithms.
   */
  String newToken(final String text) throws XsrfException {
    final int q = rng.nextInt();
    final byte[] buf = new byte[tokenLength];
    encodeInt(buf, 0, q);
    encodeInt(buf, INT_SZ, now() ^ q);
    computeToken(buf, text);
    return encodeBase64(buf) + '$' + text;
  }

  /**
   * Validate a returned token.
   *
   * @param tokenString a token string previously created by this class.
   * @param text text that must have been used during {@link #newToken(String)} in order for the
   *     token to be valid. If null the text will be taken from the token string itself.
   * @return true if the token is valid; false if the token is null, the empty string, has expired,
   *     does not match the text supplied, or is a forged token.
   * @throws XsrfException the JVM doesn't support the necessary algorithms to generate a token.
   *     XSRF services are simply not available.
   */
  ValidToken checkToken(final String tokenString, final String text) throws XsrfException {
    if (tokenString == null || tokenString.length() == 0) {
      return null;
    }

    final int s = tokenString.indexOf('$');
    if (s <= 0) {
      return null;
    }

    final String recvText = tokenString.substring(s + 1);
    final byte[] in;
    try {
      in = decodeBase64(tokenString.substring(0, s));
    } catch (RuntimeException e) {
      return null;
    }
    if (in.length != tokenLength) {
      return null;
    }

    final int q = decodeInt(in, 0);
    final int c = decodeInt(in, INT_SZ) ^ q;
    final int n = now();
    if (maxAge > 0 && Math.abs(c - n) > maxAge) {
      return null;
    }

    final byte[] gen = new byte[tokenLength];
    System.arraycopy(in, 0, gen, 0, 2 * INT_SZ);
    computeToken(gen, text != null ? text : recvText);
    if (!Arrays.equals(gen, in)) {
      return null;
    }

    return new ValidToken(maxAge > 0 && c + (maxAge >> 1) <= n, recvText);
  }

  private void computeToken(final byte[] buf, final String text) throws XsrfException {
    final Mac m = newMac();
    m.update(buf, 0, 2 * INT_SZ);
    m.update(toBytes(text));
    try {
      m.doFinal(buf, 2 * INT_SZ);
    } catch (ShortBufferException e) {
      throw new XsrfException("Unexpected token overflow", e);
    }
  }

  private Mac newMac() throws XsrfException {
    try {
      final Mac m = Mac.getInstance(MAC_ALG);
      m.init(key);
      return m;
    } catch (NoSuchAlgorithmException e) {
      throw new XsrfException(MAC_ALG + " not supported", e);
    } catch (InvalidKeyException e) {
      throw new XsrfException("Invalid private key", e);
    }
  }

  private static int now() {
    return (int) (System.currentTimeMillis() / 5000L);
  }

  private static byte[] decodeBase64(final String s) {
    return BaseEncoding.base64Url().decode(s);
  }

  private static String encodeBase64(final byte[] buf) {
    return BaseEncoding.base64Url().encode(buf);
  }

  private static void encodeInt(final byte[] buf, final int o, final int v) {
    int _v = v;
    buf[o + 3] = (byte) _v;
    _v >>>= 8;

    buf[o + 2] = (byte) _v;
    _v >>>= 8;

    buf[o + 1] = (byte) _v;
    _v >>>= 8;

    buf[o] = (byte) _v;
  }

  private static int decodeInt(final byte[] buf, final int o) {
    int r = buf[o] << 8;

    r |= buf[o + 1] & 0xff;
    r <<= 8;

    r |= buf[o + 2] & 0xff;
    return (r << 8) | (buf[o + 3] & 0xff);
  }

  private static byte[] toBytes(final String s) {
    final byte[] r = new byte[s.length()];
    for (int k = r.length - 1; k >= 0; k--) {
      r[k] = (byte) s.charAt(k);
    }
    return r;
  }
}
