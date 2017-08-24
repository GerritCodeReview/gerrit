// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.mail;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class EmailValidatorIT extends AbstractDaemonTest {
  private static final String UNSUPPORTED_PREFIX = "#! ";

  @Inject private OutgoingEmailValidator validator;

  @BeforeClass
  public static void setUpClass() throws Exception {
    // Reset before first use, in case other tests have already run in this JVM.
    resetDomainValidator();
  }

  @After
  public void tearDown() throws Exception {
    resetDomainValidator();
  }

  private static void resetDomainValidator() throws Exception {
    Class<?> c = Class.forName("org.apache.commons.validator.routines.DomainValidator");
    Field f = c.getDeclaredField("inUse");
    f.setAccessible(true);
    f.setBoolean(c, false);
  }

  @Test
  @GerritConfig(name = "sendemail.allowTLD", value = "example")
  public void testCustomTopLevelDomain() throws Exception {
    assertThat(validator.isValid("foo@bar.local")).isFalse();
    assertThat(validator.isValid("foo@bar.example")).isTrue();
    assertThat(validator.isValid("foo@example")).isTrue();
  }

  @Test
  @GerritConfig(name = "sendemail.allowTLD", value = "a")
  public void testCustomTopLevelDomainOneCharacter() throws Exception {
    assertThat(validator.isValid("foo@bar.local")).isFalse();
    assertThat(validator.isValid("foo@bar.a")).isTrue();
    assertThat(validator.isValid("foo@a")).isTrue();
  }

  @Test
  public void validateTopLevelDomains() throws Exception {
    try (InputStream in = this.getClass().getResourceAsStream("tlds-alpha-by-domain.txt")) {
      if (in == null) {
        throw new Exception("TLD list not found");
      }
      BufferedReader r = new BufferedReader(new InputStreamReader(in, UTF_8));
      String tld;
      while ((tld = r.readLine()) != null) {
        if (tld.startsWith("# ") || tld.startsWith("XN--")) {
          // Ignore comments and non-latin domains
          continue;
        }
        if (tld.startsWith(UNSUPPORTED_PREFIX)) {
          String test = "test@example." + tld.toLowerCase().substring(UNSUPPORTED_PREFIX.length());
          assertWithMessage("expected invalid TLD \"" + test + "\"")
              .that(validator.isValid(test))
              .isFalse();
        } else {
          String test = "test@example." + tld.toLowerCase();
          assertWithMessage("failed to validate TLD \"" + test + "\"")
              .that(validator.isValid(test))
              .isTrue();
        }
      }
    }
  }
}
