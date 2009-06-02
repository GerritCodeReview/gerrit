// Copyright (C) 2009 The Android Open Source Project
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

package org.apache.commons.net.smtp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.util.Base64;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class AuthSMTPClient extends SMTPClient {
  private static final Logger log =
      LoggerFactory.getLogger(AuthSMTPClient.class);

  private String authTypes;
  private Set<String> allowedRcptTo;

  public AuthSMTPClient(final String charset) {
    super(charset);
  }

  public void setAllowRcpt(final String[] allowed) {
    if (allowed != null && allowed.length > 0) {
      if (allowedRcptTo == null) {
        allowedRcptTo = new HashSet<String>();
      }
      for (final String addr : allowed) {
        allowedRcptTo.add(addr);
      }
    }
  }

  @Override
  public int rcpt(final String forwardPath) throws IOException {
    if (allowRcpt(forwardPath)) {
      return super.rcpt(forwardPath);
    } else {
      log.warn("Not emailing " + forwardPath + " (prohibited by allowrcpt)");
      return SMTPReply.ACTION_OK;
    }
  }

  private boolean allowRcpt(String addr) {
    if (allowedRcptTo == null) {
      return true;
    }
    if (addr.startsWith("<") && addr.endsWith(">")) {
      addr = addr.substring(1, addr.length() - 1);
    }
    if (allowedRcptTo.contains(addr)) {
      return true;
    }
    final int at = addr.indexOf('@');
    if (at > 0) {
      return allowedRcptTo.contains(addr.substring(at))
          || allowedRcptTo.contains(addr.substring(at + 1));
    }
    return false;
  }

  @Override
  public String[] getReplyStrings() {
    return _replyLines.toArray(new String[_replyLines.size()]);
  }

  @Override
  public boolean login() throws IOException {
    final String name = getLocalAddress().getHostName();
    if (name == null) {
      return false;
    }

    boolean ok = SMTPReply.isPositiveCompletion(sendCommand("EHLO", name));
    authTypes = "";
    for (String line : getReplyStrings()) {
      if (line != null && line.startsWith("250 AUTH ")) {
        authTypes = line;
        break;
      }
    }

    return ok;
  }

  public boolean auth(String smtpUser, String smtpPass) throws IOException {
    List<String> types = Arrays.asList(authTypes.split(" "));
    if (types.isEmpty()) {
      // Server didn't advertise authentication support.
      //
      return true;
    }

    if (smtpPass == null) {
      smtpPass = "";
    }
    if (types.contains("CRAM-SHA1")) {
      return authCram(smtpUser, smtpPass, "SHA1");
    }
    if (types.contains("CRAM-MD5")) {
      return authCram(smtpUser, smtpPass, "MD5");
    }
    if (types.contains("PLAIN")) {
      return authPlain(smtpUser, smtpPass);
    }

    throw new IOException("Unsupported AUTH: " + authTypes);
  }

  private boolean authCram(String smtpUser, String smtpPass, String alg)
      throws UnsupportedEncodingException, IOException {
    final String macName = "Hmac" + alg;
    if (sendCommand("AUTH", "CRAM-" + alg) != 334) {
      return false;
    }

    final byte[] nonce = Base64.decode(getReplyStrings()[0].split(" ", 2)[1]);
    final String sec;
    try {
      Mac mac = Mac.getInstance(macName);
      mac.init(new SecretKeySpec(smtpPass.getBytes("UTF-8"), macName));
      sec = toHex(mac.doFinal(nonce));
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("Cannot use CRAM-" + alg, e);
    } catch (InvalidKeyException e) {
      throw new IOException("Cannot use CRAM-" + alg, e);
    }

    String token = smtpUser + ' ' + sec;
    String cmd = Base64.encodeBytes(token.getBytes("UTF-8"));
    return SMTPReply.isPositiveCompletion(sendCommand(cmd));
  }

  private static final char[] hexchar =
      {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
          'e', 'f'};

  private String toHex(final byte[] b) {
    final StringBuilder sec = new StringBuilder();
    for (int i = 0; i < b.length; i++) {
      final int u = (b[i] >> 4) & 0xf;
      final int l = b[i] & 0xf;
      sec.append(hexchar[u]);
      sec.append(hexchar[l]);
    }
    return sec.toString();
  }

  private boolean authPlain(String smtpUser, String smtpPass)
      throws UnsupportedEncodingException, IOException {
    String token = '\0' + smtpUser + '\0' + smtpPass;
    String cmd = "PLAIN " + Base64.encodeBytes(token.getBytes("UTF-8"));
    return SMTPReply.isPositiveCompletion(sendCommand("AUTH", cmd));
  }
}
