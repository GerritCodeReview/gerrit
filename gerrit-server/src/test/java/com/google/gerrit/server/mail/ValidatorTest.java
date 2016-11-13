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

package com.google.gerrit.server.mail;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.junit.Test;

public class ValidatorTest {
  private static final String UNSUPPORTED_PREFIX = "#! ";

  @Test
  public void validateLocalDomain() throws Exception {
    assertThat(OutgoingEmailValidator.isValid("foo@bar.local")).isTrue();
  }

  @Test
  public void validateTopLevelDomains() throws Exception {
    try (InputStream in = this.getClass().getResourceAsStream("tlds-alpha-by-domain.txt")) {
      if (in == null) {
        throw new Exception("TLD list not found");
      }
      BufferedReader r = new BufferedReader(new InputStreamReader(in));
      String tld;
      while ((tld = r.readLine()) != null) {
        if (tld.startsWith("# ") || tld.startsWith("XN--")) {
          // Ignore comments and non-latin domains
          continue;
        }
        if (tld.startsWith(UNSUPPORTED_PREFIX)) {
          String test = "test@example." + tld.toLowerCase().substring(UNSUPPORTED_PREFIX.length());
          assert_()
              .withFailureMessage("expected invalid TLD \"" + test + "\"")
              .that(OutgoingEmailValidator.isValid(test))
              .isFalse();
        } else {
          String test = "test@example." + tld.toLowerCase();
          assert_()
              .withFailureMessage("failed to validate TLD \"" + test + "\"")
              .that(OutgoingEmailValidator.isValid(test))
              .isTrue();
        }
      }
    }
  }
}
