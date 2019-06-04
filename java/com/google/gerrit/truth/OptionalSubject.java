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

import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.CustomSubjectBuilder;
import com.google.common.truth.DefaultSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StandardSubjectBuilder;
import com.google.common.truth.Subject;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class OptionalSubject<S extends Subject, T> extends Subject {

  private final Optional<T> optional;
  private final BiFunction<StandardSubjectBuilder, ? super T, ? extends S> valueSubjectCreator;

  // TODO(aliceks): Remove when all relevant usages are adapted to new check()/factory approach.
  public static <S extends Subject, T> OptionalSubject<S, T> assertThat(
      Optional<T> optional, Function<? super T, ? extends S> elementAssertThatFunction) {
    Subject.Factory<S, T> valueSubjectFactory =
        (metadata, value) -> elementAssertThatFunction.apply(value);
    return assertThat(optional, valueSubjectFactory);
  }

  public static <S extends Subject, T> OptionalSubject<S, T> assertThat(
      Optional<T> optional, Subject.Factory<S, T> valueSubjectFactory) {
    return assertAbout(optionals()).thatCustom(optional, valueSubjectFactory);
  }

  public static OptionalSubject<Subject, ?> assertThat(Optional<?> optional) {
    return assertAbout(optionals())
        .that(optional, (builder, value) -> (DefaultSubject) builder.that(value));
  }

  public static CustomSubjectBuilder.Factory<OptionalSubjectBuilder> optionals() {
    return OptionalSubjectBuilder::new;
  }

  private OptionalSubject(
      FailureMetadata failureMetadata,
      Optional<T> optional,
      BiFunction<StandardSubjectBuilder, ? super T, ? extends S> valueSubjectCreator) {
    super(failureMetadata, optional);
    this.optional = optional;
    this.valueSubjectCreator = valueSubjectCreator;
  }

  public void isPresent() {
    isNotNull();
    if (!optional.isPresent()) {
      failWithoutActual(fact("expected to have", "value"));
    }
  }

  public void isAbsent() {
    isNotNull();
    if (optional.isPresent()) {
      failWithoutActual(fact("expected not to have", "value"));
    }
  }

  public void isEmpty() {
    isAbsent();
  }

  public S value() {
    isNotNull();
    isPresent();
    return valueSubjectCreator.apply(check("value()"), optional.get());
  }

  public static class OptionalSubjectBuilder extends CustomSubjectBuilder {

    OptionalSubjectBuilder(FailureMetadata failureMetadata) {
      super(failureMetadata);
    }

    public <S extends Subject, T> OptionalSubject<S, T> thatCustom(
        Optional<T> optional, Subject.Factory<S, T> valueSubjectFactory) {
      return that(optional, (builder, value) -> builder.about(valueSubjectFactory).that(value));
    }

    public OptionalSubject<Subject, ?> that(Optional<?> optional) {
      return that(optional, (builder, value) -> (DefaultSubject) builder.that(value));
    }

    public <S extends Subject, T> OptionalSubject<S, T> that(
        Optional<T> optional,
        BiFunction<StandardSubjectBuilder, ? super T, ? extends S> valueSubjectCreator) {
      return new OptionalSubject<>(metadata(), optional, valueSubjectCreator);
    }
  }
}
