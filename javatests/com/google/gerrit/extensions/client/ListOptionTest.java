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

package com.google.gerrit.extensions.client;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.extensions.client.ListOptionTest.MyOption.BAR;
import static com.google.gerrit.extensions.client.ListOptionTest.MyOption.BAZ;
import static com.google.gerrit.extensions.client.ListOptionTest.MyOption.FOO;
import static org.junit.Assert.fail;

import com.google.common.math.IntMath;
import java.util.EnumSet;
import org.junit.Test;

public class ListOptionTest {
  enum MyOption implements ListOption {
    FOO(0),
    BAR(1),
    BAZ(17);

    private final int value;

    MyOption(int value) {
      this.value = value;
    }

    @Override
    public int getValue() {
      return value;
    }
  }

  @Test
  public void fromBitsStr() {
    try {
      // TODO(hanwen): move GerritJUnit.assertThrows to a place that doesn't depend on everything.
      ListOption.fromHexString(MyOption.class, "xyz");
      fail("must throw");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("32-bit integer");
    }
  }

  @Test
  public void fromBits() {
    assertThat(IntMath.pow(2, BAZ.getValue())).isEqualTo(131072);
    assertThat(ListOption.fromBits(MyOption.class, 0)).isEmpty();
    assertThat(ListOption.fromBits(MyOption.class, 1)).containsExactly(FOO);
    assertThat(ListOption.fromBits(MyOption.class, 2)).containsExactly(BAR);
    assertThat(ListOption.fromBits(MyOption.class, 131072)).containsExactly(BAZ);
    assertThat(ListOption.fromBits(MyOption.class, 3)).containsExactly(FOO, BAR);
    assertThat(ListOption.fromBits(MyOption.class, 131073)).containsExactly(FOO, BAZ);
    assertThat(ListOption.fromBits(MyOption.class, 131074)).containsExactly(BAR, BAZ);
    assertThat(ListOption.fromBits(MyOption.class, 131075)).containsExactly(FOO, BAR, BAZ);

    assertFromBitsFails(4);
    assertFromBitsFails(8);
    assertFromBitsFails(16);
    assertFromBitsFails(250);
  }

  private void assertFromBitsFails(int v) {
    try {
      EnumSet<MyOption> opts = ListOption.fromBits(MyOption.class, v);
      assertWithMessage("expected RuntimeException for fromBits(%s), got: %s", v, opts).fail();
    } catch (RuntimeException e) {
      // Expected.
    }
  }
}
