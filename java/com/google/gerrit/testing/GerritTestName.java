// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.testing;

import com.google.common.base.CharMatcher;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class GerritTestName implements TestRule {
  private final TestName delegate = new TestName();

  public String getSanitizedMethodName() {
    String name = delegate.getMethodName().toLowerCase();
    name =
        CharMatcher.inRange('a', 'z')
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9'))
            .negate()
            .replaceFrom(name, '_');
    name = CharMatcher.is('_').trimTrailingFrom(name);
    return name;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return delegate.apply(base, description);
  }
}
