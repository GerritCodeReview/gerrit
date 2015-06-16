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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.git.SignedPushPreReceiveHook.keyIdToString;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;

public class SignedPushPreReceiveHookTest {
  // ./pubring.gpg
  // -------------
  // pub   1024R/30A5A053 2015-06-16 [expires: 2015-06-17]
  //       Key fingerprint = 96D6 DE78 E6D8 DA49 9387  1F31 FA09 A0C4 30A5 A053
  // uid                  A U. Thor <a_u_thor@example.com>
  // sub   1024R/D6831DC8 2015-06-16 [expires: 2015-06-17]
  private static final String PUBKEY =
      "-----BEGIN PGP PUBLIC KEY BLOCK-----\n"
      + "Version: GnuPG v1\n"
      + "\n"
      + "mI0EVYCBUQEEALCKzuY6M68RRRm6PS1F322lpHSHTdW9PIURm5B//tbfS32EN6lM\n"
      + "ISwJxhanpZanv2o4mbV3V8oLT3jMVDPJ3dqmOZJdJs37l+dxCVJ3ycFe1LHtT2oT\n"
      + "eRyC5PxD7UY5PdDe97mjp7yrp/bx1hE6XqGV0nDGrkJXc8A35u3WzIF5ABEBAAG0\n"
      + "IEEgVS4gVGhvciA8YV91X3Rob3JAZXhhbXBsZS5jb20+iL4EEwECACgFAlWAgVEC\n"
      + "GwMFCQABUYAGCwkIBwMCBhUIAgkKCwQWAgMBAh4BAheAAAoJEPoJoMQwpaBTjhoD\n"
      + "/0MRCX1zBjEKIfzFYeSEg/OcSLbAkUD7un5YTfpgds3oUNIKlIgovWO24TQxrCCu\n"
      + "5pSzN/WfRSzPFhj9HahY/5yh+EGd6HmIU2v/k5I3LwTPEOcZUi1SzOScSv6JOO9Q\n"
      + "3srVilCu3h6TNW1UGBNjfOr1NdmkWfsUZcjsEc/XrfBGuI0EVYCBUQEEAL0UP9jJ\n"
      + "eLj3klCCa2tmwdgyFiSf9T+Yoed4I3v3ag2F0/CWrCJr3e1ogSs4Bdts0WptI+Nu\n"
      + "QIq40AYszewq55dTcB4lbNAYE4svVYQ5AGz78iKzljaBFhyT6ePdZ5wfb+8Jqu1l\n"
      + "7wRwzRI5Jn3OXCmdGm/dmoUNG136EA9A4ZLLABEBAAGIpQQYAQIADwUCVYCBUQIb\n"
      + "DAUJAAFRgAAKCRD6CaDEMKWgU5JTA/9XjwPFZ5NseNROMhYZMmje1/ixISb2jaVc\n"
      + "9m9RLCl8Y3RCY9NNdU5FinTIX9LsRTrJlW6FSG5sin8mwx9jq0eGE1TBEKND5klT\n"
      + "TmsG0jx1dZG9kWDy6lPnIWw2/4W+N0fK/Cw6WEL1Xg7RLi4NQ9Bi2WoxJii9bWMv\n"
      + "yy35U6UfPQ==\n"
      + "=0GL9\n"
      + "-----END PGP PUBLIC KEY BLOCK-----\n";

  private PGPPublicKey key;

  @Before
  public void setUp() throws Exception {
    ArmoredInputStream in = new ArmoredInputStream(
        new ByteArrayInputStream(Constants.encode(PUBKEY)));
    PGPPublicKeyRing keyRing =
        new PGPPublicKeyRing(in, new BcKeyFingerprintCalculator());
    key = keyRing.getPublicKey();
  }

  @Test
  public void testKeyIdToString() throws Exception {
    assertThat(keyIdToString(key.getKeyID()))
        .isEqualTo("30A5A053");
  }

  @Test
  public void testKeyToString() throws Exception {
    assertThat(SignedPushPreReceiveHook.toString(key))
        .isEqualTo("30A5A053 A U. Thor <a_u_thor@example.com>"
          + " (96D6 DE78 E6D8 DA49 9387  1F31 FA09 A0C4 30A5 A053)");
  }

  @Test
  public void testKeyObjectId() throws Exception {
    String objId = SignedPushPreReceiveHook.keyObjectId(key.getKeyID()).name();
    assertThat(objId).isEqualTo("fa09a0c430a5a053000000000000000000000000");
    assertThat(objId.substring(8, 16))
        .isEqualTo(keyIdToString(key.getKeyID()).toLowerCase());
  }

  @Test
  public void testToUserId() throws Exception {
    assertThat(SignedPushPreReceiveHook.toUserId(
          new PersonIdent("A U. Thor", "a_u_thor@example.com", 0, 0)))
        .isEqualTo("A U. Thor <a_u_thor@example.com>");
  }
}
