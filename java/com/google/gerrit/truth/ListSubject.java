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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import java.util.List;
import java.util.function.Function;

public class ListSubject<S extends Subject<S, E>, E> extends IterableSubject {

  private final Function<E, S> elementAssertThatFunction;

  @SuppressWarnings("unchecked")
  public static <S extends Subject<S, E>, E> ListSubject<S, E> assertThat(
      List<E> list, Function<E, S> elementAssertThatFunction) {
    // The ListSubjectFactory always returns ListSubjects. -> Casting is appropriate.
    return (ListSubject<S, E>)
        assertAbout(new ListSubjectFactory<>(elementAssertThatFunction)).that(list);
  }

  private ListSubject(
      FailureMetadata failureMetadata, List<E> list, Function<E, S> elementAssertThatFunction) {
    super(failureMetadata, list);
    this.elementAssertThatFunction = elementAssertThatFunction;
  }

  public S element(int index) {
    checkArgument(index >= 0, "index(%s) must be >= 0", index);
    isNotNull();
    List<E> list = getActualList();
    if (index >= list.size()) {
      failWithoutActual(fact("expected to have element at index", index));
    }
    return elementAssertThatFunction.apply(list.get(index));
  }

  public S onlyElement() {
    isNotNull();
    hasSize(1);
    return element(0);
  }

  public S lastElement() {
    isNotNull();
    isNotEmpty();
    List<E> list = getActualList();
    return element(list.size() - 1);
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

  private static class ListSubjectFactory<S extends Subject<S, T>, T>
      implements Subject.Factory<IterableSubject, Iterable<?>> {

    private Function<T, S> elementAssertThatFunction;

    ListSubjectFactory(Function<T, S> elementAssertThatFunction) {
      this.elementAssertThatFunction = elementAssertThatFunction;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ListSubject<S, T> createSubject(FailureMetadata failureMetadata, Iterable<?> objects) {
      // The constructor of ListSubject only accepts lists. -> Casting is appropriate.
      return new ListSubject<>(failureMetadata, (List<T>) objects, elementAssertThatFunction);
    }
  }
}
