// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.truth;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.DefaultSubject;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import java.util.function.Function;
import java.util.Optional;

public class OptionalSubject<S extends Subject<S, ? super T>, T>
    extends Subject<OptionalSubject<S, T>, Optional<T>> {

  private final Function<? super T, ? extends S> valueAssertThatFunction;

  public static <S extends Subject<S, ? super T>, T> OptionalSubject<S, T> assertThat(
      Optional<T> optional, Function<? super T, ? extends S> elementAssertThatFunction) {
    OptionalSubjectFactory<S, T> optionalSubjectFactory =
        new OptionalSubjectFactory<>(elementAssertThatFunction);
    return assertAbout(optionalSubjectFactory).that(optional);
  }

  public static OptionalSubject<DefaultSubject, ?> assertThat(Optional<?> optional) {
    // Unfortunately, we need to cast to DefaultSubject as Truth.assertThat()
    // only returns Subject<DefaultSubject, Object>. There shouldn't be a way
    // for that method not to return a DefaultSubject because the generic type
    // definitions of a Subject are quite strict.
    Function<Object, DefaultSubject> valueAssertThatFunction =
        value -> (DefaultSubject) Truth.assertThat(value);
    return assertThat(optional, valueAssertThatFunction);
  }

  private OptionalSubject(
      FailureStrategy failureStrategy,
      Optional<T> optional,
      Function<? super T, ? extends S> valueAssertThatFunction) {
    super(failureStrategy, optional);
    this.valueAssertThatFunction = valueAssertThatFunction;
  }

  public void isPresent() {
    isNotNull();
    Optional<T> optional = actual();
    if (!optional.isPresent()) {
      fail("has a value");
    }
  }

  public void isAbsent() {
    isNotNull();
    Optional<T> optional = actual();
    if (optional.isPresent()) {
      fail("does not have a value");
    }
  }

  public void isEmpty() {
    isAbsent();
  }

  public S value() {
    isNotNull();
    isPresent();
    Optional<T> optional = actual();
    return valueAssertThatFunction.apply(optional.get());
  }

  private static class OptionalSubjectFactory<S extends Subject<S, ? super T>, T>
      extends SubjectFactory<OptionalSubject<S, T>, Optional<T>> {

    private Function<? super T, ? extends S> valueAssertThatFunction;

    OptionalSubjectFactory(Function<? super T, ? extends S> valueAssertThatFunction) {
      this.valueAssertThatFunction = valueAssertThatFunction;
    }

    @Override
    public OptionalSubject<S, T> getSubject(FailureStrategy failureStrategy, Optional<T> optional) {
      return new OptionalSubject<>(failureStrategy, optional, valueAssertThatFunction);
    }
  }
}
