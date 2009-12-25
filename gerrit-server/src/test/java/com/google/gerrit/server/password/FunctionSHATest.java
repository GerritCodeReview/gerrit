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

public class FunctionSHATest extends TestCase {
  private final FunctionSHA fun = new FunctionSHA();

  public void testName() {
    assertEquals("SHA", fun.getName());
  }

  public void testCheckFoo() {
    final String s = "{SHA}C+7Hteo/D9vJXQ3UfzxbwnXaijM=";
    final String stored = s.substring(s.indexOf('}') + 1);
    assertTrue(fun.check("foo", stored));
    assertFalse(fun.check("fooo", stored));
  }

  public void testDecrypt() {
    final String s = "{SHA}C+7Hteo/D9vJXQ3UfzxbwnXaijM=";
    final String stored = s.substring(s.indexOf('}') + 1);
    assertNull(fun.decrypt(stored));
  }

  public void testEncrypt() {
    assertEquals("Ys23Ag/5IOWqZCw9QGaVDdHwH00=", fun.encrypt(null, "bar"));
    assertEquals("C+7Hteo/D9vJXQ3UfzxbwnXaijM=", fun.encrypt(null, "foo"));
  }
}
