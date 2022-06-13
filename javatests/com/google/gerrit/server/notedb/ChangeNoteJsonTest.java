// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.gson.Gson;
import com.google.inject.TypeLiteral;
import java.util.Optional;
import org.junit.Test;

public class ChangeNoteJsonTest {
  private final Gson gson = new ChangeNoteJson().getGson();

  static class Child {
    Optional<String> optionalValue;
  }

  static class Parent {
    Optional<Child> optionalChild;
  }

  @Test
  public void shouldSerializeAndDeserializeEmptyOptional() {
    // given
    Optional<?> empty = Optional.empty();

    // when
    String json = gson.toJson(empty);

    // then
    assertThat(json).isEqualTo("{}");

    // and when
    Optional<?> result = gson.fromJson(json, Optional.class);

    // and then
    assertThat(result).isEmpty();
  }

  @Test
  public void shouldSerializeAndDeserializeNonEmptyOptional() {
    // given
    String value = "foo";
    Optional<String> nonEmpty = Optional.of(value);

    // when
    String json = gson.toJson(nonEmpty);

    // then
    assertThat(json).isEqualTo("{\n  \"value\": \"" + value + "\"\n}");

    // and when
    Optional<String> result = gson.fromJson(json, new TypeLiteral<Optional<String>>() {}.getType());

    // and then
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(value);
  }

  @Test
  public void shouldSerializeAndDeserializeNestedNonEmptyOptional() {
    String value = "foo";
    Child fooChild = new Child();
    fooChild.optionalValue = Optional.of(value);
    Parent parent = new Parent();
    parent.optionalChild = Optional.of(fooChild);

    String json = gson.toJson(parent);

    assertThat(json)
        .isEqualTo(
            "{\n"
                + "  \"optionalChild\": {\n"
                + "    \"value\": {\n"
                + "      \"optionalValue\": {\n"
                + "        \"value\": \"foo\"\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}");

    Parent result = gson.fromJson(json, new TypeLiteral<Parent>() {}.getType());

    assertThat(result.optionalChild).isPresent();
    assertThat(result.optionalChild.get().optionalValue).isPresent();
    assertThat(result.optionalChild.get().optionalValue.get()).isEqualTo(value);
  }

  @Test
  public void shouldSerializeAndDeserializeNestedEmptyOptional() {
    Child fooChild = new Child();
    fooChild.optionalValue = Optional.empty();
    Parent parent = new Parent();
    parent.optionalChild = Optional.of(fooChild);

    String json = gson.toJson(parent);

    assertThat(json)
        .isEqualTo(
            "{\n"
                + "  \"optionalChild\": {\n"
                + "    \"value\": {\n"
                + "      \"optionalValue\": {}\n"
                + "    }\n"
                + "  }\n"
                + "}");

    Parent result = gson.fromJson(json, new TypeLiteral<Parent>() {}.getType());

    assertThat(result.optionalChild).isPresent();
    assertThat(result.optionalChild.get().optionalValue).isEmpty();
  }
}
