// Copyright (C) 2015 The Android Open Source Project
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

package com.googlesource.gerrit.convertkey;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSchException;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import org.apache.sshd.common.util.Buffer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

public class ConvertKey {
  public static void main(String[] args)
      throws GeneralSecurityException, JSchException, IOException {
    SimpleGeneratorHostKeyProvider p;

    if (args.length != 1) {
      System.err.println("Error: requires path to the SSH host key");
      return;
    } else {
      File file = new File(args[0]);
      if (!file.exists() || !file.isFile() || !file.canRead()) {
        System.err.println("Error: ssh key should exist and be readable");
        return;
      }
    }

    p = new SimpleGeneratorHostKeyProvider();
    // Gerrit's SSH "simple" keys are always RSA.
    p.setPath(args[0]);
    p.setAlgorithm("RSA");
    Iterable<KeyPair> keys = p.loadKeys(); // forces the key to generate.
    for (KeyPair k : keys) {
      System.out.println("Public Key (" + k.getPublic().getAlgorithm() + "):");
      // From Gerrit's SshDaemon class; use JSch to get the public
      // key/type
      final Buffer buf = new Buffer();
      buf.putRawPublicKey(k.getPublic());
      final byte[] keyBin = buf.getCompactData();
      HostKey pub = new HostKey("localhost", keyBin);
      System.out.println(pub.getType() + " " + pub.getKey());
      System.out.println("Private Key:");
      // Use Bouncy Castle to write the private key back in PEM format
      // (PKCS#1)
      // http://stackoverflow.com/questions/25129822/export-rsa-public-key-to-pem-string-using-java
      StringWriter privout = new StringWriter();
      JcaPEMWriter privWriter = new JcaPEMWriter(privout);
      privWriter.writeObject(k.getPrivate());
      privWriter.close();
      System.out.println(privout);
    }
  }
}
