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

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class PublicKeyCheckerTest {
  private PublicKeyChecker checker;

  @Before
  public void setUp() {
    checker = new PublicKeyChecker();
  }

  @Test
  public void validKey() throws Exception {
    assertProblems(TestKey.key1());
  }

  @Test
  public void wrongKeyId() throws Exception {
    TestKey k = TestKey.key1();
    long badId = k.getKeyId() + 1;
    CheckResult result = checker.check(
        k.getPublicKey(), badId, k.getFirstUserId());
    assertEquals(
        Arrays.asList("Public key does not match ID 46328A8D"),
        result.getProblems());
  }

  @Test
  public void wrongUserId() throws Exception {
    TestKey k = TestKey.key1();
    CheckResult result = checker.check(
        k.getPublicKey(), k.getKeyId(), "test2@example.com");
    assertEquals(
        Arrays.asList("No certification for User ID test2@example.com"),
        result.getProblems());
  }

  @Test
  public void keyExpiringInFuture() throws Exception {
    assertProblems(TestKey.key2());
  }

  @Test
  public void expiredKey() throws Exception {
    assertProblems(TestKey.key3(), "Key is expired");
  }

  @Test
  public void selfRevokedKey() throws Exception {
    assertProblems(TestKey.key4(), "Key is revoked");
  }

  private void assertProblems(TestKey tk, String... expected) throws Exception {
    CheckResult result = checker.check(
        tk.getPublicKey(), tk.getKeyId(), tk.getFirstUserId());
    assertEquals(Arrays.asList(expected), result.getProblems());
  }
}
