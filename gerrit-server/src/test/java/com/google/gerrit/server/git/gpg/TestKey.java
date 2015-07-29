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

package com.google.gerrit.server.git.gpg;

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

import java.io.ByteArrayInputStream;
import java.io.IOException;

class TestKey {
  /**
   * A valid key with no expiration.
   *
   * <pre>
   * pub   2048R/46328A8C 2015-07-08
   *       Key fingerprint = 04AE A7ED 2F82 1133 E5B1  28D1 ED06 25DC 4632 8A8C
   * uid                  Testuser One &lt;test1@example.com&gt;
   * sub   2048R/F0AF69C0 2015-07-08
   * </pre>
   */
  static TestKey key1() throws PGPException, IOException {
    return new TestKey("-----BEGIN PGP PUBLIC KEY BLOCK-----\n"
        + "Version: GnuPG v1\n"
        + "\n"
        + "mQENBFWdTIkBCADOaygDKjLuRX6LXAvBAYB91cmTf1MSlmEy+qsG3c9ijjQixPkr\n"
        + "atdYkocrrT2S0R9UGjksTOI2WN5S0lQfLA1RSk63KURQE+OF+IfNqdD6nQdLBs1w\n"
        + "va+GDj/uvuI05I0oXf/M7POdFphutrS4EUDBnFPj6ns/0C2sTRTxliD+Y9Y9a84V\n"
        + "DfVVUbJB6wc3LP3L6ImT+cSM7dLq3hZHya+9FNeYPmPYnBrkJyqf2NDd38Sddsro\n"
        + "7smw/GgCZHnnuVNS4C7NsHr6900VKC+JDtdx+fqptixcAEJWiGoQfWqU+hYmia3p\n"
        + "9+Xw02+3FcjOT6ONUCmHX+xlz0pXW4iIYlPpABEBAAG0IFRlc3R1c2VyIE9uZSA8\n"
        + "dGVzdDFAZXhhbXBsZS5jb20+iQE4BBMBAgAiBQJVnUyJAhsDBgsJCAcDAgYVCAIJ\n"
        + "CgsEFgIDAQIeAQIXgAAKCRDtBiXcRjKKjHblB/9RaFO5+GTDIphAL/aVj2u+d8Lq\n"
        + "yUpBrDp3P06QDGpKGFMAovBuh+NLH76VKNIzQLQC8rdTj651fLcLMuJ1enQ3Rblg\n"
        + "RKr1oc+wqqtFHr4QyOQjE/N3C9GQjEzfqn4qnp5KtZxYFnlvU5NGehid7M1HTZMx\n"
        + "jRcHbM9KQnsE5Z4fh4wmN5ynG+5nbaF4O9otPOpFzYRvIhxFmHscWyOgRaMZiYEX\n"
        + "7Qkzze+scAlc9E/EWRJQIFcxnxV/SYIT4qCTT1g2aKA8OCBO/ZTOleH8SzvTODjy\n"
        + "W0lGHnh/ZqH6XGVcGUaJZZ2uHTck1+czuVVShNcXPW1W20T6E9UqzHbJHN0guQEN\n"
        + "BFWdTIkBCACoLVdPr3gpQwzI+2NGXjdtoyqYoPlgfeyI2M1XQD/7+rLZTbi14ZjN\n"
        + "vYkS/+/oGtVEmiYOiAVTwmkjCYkKGDgNcCiJVekiPAN6JryVv488wRc999b5LpFE\n"
        + "fhLGwI0YxjcS4KFFnpMC3wSb6tJUnHRLVoE5d8icdiaOpgYdp7uqWkSx2oxqHgIb\n"
        + "nuyrk3ydEcS4ZeGD+w+taIxMc9F1DS9kiXALD7xWgUkmqZLEQoNgF6KlwCHXRd3m\n"
        + "rBCo97sE95yKcq98ZMIWuQtTcEccZsN/6jlsei+9RI0tqs+FbZnIFm/go9zk11Vl\n"
        + "IQ9QFSj6ruqoKrYvNZuDDLD1lHvZPD4/ABEBAAGJAR8EGAECAAkFAlWdTIkCGwwA\n"
        + "CgkQ7QYl3EYyiox+HAf/Z/OCQO3jxALAcn3oUb1g/IlHm6qZv7RJOFUsj/16fGiF\n"
        + "rRTP15zMXzyqV+L/LGV/owvOsdD/o7boZz4C/U98COx0Nl1jOrmPATOl+xqsgpEj\n"
        + "Fhk+eAR7exO2XxW+u2g4cYoSMosIOX5w1GrdsxQeaZDwiSJMEOR2cVLs3YI19Ci/\n"
        + "FuzActZ0wJNk0nlNF6l8CAbzwN6pM9OIc/iBIwDjz92KUco0NF8XKZnxqhH4wfHB\n"
        + "PGkTx8RwOvELUTDMtvYnG5R0QtND0RbOnmp4ZVZmeOjKSLo1mZliUZB1H2PPSxrA\n"
        + "0oLr8+wLntz1SU7uS4ddvhSQW+j2M/0pa352KUwmrw==\n"
        + "=o/aU\n"
        + "-----END PGP PUBLIC KEY BLOCK-----\n",
        "-----BEGIN PGP PRIVATE KEY BLOCK-----\n"
        + "Version: GnuPG v1\n"
        + "\n"
        + "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\n"
        + "=MuAn\n"
        + "-----END PGP PRIVATE KEY BLOCK-----\n");
  }

