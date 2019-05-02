// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.ioutil;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HexFormatTest {

  @Test
  public void fromInt() {
    assertEquals("0000000f", HexFormat.fromInt(0xf));
    assertEquals("801234ab", HexFormat.fromInt(0x801234ab));
    assertEquals("deadbeef", HexFormat.fromInt(0xdeadbeef));
  }
}
