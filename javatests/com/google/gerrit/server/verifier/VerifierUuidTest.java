// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.verifier;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.testing.GerritBaseTests;
import org.eclipse.jgit.lib.Ref;
import org.junit.Test;

public class VerifierUuidTest extends GerritBaseTests {
  private static final ImmutableSet<String> INVALID_VERIFIER_UUIDS =
      ImmutableSet.of(
          "",
          "437ee3",
          "Id852b02b44d3148de21603fecbc817d03d6899fe",
          "foo",
          "437ee373885fbc47b103dc722800448320e8bc61-foo",
          "437ee373885fbc47b103dc722800448320e8bc61 foo");

  @Test
  public void createdUuidsForSameInputShouldBeDifferent() {
    String verifierName = "my-verifier";
    String uuid1 = VerifierUuid.make(verifierName);
    String uuid2 = VerifierUuid.make(verifierName);
    assertThat(uuid2).isNotEqualTo(uuid1);
  }

  @Test
  public void isUuid() {
    // valid UUIDs
    assertThat(VerifierUuid.isUuid("437ee373885fbc47b103dc722800448320e8bc61")).isTrue();
    assertThat(VerifierUuid.isUuid(VerifierUuid.make("my-verifier"))).isTrue();

    // invalid UUIDs
    assertThat(VerifierUuid.isUuid(null)).isFalse();
    for (String invalidVerifierUuid : INVALID_VERIFIER_UUIDS) {
      assertThat(VerifierUuid.isUuid(invalidVerifierUuid)).isFalse();
    }
  }

  @Test
  public void checkUuid() {
    // valid UUIDs
    assertThat(VerifierUuid.checkUuid("437ee373885fbc47b103dc722800448320e8bc61"))
        .isEqualTo("437ee373885fbc47b103dc722800448320e8bc61");

    String verifierUuid = VerifierUuid.make("my-verifier");
    assertThat(VerifierUuid.checkUuid(verifierUuid)).isEqualTo(verifierUuid);

    // invalid UUIDs
    assertThatCheckUuidThrowsIllegalStateExceptionFor(null);
    for (String invalidVerifierUuid : INVALID_VERIFIER_UUIDS) {
      assertThatCheckUuidThrowsIllegalStateExceptionFor(invalidVerifierUuid);
    }
  }

  private void assertThatCheckUuidThrowsIllegalStateExceptionFor(@Nullable String verifierUuid) {
    try {
      VerifierUuid.checkUuid(verifierUuid);
      assert_()
          .fail("expected IllegalStateException when checking verifier UUID \"%s\"", verifierUuid);
    } catch (IllegalStateException e) {
      assertThat(e.getMessage())
          .isEqualTo(String.format("invalid verifier UUID: %s", verifierUuid));
    }
  }

  @Test
  public void fromRef() throws Exception {
    // valid verifier refs
    assertThat(VerifierUuid.fromRef("refs/verifiers/43/437ee373885fbc47b103dc722800448320e8bc61"))
        .hasValue("437ee373885fbc47b103dc722800448320e8bc61");

    String verifierUuid = VerifierUuid.make("my-verifier");
    assertThat(VerifierUuid.fromRef(RefNames.refsVerifiers(verifierUuid))).hasValue(verifierUuid);

    // invalid verifier refs
    assertThat(VerifierUuid.fromRef((Ref) null)).isEmpty();
    assertThat(VerifierUuid.fromRef((String) null)).isEmpty();
    assertThat(VerifierUuid.fromRef("")).isEmpty();
    assertThat(VerifierUuid.fromRef("refs/verifiers/437ee373885fbc47b103dc722800448320e8bc61"))
        .isEmpty();
    assertThat(VerifierUuid.fromRef("refs/verifiers/61/437ee373885fbc47b103dc722800448320e8bc61"))
        .isEmpty();
    assertThat(VerifierUuid.fromRef("refs/verifier/43/437ee373885fbc47b103dc722800448320e8bc61"))
        .isEmpty();
    assertThat(VerifierUuid.fromRef("refs/verifier/43/7ee373885fbc47b103dc722800448320e8bc61"))
        .isEmpty();
    assertThat(VerifierUuid.fromRef("refs/verifiers/foo")).isEmpty();
    assertThat(VerifierUuid.fromRef("refs/groups/43/437ee373885fbc47b103dc722800448320e8bc61"))
        .isEmpty();
  }
}