  /**
   * A valid key expiring in 2065.
   *
   * <pre>
   * pub   2048R/378A0AED 2015-07-08 [expires: 2065-06-25]
   *       Key fingerprint = C378 369A CBCD 34CC 138D  90B1 4531 1A6F 378A 0AED
   * uid                  Testuser Two &lt;test2@example.com&gt;
   * sub   2048R/46D4F204 2015-07-08 [expires: 2065-06-25]
   * </pre>
   */
  static final TestKey key2() throws PGPException, IOException {
    return new TestKey(
        "-----BEGIN PGP PUBLIC KEY BLOCK-----\n"
        + "Version: GnuPG v1\n"
        + "\n"
        + "mQENBFWdTP8BCADRxNpasIv0jtNXTK6VYIS2VJ2Xk0ZD6gtxeoXCpjQ+TsB9fxh3\n"
        + "vAMPt2Zu5LqoGwygKOJj1zquG8xk7GUCCHJk3+qG8xxB1xGtSz2vLyfRm7fOZmHj\n"
        + "3W/C/25lynSPDrfvcwvwA4PN8iP5EWbWU10L6WOZGMwwwtVDUSEouSOw2LEepxLV\n"
        + "rkKuZcyHaivheDbUlZliwe9rGXd4hh1h4qyNQWG3q+ytlL28sVkOzUh6IMBTvqhe\n"
        + "IRsvxvaVSLV8jRVKfUTqw0g57ft4ZD2/L46yUTXzr9aUCBjTNxvWLlyboqql/D8P\n"
        + "inp51h3cvAg7NW5RdG1GEYmylH8SygT5utPxABEBAAG0IFRlc3R1c2VyIFR3byA8\n"
        + "dGVzdDJAZXhhbXBsZS5jb20+iQE+BBMBAgAoBQJVnUz/AhsDBQld/A8ABgsJCAcD\n"
        + "AgYVCAIJCgsEFgIDAQIeAQIXgAAKCRBFMRpvN4oK7UZqCACWwQL/YvBK4b0m+R0d\n"
        + "UdvAXeBx7DwOAnAodis9ZVqChb7RxcZQxF1Ti9mtCBPPQGuEs5wE2Ocrrq+L13r6\n"
        + "bgW+1WOB1tZSDVxwL1PnZFw/SyADRIDCZrOHiAkp82UnZwWAkk39GzNJtt1wTYDZ\n"
        + "FMTFUr2SPscXk1k7muS+ZfEFwNPD4tODo/poJKDYEJ80Z5UXXFQLDtsfdeIXMFIT\n"
        + "449CYoq8XBMBfvyWl/LLpw0r3JI6pV/YdH3Oeuz8XkkEVzRxaxB6Zmeo5jSwjR/T\n"
        + "8TKDGwwiuwiiT3SfkFSVdcjKulRuXSRNs1Ouf7/UC3cq4bG2WXWa85X1+HQRm7iu\n"
        + "RHSOuQENBFWdTP8BCADhhGxAA0pX5yBHwIgM1j0gw2h5nSsopDrO6t/sbRUcNxnR\n"
        + "tBScgKZnP0sjRTYEUIwmZuseHMBohtVCuMaDt06qyZDvDk/98j3AeE5t2dgFnOIe\n"
        + "qCrm/6aejbFcQOpxe6U29KJRCAxuwNtB15X1VH1Kj7B0gRSTu13n/5sUsi2lunoZ\n"
        + "oIvpIe9tZH4aXitCY2MCQH+hTyCyNBzlEa44kWz6LxUsPdo7I6rXkTr6Ot7wQh+9\n"
        + "7HCe042GIq65h0apgujyjhJidjch5ur1mngaSNSEyvbji2MGC+cd3wAIstG5a7xP\n"
        + "d9MncY5Q/eH+hn96694k5bckottSyGm/3f2Ihfj1ABEBAAGJASUEGAECAA8FAlWd\n"
        + "TP8CGwwFCV38DwAACgkQRTEabzeKCu1FNwgAif4eK2v7R3QubL2S6wmb1nsgRMgV\n"
        + "YoxGBeUk2EK6WZ5IPor93ySd0ixRVNMRmJ8BLH3EMjZQTzkDG+BH6zFyxo6lLHw9\n"
        + "NxQjI06tqQWgyyK0mEweVwB/zqtxiB4lNUpsNbqOZWnBJ3d6o1SsnD2Q3uwvP5fb\n"
        + "fSIgdmUk3c0VMdgA+KzWjPD/PJIPujE+ckHhjn5cbDNw35/FuyhkLJfqlOG7SPvM\n"
        + "NmCdJ1Pcqju9t7sf6b0BGPDOCL4gpuWKK7HJz9WxngNb3FSziLbyPLk13ynADO+v\n"
        + "EOR44LPyXE9kVxPusazsXlt9ayTOhELhwzw7sGFFu8E17Cpn7GnVj3tN9A==\n"
        + "=1e/A\n"
        + "-----END PGP PUBLIC KEY BLOCK-----\n",
        "-----BEGIN PGP PRIVATE KEY BLOCK-----\n"
        + "Version: GnuPG v1\n"
        + "\n"
        + "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\n"
        + "9A==\n"
        + "=qbV3\n"
        + "-----END PGP PRIVATE KEY BLOCK-----\n");
  }

