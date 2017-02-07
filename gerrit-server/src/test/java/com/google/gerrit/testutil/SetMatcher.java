// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.testutil;

import com.google.common.collect.Sets;
import java.util.Set;
import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;

/**
 * Match for Iterables via set equals
 *
 * <p>Converts both expected and actual parameter to a set and compares those two sets via equals to
 * determine whether or not they match.
 */
public class SetMatcher<T> implements IArgumentMatcher {
  public static <S extends Iterable<T>, T> S setEq(S expected) {
    EasyMock.reportMatcher(new SetMatcher<>(expected));
    return null;
  }

  Set<T> expected;

  public SetMatcher(Iterable<T> expected) {
    this.expected = Sets.newHashSet(expected);
  }

  @Override
  public boolean matches(Object actual) {
    if (actual instanceof Iterable<?>) {
      Set<?> actualSet = Sets.newHashSet((Iterable<?>) actual);
      return expected.equals(actualSet);
    }
    return false;
  }

  @Override
  public void appendTo(StringBuffer buffer) {
    buffer.append(expected);
  }
}
