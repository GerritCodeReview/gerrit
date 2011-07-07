// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.test;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class RsaKeyPair {

  private final byte[] publicKey;

  private final byte[] privateKey;

  private RsaKeyPair(final byte[] publicKey, final byte[] privateKey) {
    this.publicKey = publicKey;
    this.privateKey = privateKey;
  }

  public static RsaKeyPair create() {
    final byte[] publicKey;
    final byte[] privateKey;
    try {
      final KeyPair keyPair = KeyPair.genKeyPair(new JSch(), KeyPair.RSA);

      ByteArrayOutputStream publicKeyStream = new ByteArrayOutputStream();
      keyPair.writePublicKey(publicKeyStream, null);
      publicKeyStream.flush();
      publicKey = publicKeyStream.toByteArray();

      ByteArrayOutputStream privateKeyStream = new ByteArrayOutputStream();
      keyPair.writePrivateKey(privateKeyStream);
      privateKeyStream.flush();
      privateKey = privateKeyStream.toByteArray();

    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (JSchException e) {
      throw new RuntimeException(e);
    }

    return new RsaKeyPair(publicKey, privateKey);

  }

  public byte[] getPublicKey() {
    return publicKey;
  }

  public String getPublicKeyAsText() {
    return new String(publicKey);
  }

  public byte[] getPrivateKey() {
    return privateKey;
  }

}