  /**
   * A key that expired in 2006.
   *
   * <pre>
   * pub   2048R/17DE1ACD 2005-07-08 [expired: 2006-07-08]
   *       Key fingerprint = 1D9E EB79 DD38 B049 939D  9CAF 3CEC 781B 17DE 1ACD
   * uid                  Testuser Three &lt;test3@example.com&gt;
   * </pre>
   */
  static final TestKey key3() throws PGPException, IOException {
    return new TestKey(
        "-----BEGIN PGP PUBLIC KEY BLOCK-----\n"
        + "Version: GnuPG v1\n"
        + "\n"
        + "mQENBELOp0UBCACxholOPWuKhK+TYb88nvLUSCMvTLIFEpb5u3Eavr0wiluEzq6H\n"
        + "55nswAD3dQm8DWxA7yUlEYjPr5btpw7V9441bb1+qtgZMJ10RTdEb/WjyctdGA99\n"
        + "uOKBEarWbt8W+w6lyJ9NXy5bS/x5EwHHfoTFp4ff6ffHI5hbx1a00K8oxmitgd0X\n"
        + "Mx86UmauFNJYupZOZG9gEcP4RbRp7e2pm4Jy1WLEOeg9Fdgm5e5Hj2nMkCSZ9BKV\n"
        + "cxuOllSVzM/Zp0/4+RS9R57jKo3/V74Whwh9yQNgL9UxdNk7L0eGqvaT3EjXxjOc\n"
        + "RCeJiucGN/0W2iq+V01/QGspp4SKtAogWBozABEBAAG0IlRlc3R1c2VyIFRocmVl\n"
        + "IDx0ZXN0M0BleGFtcGxlLmNvbT6JAT4EEwECACgFAkLOp0UCGwMFCQHhM4AGCwkI\n"
        + "BwMCBhUIAgkKCwQWAgMBAh4BAheAAAoJEDzseBsX3hrNYg0H/2CMm5/JDQNSuRFC\n"
        + "ECWLrcOeimuvwbmkonNzOkvKbGXl73GStISAksRWAHBQED1rEPC0NkFCDeVZO7df\n"
        + "SYLlsqKwV6uSh05Ra0F5XeniC12YpAyzoQyCGRS2wLaS822j0zUPXA8XLaO2blCu\n"
        + "R+8sNu/oecMRcFK4S9NaApi3vdqBNhLiN1/Lpqn1LfB8uIO+eaUf4PmCWbaPgzSk\n"
        + "qcPfKZmocNXdgLV5Q80n3hc2y2nrl+vDW2M+eVZuDHAok2BOD9uGKFfLAbaXLbX5\n"
        + "btBW2L0UHtoEyiqkRfD6lX2laSLQmA6+eup7e4GS+s0vXBuVh8XEYddV6Yjt8H7/\n"
        + "2thO41K5AQ0EQs6nRQEIAM/833UHK1DuFlOm7/n18dRMvs7BkXvg+hPquKWMG3be\n"
        + "eE4sh1NG5DbRCdo6iacZLarWr3FDz7J9+wswRhtHCh3pGHEuaJk52vRjQxlkNh5F\n"
        + "p5u2R4WF546bWqX45xPdLfHVTPyWB9q7aVxE+6Q+MHa6lMoyTVnTVCOy3nshiihw\n"
        + "dxLsxaga+QmaL0bAR+dRcO6ucj7TDQXz1AJAVp26c0LXV9iErhFuuybUZKT0a9Aj\n"
        + "FoumMZ6l+k30sSdjSjpBMsNvPos0dTPPRXUMu77o5sj+pHa4o8WctgB3o7BHQELp\n"
        + "KgujZ2sKC9Nm395u6Q4cqUWihzb/Y7rIRuNHJarI7vUAEQEAAYkBJQQYAQIADwUC\n"
        + "Qs6nRQIbDAUJAeEzgAAKCRA87HgbF94azRiBB/4vAyOOjUjK3lDWjHGs7mvEWJI/\n"
        + "1MeLlGPswCSInJBa+HMiMI4tzq+hu5ejGThojNbmnL96GdzfDkMlP4Feyxb2rjtb\n"
        + "NrD/R5tlXHmjX/QLzep4LCeMziP80fu8qUeiOej/Ecdny0w365PlMdt10RaYR8VE\n"
        + "ZX/DAie6JfElnfQcG5q8TIOH3i71qxV+kIoPqKWfQ0MXrNEJ3BYFfDGdUt8U1Kq9\n"
        + "OuIHVRgGS7mMSyjgNqqp7MBeMY+PFFZaZel5yoYVjb9d3L8XvVv2eoa/jPj5FUEU\n"
        + "kE9uxNmwaD1PiV8DvBTYI+eQL4qzfu+3NTG2SfgQYtj5oiGHw8aL3U6QHDJb\n"
        + "=d/Xp\n"
        + "-----END PGP PUBLIC KEY BLOCK-----\n",
        "-----BEGIN PGP PRIVATE KEY BLOCK-----\n"
        + "Version: GnuPG v1\n"
        + "\n"
        + "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\n"
        + "HDJb\n"
        + "=RrXv\n"
        + "-----END PGP PRIVATE KEY BLOCK-----\n");
  }

