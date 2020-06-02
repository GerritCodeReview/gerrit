/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.google.gerrit.acceptance;

import com.google.common.base.Strings;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.util.GenericUtils;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.util.io.pem.PemObject;

public class KeyPairResourceWriter {
  private static final Pattern VERTICALSPACE = Pattern.compile("\\v");

  public static void writePublicKey(PublicKey key, String comment, OutputStream out)
      throws IOException {
    StringBuilder b = new StringBuilder(82);
    PublicKeyEntry.appendPublicKeyEntry(b, key);
    // Append first line of comment - if available
    String line = firstLine(comment);
    if (GenericUtils.isNotEmpty(line)) {
      b.append(' ').append(line);
    }
    out.write(b.toString().getBytes(StandardCharsets.UTF_8));
  }

  /* Unencrypted form of PKCS#8 file */
  public static void writePrivateKeyPKCS8(PrivateKey privateKey, Writer out) throws IOException {
    JcaPKCS8Generator gen1 = new JcaPKCS8Generator(privateKey, null);
    PemObject obj1 = gen1.generate();
    try (JcaPEMWriter pw = new JcaPEMWriter(out)) {
      pw.writeObject(obj1);
    }
  }

  private static String firstLine(String text) {
    if (!Strings.isNullOrEmpty(text)) {
      Matcher m = VERTICALSPACE.matcher(text);
      if (m.find()) {
        return text.substring(0, m.start()).trim();
      }
    }

    return text;
  }

  private KeyPairResourceWriter() {}
}
