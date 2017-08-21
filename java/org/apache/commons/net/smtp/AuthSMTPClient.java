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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.util.ssl.BlindSSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.apache.commons.codec.binary.Base64;

public class AuthSMTPClient extends SMTPClient {
  private String authTypes;

  public AuthSMTPClient(String charset) {
    super(charset);
  }

  public void enableSSL(boolean verify) {
    _socketFactory_ = sslFactory(verify);
  }

  public boolean startTLS(String hostname, int port, boolean verify)
      throws SocketException, IOException {
    if (sendCommand("STARTTLS") != 220) {
      return false;
    }

    _socket_ = sslFactory(verify).createSocket(_socket_, hostname, port, true);

    if (verify) {
      SSLParameters sslParams = new SSLParameters();
      sslParams.setEndpointIdentificationAlgorithm("HTTPS");
      ((SSLSocket) _socket_).setSSLParameters(sslParams);
    }

    // XXX: Can't call _connectAction_() because SMTP server doesn't
    // give banner information again after STARTTLS, thus SMTP._connectAction_()
    // will wait on __getReply() forever, see source code of commons-net-2.2.
    //
    // The lines below are copied from SocketClient._connectAction_() and
    // SMTP._connectAction_() in commons-net-2.2.
    _socket_.setSoTimeout(_timeout_);
    _input_ = _socket_.getInputStream();
    _output_ = _socket_.getOutputStream();
    _reader = new BufferedReader(new InputStreamReader(_input_, UTF_8));
    _writer = new BufferedWriter(new OutputStreamWriter(_output_, UTF_8));
    return true;
  }

  private static SSLSocketFactory sslFactory(boolean verify) {
    if (verify) {
      return (SSLSocketFactory) SSLSocketFactory.getDefault();
    }
    return (SSLSocketFactory) BlindSSLSocketFactory.getDefault();
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
      if (line != null && (line.startsWith("250 AUTH ") || line.startsWith("250-AUTH "))) {
        authTypes = line;
        break;
      }
    }

    return ok;
  }

  public boolean auth(String smtpUser, String smtpPass) throws IOException {
    List<String> types = Arrays.asList(authTypes.split(" "));
    if ("".equals(authTypes)) {
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
    if (types.contains("LOGIN")) {
      return authLogin(smtpUser, smtpPass);
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

    final String enc = getReplyStrings()[0].split(" ", 2)[1];
    final byte[] nonce = Base64.decodeBase64(enc.getBytes(UTF_8));
    final String sec;
    try {
      Mac mac = Mac.getInstance(macName);
      mac.init(new SecretKeySpec(smtpPass.getBytes(UTF_8), macName));
      sec = toHex(mac.doFinal(nonce));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IOException("Cannot use CRAM-" + alg, e);
    }

    String token = smtpUser + ' ' + sec;
    String cmd = encodeBase64(token.getBytes(UTF_8));
    return SMTPReply.isPositiveCompletion(sendCommand(cmd));
  }

  private boolean authLogin(String smtpUser, String smtpPass)
      throws UnsupportedEncodingException, IOException {
    if (sendCommand("AUTH", "LOGIN") != 334) {
      return false;
    }

    String cmd = encodeBase64(smtpUser.getBytes(UTF_8));
    if (sendCommand(cmd) != 334) {
      return false;
    }

    cmd = encodeBase64(smtpPass.getBytes(UTF_8));
    return SMTPReply.isPositiveCompletion(sendCommand(cmd));
  }

  private static final char[] hexchar = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
  };

  private String toHex(byte[] b) {
    final StringBuilder sec = new StringBuilder();
    for (byte c : b) {
      final int u = (c >> 4) & 0xf;
      final int l = c & 0xf;
      sec.append(hexchar[u]);
      sec.append(hexchar[l]);
    }
    return sec.toString();
  }

  private boolean authPlain(String smtpUser, String smtpPass)
      throws UnsupportedEncodingException, IOException {
    String token = '\0' + smtpUser + '\0' + smtpPass;
    String cmd = "PLAIN " + encodeBase64(token.getBytes(UTF_8));
    return SMTPReply.isPositiveCompletion(sendCommand("AUTH", cmd));
  }

  private static String encodeBase64(byte[] data) {
    return new String(Base64.encodeBase64(data), UTF_8);
  }
}
