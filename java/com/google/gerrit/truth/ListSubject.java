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

package com.google.gerrit.truth;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.collect.Iterables;
import com.google.common.truth.CustomSubjectBuilder;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.StandardSubjectBuilder;
import com.google.common.truth.Subject;
import java.util.List;
import java.util.function.BiFunction;

public class ListSubject<S extends Subject, E> extends IterableSubject {

  private final List<E> list;
  private final BiFunction<StandardSubjectBuilder, ? super E, ? extends S> elementSubjectCreator;

  public static <S extends Subject, E> ListSubject<S, E> assertThat(
      List<E> list, Subject.Factory<? extends S, ? super E> subjectFactory) {
    return assertAbout(elements()).thatCustom(list, subjectFactory);
  }

  public static CustomSubjectBuilder.Factory<ListSubjectBuilder> elements() {
    return ListSubjectBuilder::new;
  }

  private ListSubject(
      FailureMetadata failureMetadata,
      List<E> list,
      BiFunction<StandardSubjectBuilder, ? super E, ? extends S> elementSubjectCreator) {
    super(failureMetadata, list);
    this.list = list;
    this.elementSubjectCreator = elementSubjectCreator;
  }

  public S element(int index) {
    checkArgument(index >= 0, "index(%s) must be >= 0", index);
    isNotNull();
    if (index >= list.size()) {
      failWithoutActual(fact("expected to have element at index", index));
    }
    return elementSubjectCreator.apply(check("element(%s)", index), list.get(index));
  }

  public S onlyElement() {
    isNotNull();
    hasSize(1);
    return elementSubjectCreator.apply(check("onlyElement()"), Iterables.getOnlyElement(list));
  }

  public S lastElement() {
    isNotNull();
    isNotEmpty();
    return elementSubjectCreator.apply(check("lastElement()"), Iterables.getLast(list));
  }

  public static class ListSubjectBuilder extends CustomSubjectBuilder {

    ListSubjectBuilder(FailureMetadata failureMetadata) {
      super(failureMetadata);
    }

    public <S extends Subject, E> ListSubject<S, E> thatCustom(
        List<E> list, Subject.Factory<? extends S, ? super E> subjectFactory) {
      return that(list, (builder, element) -> builder.about(subjectFactory).that(element));
    }

    public <S extends Subject, E> ListSubject<S, E> that(
        List<E> list,
        BiFunction<StandardSubjectBuilder, ? super E, ? extends S> elementSubjectCreator) {
      return new ListSubject<>(metadata(), list, elementSubjectCreator);
    }
  }
}
