package com.googlesource.gerrit.convertkey;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSchException;

import org.apache.sshd.common.util.Buffer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.GeneralSecurityException;

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