  /**
   * A self-revoked key with no expiration.
   *
   * <pre>
   * pub   2048R/7CA87821 2015-07-08 [revoked: 2015-07-08]
   *       Key fingerprint = E328 CAB1 1F7E B1BC 1451  ABA5 0855 2A17 7CA8 7821
   * uid                  Testuser Four &lt;test4@example.com&gt;
   * </pre>
   */
  static final TestKey key4() throws PGPException, IOException {
    return new TestKey(
        "-----BEGIN PGP PUBLIC KEY BLOCK-----\n"
        + "Version: GnuPG v1\n"
        + "\n"
        + "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\n"
        + "=477N\n"
        + "-----END PGP PUBLIC KEY BLOCK-----\n",
        "-----BEGIN PGP PRIVATE KEY BLOCK-----\n"
        + "Version: GnuPG v1\n"
        + "\n"
        + "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\n"
        + "=5aNq\n"
        + "-----END PGP PRIVATE KEY BLOCK-----\n");
  }

  // TODO(dborowitz): Figure out how to get gpg to revoke a key for someone
  // else.

  private final String pubArmored;
  private final String secArmored;
  private final PGPPublicKeyRing pubRing;
  private final PGPSecretKeyRing secRing;

  private TestKey(String pubArmored, String secArmored)
      throws PGPException, IOException {
    this.pubArmored = pubArmored;
    this.secArmored = secArmored;
    BcKeyFingerprintCalculator fc = new BcKeyFingerprintCalculator();
    this.pubRing = new PGPPublicKeyRing(newStream(pubArmored), fc);
    this.secRing = new PGPSecretKeyRing(newStream(secArmored), fc);
  }

  String getPublicKeyArmored() {
    return pubArmored;
  }

  String getSecretKeyArmored() {
    return secArmored;
  }

  PGPPublicKeyRing getPublicKeyRing() {
    return pubRing;
  }

  PGPPublicKey getPublicKey() {
    return pubRing.getPublicKey();
  }

  PGPSecretKey getSecretKey() {
    return secRing.getSecretKey();
  }

  long getKeyId() {
    return getPublicKey().getKeyID();
  }

  String getFirstUserId() {
    return (String) getPublicKey().getUserIDs().next();
  }

  PGPPrivateKey getPrivateKey() throws PGPException {
    return getSecretKey().extractPrivateKey(
        new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider())
          // All test keys have no passphrase.
          .build(new char[0]));
  }

  private static ArmoredInputStream newStream(String armored)
      throws IOException {
    return new ArmoredInputStream(
        new ByteArrayInputStream(Constants.encode(armored)));
  }

}
