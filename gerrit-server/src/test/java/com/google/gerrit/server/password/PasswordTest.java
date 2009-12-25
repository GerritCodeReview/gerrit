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

import com.google.inject.Guice;

import junit.framework.TestCase;

public class PasswordTest extends TestCase {
  public void testModule() {
    final Password p =
        Guice.createInjector(new PasswordModule()).getInstance(Password.class);

    String stored = p.encrypt("foo");
    assertTrue("default is SSHA", stored.startsWith("{SSHA}"));
    assertTrue("SSHA checks password", p.check("foo", stored));
    assertFalse("SSHA checks password", p.check("bar", stored));
    assertNull("SSHA cannot decrypt", p.decrypt(stored));

    assertNull("unknown method fails", p.decrypt("{NOT.A.METHOD}foo"));
    assertFalse("unknown method fails", p.check("foo", "{NOT.A.METHOD}foo"));

    assertTrue("CLEARTEXT checks password", p.check("foo", "{CLEARTEXT}foo"));
    assertFalse("CLEARTEXT checks password", p.check("bar", "{CLEARTEXT}foo"));
  }

  public void testDefaultFunctions() {
    final Password p =
        Guice.createInjector(new PasswordModule()).getInstance(Password.class);

    assertTrue("SSHA", p.check("foo", "{SSHA}h+0XUcmOOKjr3MwnGe0WlnV0oUpRUVFR"));
    assertTrue("SHA", p.check("foo", "{SHA}C+7Hteo/D9vJXQ3UfzxbwnXaijM="));
    assertTrue("SMD5", p.check("foo", "{SMD5}vxC4qGFmqTW80bzSopYLqFFRUVE="));
    assertTrue("MD5", p.check("foo", "{MD5}rL0Y20zC+Fzt72VPzMSk2A=="));
    assertTrue("CLEARTEXT", p.check("foo", "{CLEARTEXT}foo"));
  }
}
