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

package com.google.gerrit.server.mail.send;

import static org.apache.commons.validator.routines.DomainValidator.ArrayType.GENERIC_PLUS;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.DomainValidator.Item;
import org.apache.commons.validator.routines.EmailValidator;
import org.eclipse.jgit.lib.Config;

/**
 * Validator that checks if an email address is valid and allowed for receiving notification emails.
 *
 * <p>An email address is valid if it is syntactically correct.
 *
 * <p>An email address is allowed if its top level domain is allowed by Gerrit's configuration.
 */
@Singleton
public class OutgoingEmailValidator {
  private final EmailValidator validator;

  @Inject
  OutgoingEmailValidator(@GerritServerConfig Config config) {
    String[] allowTLD = config.getStringList("sendemail", null, "allowTLD");
    DomainValidator domainValidator =
        allowTLD.length == 0
            ? DomainValidator.getInstance(true)
            : DomainValidator.getInstance(true, List.of(new Item(GENERIC_PLUS, allowTLD)));
    this.validator = new EmailValidator(true, true, domainValidator);
  }

  public boolean isValid(String addr) {
    return validator.isValid(addr);
  }
}
