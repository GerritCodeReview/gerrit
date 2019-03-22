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

public class ListSubject<S extends Subject<S, E>, E> extends IterableSubject {

  private final BiFunction<StandardSubjectBuilder, E, S> elementSubjectCreator;

  public static <S extends Subject<S, E>, E> ListSubject<S, E> assertThat(
      List<E> list, Subject.Factory<S, E> subjectFactory) {
    return assertAbout(elements()).thatCustom(list, subjectFactory);
  }

  public static CustomSubjectBuilder.Factory<ListSubjectBuilder> elements() {
    return ListSubjectBuilder::new;
  }

  private ListSubject(
      FailureMetadata failureMetadata,
      List<E> list,
      BiFunction<StandardSubjectBuilder, E, S> elementSubjectCreator) {
    super(failureMetadata, list);
    this.elementSubjectCreator = elementSubjectCreator;
  }

  public S element(int index) {
    checkArgument(index >= 0, "index(%s) must be >= 0", index);
    isNotNull();
    List<E> list = getActualList();
    if (index >= list.size()) {
      failWithoutActual(fact("expected to have element at index", index));
    }
    return elementSubjectCreator.apply(check("element(%s)", index), list.get(index));
  }

  public S onlyElement() {
    isNotNull();
    hasSize(1);
    List<E> list = getActualList();
    return elementSubjectCreator.apply(check("onlyElement()"), Iterables.getOnlyElement(list));
  }

  public S lastElement() {
    isNotNull();
    isNotEmpty();
    List<E> list = getActualList();
    return elementSubjectCreator.apply(check("lastElement()"), Iterables.getLast(list));
  }

  @SuppressWarnings("unchecked")
  private List<E> getActualList() {
    // The constructor only accepts lists. -> Casting is appropriate.
    return (List<E>) actual();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ListSubject<S, E> named(String s, Object... objects) {
    // This object is returned which is of type ListSubject. -> Casting is appropriate.
    return (ListSubject<S, E>) super.named(s, objects);
  }

  public static class ListSubjectBuilder extends CustomSubjectBuilder {

    ListSubjectBuilder(FailureMetadata failureMetadata) {
      super(failureMetadata);
    }

    public <S extends Subject<S, E>, E> ListSubject<S, E> thatCustom(
        List<E> list, Subject.Factory<S, E> subjectFactory) {
      return that(list, (builder, element) -> builder.about(subjectFactory).that(element));
    }

    public <S extends Subject<S, E>, E> ListSubject<S, E> that(
        List<E> list, BiFunction<StandardSubjectBuilder, E, S> elementSubjectCreator) {
      return new ListSubject<>(metadata(), list, elementSubjectCreator);
    }
  }
}
