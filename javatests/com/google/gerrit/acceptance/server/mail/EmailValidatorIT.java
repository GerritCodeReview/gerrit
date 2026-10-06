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
import static org.apache.commons.validator.routines.DomainValidator.ArrayType.COUNTRY_CODE_RO;
import static org.apache.commons.validator.routines.DomainValidator.ArrayType.GENERIC_RO;
import static org.apache.commons.validator.routines.DomainValidator.ArrayType.INFRASTRUCTURE_RO;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.inject.Inject;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.DomainValidator.ArrayType;
import org.junit.Test;

public class EmailValidatorIT extends AbstractDaemonTest {
  @Inject private OutgoingEmailValidator validator;

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
    for (ArrayType tldType : new ArrayType[] {INFRASTRUCTURE_RO, COUNTRY_CODE_RO, GENERIC_RO}) {
      for (String tld : DomainValidator.getTLDEntries(tldType)) {
        String test = "test@example." + tld;
        assertWithMessage("failed to validate TLD \"" + test + "\"")
            .that(validator.isValid(test))
            .isTrue();
      }
    }
    assertThat(validator.isValid("test@example.invalid")).isFalse();
  }
}
