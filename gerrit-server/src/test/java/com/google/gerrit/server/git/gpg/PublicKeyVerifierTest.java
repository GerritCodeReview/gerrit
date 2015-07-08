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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.git.gpg.PublicKeyVerifier.Result;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.junit.Test;

public class PublicKeyVerifierTest {
  @Test
  public void validKey() throws Exception {
    assertProblems(TestKey.KEY1);
  }

  @Test
  public void wrongKeyId() throws Exception {
    PGPPublicKey pk = TestKey.KEY1.getPublicKey();
    long badId = pk.getKeyID() + 1;
    Result result = PublicKeyVerifier.verify(
        pk, badId, TestKey.KEY1.getFirstUserId());
    assertThat(result.getProblems()).containsExactly(
        "Public key does not match ID 46328A8D");
  }

  @Test
  public void wrongUserId() throws Exception {
    PGPPublicKey pk = TestKey.KEY1.getPublicKey();
    Result result = PublicKeyVerifier.verify(
        pk, pk.getKeyID(), "test2@example.com");
    assertThat(result.getProblems()).containsExactly(
        "No certification for User ID test2@example.com");
  }

  @Test
  public void keyExpiringInFuture() throws Exception {
    assertProblems(TestKey.KEY2);
  }

  @Test
  public void expiredKey() throws Exception {
    assertProblems(TestKey.KEY3, "Key is expired");
  }

  @Test
  public void selfRevokedKey() throws Exception {
    assertProblems(TestKey.KEY4, "Key is revoked");
  }

  private static void assertProblems(TestKey tk, String... expected)
      throws Exception {
    PGPPublicKey pk = tk.getPublicKey();
    Result result = PublicKeyVerifier.verify(
        pk, pk.getKeyID(), tk.getFirstUserId());
    assertThat(result.getProblems()).containsExactly((Object[]) expected);
  }
}
