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

package com.google.gerrit.server.password;

import junit.framework.TestCase;

public class FunctionMD5Test extends TestCase {
  private final FunctionMD5 fun = new FunctionMD5();

  public void testName() {
    assertEquals("MD5", fun.getName());
  }

  public void testCheckFoo() {
    final String s = "{MD5}rL0Y20zC+Fzt72VPzMSk2A==";
    final String stored = s.substring(s.indexOf('}') + 1);
    assertTrue(fun.check("foo", stored));
    assertFalse(fun.check("fooo", stored));
  }

  public void testDecrypt() {
    final String s = "{MD5}rL0Y20zC+Fzt72VPzMSk2A==";
    final String stored = s.substring(s.indexOf('}') + 1);
    assertNull(fun.decrypt(stored));
  }

  public void testEncrypt() {
    assertEquals("N7UdGUp1E+RbVvZSTy1R8g==", fun.encrypt(null, "bar"));
    assertEquals("rL0Y20zC+Fzt72VPzMSk2A==", fun.encrypt(null, "foo"));
  }
}
