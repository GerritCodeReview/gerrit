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

import com.google.inject.Singleton;
import java.lang.reflect.Method;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OutgoingEmailValidator {
  private static final Logger log = LoggerFactory.getLogger(OutgoingEmailValidator.class);

  OutgoingEmailValidator() {
    try {
      initializeMailValidator();
    } catch (IllegalStateException e) {
      // Should only happen in tests, where the OutgoingEmailValidator
      // is instantiated repeatedly.
      log.error("Failed to update TLD override: " + e.getMessage());
      resetMailValidator();
      initializeMailValidator();
    }
  }

  public boolean isValid(String addr) {
    return EmailValidator.getInstance(true, true).isValid(addr);
  }

  private void initializeMailValidator() {
    DomainValidator.updateTLDOverride(GENERIC_PLUS, new String[] {"local"});
  }

  private void resetMailValidator() {
    try {
      Class<?> c = Class.forName("org.apache.commons.validator.routines.DomainValidator");
      Method m = c.getDeclaredMethod("clearTLDOverrides", new Class<?>[] {});
      m.setAccessible(true);
      m.invoke(c, new Object[] {});
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to reset mail validator for tests", e);
    }
  }
}
