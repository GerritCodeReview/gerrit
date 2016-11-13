// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import org.junit.Test;

public class IdGeneratorTest {
  @Test
  public void test1234() {
    final HashSet<Integer> seen = new HashSet<>();
    for (int i = 0; i < 1 << 16; i++) {
      final int e = IdGenerator.mix(i);
      assertTrue("no duplicates", seen.add(e));
      assertEquals("mirror image", i, IdGenerator.unmix(e));
    }
    assertEquals(0x801234ab, IdGenerator.unmix(IdGenerator.mix(0x801234ab)));
    assertEquals(0xc0ffee12, IdGenerator.unmix(IdGenerator.mix(0xc0ffee12)));
    assertEquals(0xdeadbeef, IdGenerator.unmix(IdGenerator.mix(0xdeadbeef)));
    assertEquals(0x0b966b11, IdGenerator.unmix(IdGenerator.mix(0x0b966b11)));
  }

  @Test
  public void testFormat() {
    assertEquals("0000000f", IdGenerator.format(0xf));
    assertEquals("801234ab", IdGenerator.format(0x801234ab));
    assertEquals("deadbeef", IdGenerator.format(0xdeadbeef));
  }
}
