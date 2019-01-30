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

package com.google.gerrit.server.checker;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.testing.GerritBaseTests;
import org.junit.Test;

public class CheckerUuidTest extends GerritBaseTests {
  private static final ImmutableSet<String> INVALID_CHECKER_UUIDS =
      ImmutableSet.of(
          "",
          "437ee3",
          "Id852b02b44d3148de21603fecbc817d03d6899fe",
          "foo",
          "437ee373885fbc47b103dc722800448320e8bc61-foo",
          "437ee373885fbc47b103dc722800448320e8bc61 foo");

  @Test
  public void createdUuidsForSameInputShouldBeDifferent() {
    String checkerName = "my-checker";
    String uuid1 = CheckerUuid.make(checkerName);
    String uuid2 = CheckerUuid.make(checkerName);
    assertThat(uuid2).isNotEqualTo(uuid1);
  }

  @Test
  public void isUuid() {
    // valid UUIDs
    assertThat(CheckerUuid.isUuid("437ee373885fbc47b103dc722800448320e8bc61")).isTrue();
    assertThat(CheckerUuid.isUuid(CheckerUuid.make("my-checker"))).isTrue();

    // invalid UUIDs
    assertThat(CheckerUuid.isUuid(null)).isFalse();
    for (String invalidCheckerUuid : INVALID_CHECKER_UUIDS) {
      assertThat(CheckerUuid.isUuid(invalidCheckerUuid)).isFalse();
    }
  }

  @Test
  public void checkUuid() {
    // valid UUIDs
    assertThat(CheckerUuid.checkUuid("437ee373885fbc47b103dc722800448320e8bc61"))
        .isEqualTo("437ee373885fbc47b103dc722800448320e8bc61");

    String checkerUuid = CheckerUuid.make("my-checker");
    assertThat(CheckerUuid.checkUuid(checkerUuid)).isEqualTo(checkerUuid);

    // invalid UUIDs
    assertThatCheckUuidThrowsIllegalStateExceptionFor(null);
    for (String invalidCheckerUuid : INVALID_CHECKER_UUIDS) {
      assertThatCheckUuidThrowsIllegalStateExceptionFor(invalidCheckerUuid);
    }
  }

  private void assertThatCheckUuidThrowsIllegalStateExceptionFor(@Nullable String checkerUuid) {
    try {
      CheckerUuid.checkUuid(checkerUuid);
      assert_()
          .fail("expected IllegalStateException when checking checker UUID \"%s\"", checkerUuid);
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).isEqualTo(String.format("invalid checker UUID: %s", checkerUuid));
    }
  }
}
