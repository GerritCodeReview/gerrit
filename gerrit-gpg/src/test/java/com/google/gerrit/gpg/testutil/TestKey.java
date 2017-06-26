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

package com.google.gerrit.gpg.testutil;

import static com.google.gerrit.gpg.PublicKeyStore.keyIdToString;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.eclipse.jgit.lib.Constants;

public class TestKey {
  private final String pubArmored;
  private final String secArmored;
  private final PGPPublicKeyRing pubRing;
  private final PGPSecretKeyRing secRing;

  public TestKey(String pubArmored, String secArmored) {
    this.pubArmored = pubArmored;
    this.secArmored = secArmored;
    BcKeyFingerprintCalculator fc = new BcKeyFingerprintCalculator();
    try {
      this.pubRing = new PGPPublicKeyRing(newStream(pubArmored), fc);
      this.secRing = new PGPSecretKeyRing(newStream(secArmored), fc);
    } catch (PGPException | IOException e) {
      throw new AssertionError(e);
    }
  }

  public String getPublicKeyArmored() {
    return pubArmored;
  }

  public String getSecretKeyArmored() {
    return secArmored;
  }

  public PGPPublicKeyRing getPublicKeyRing() {
    return pubRing;
  }

  public PGPPublicKey getPublicKey() {
    return pubRing.getPublicKey();
  }

  public PGPSecretKey getSecretKey() {
    return secRing.getSecretKey();
  }

  public long getKeyId() {
    return getPublicKey().getKeyID();
  }

  public String getKeyIdString() {
    return keyIdToString(getPublicKey().getKeyID());
  }

  public String getFirstUserId() {
    return (String) getPublicKey().getUserIDs().next();
  }

  public PGPPrivateKey getPrivateKey() throws PGPException {
    return getSecretKey()
        .extractPrivateKey(
            new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider())
                // All test keys have no passphrase.
                .build(new char[0]));
  }

  private static ArmoredInputStream newStream(String armored) throws IOException {
    return new ArmoredInputStream(new ByteArrayInputStream(Constants.encode(armored)));
  }
}
