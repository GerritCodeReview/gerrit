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

import com.google.gerrit.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.EmailValidator;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OutgoingEmailValidator {
  private static final Logger log = LoggerFactory.getLogger(OutgoingEmailValidator.class);

  @Inject
  OutgoingEmailValidator(@GerritServerConfig Config config) {
    String[] allowTLD = config.getStringList("sendemail", null, "allowTLD");
    if (allowTLD.length != 0) {
      try {
        DomainValidator.updateTLDOverride(GENERIC_PLUS, allowTLD);
      } catch (IllegalStateException e) {
        // Should only happen in tests, where the OutgoingEmailValidator
        // is instantiated repeatedly.
        log.error("Failed to update TLD override: " + e.getMessage());
      }
    }
  }

  public boolean isValid(String addr) {
    return EmailValidator.getInstance(true, true).isValid(addr);
  }
}
