// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.cache.proto.Cache;
import java.util.Arrays;
import org.junit.Test;

/** Test for {@link UserPreferenceFields}. */
public class UserPreferenceFieldsTest {

  @Test
  public void storageAdapter_singleValueRoundTrip() {
    UserPreferenceFields.StorageAdapter<Integer> adapter =
        UserPreferenceFields.StorageAdapter.single(Object::toString, Integer::valueOf);
    assertThat(adapter.fromString(ImmutableList.of("1"))).isEqualTo(1);
    assertThat(adapter.toString(1)).isEqualTo(ImmutableList.of("1"));
  }

  @Test
  public void storageAdapter_repeatedValueRoundTrip() {
    UserPreferenceFields.StorageAdapter<ImmutableList<Integer>> adapter =
        UserPreferenceFields.StorageAdapter.repeated(
            l -> l.stream().map(Object::toString).collect(toImmutableList()),
            l -> l.stream().map(Integer::valueOf).collect(toImmutableList()));
    assertThat(adapter.fromString(ImmutableList.of("1", "2"))).isEqualTo(ImmutableList.of(1, 2));
    assertThat(adapter.toString(ImmutableList.of(1, 2))).isEqualTo(ImmutableList.of("1", "2"));
  }

  @Test
  public void fieldTest_noValue() {
    UserPreferenceFields.Field<Integer> field =
        UserPreferenceFields.Field.of(
            UserPreferenceSection.GENERAL, "someKey", 123, UserPreferenceFields.INTEGER_ADAPTER);
    Cache.UserPreferences preferences = Cache.UserPreferences.newBuilder().build();
    assertThat(field.get(preferences)).isNull();
    assertThat(field.getOrDefault(preferences)).isEqualTo(123);
  }

  @Test
  public void fieldTest_withValue() {
    UserPreferenceFields.Field<Integer> field =
        UserPreferenceFields.Field.of(
            UserPreferenceSection.GENERAL, "someKey", 123, UserPreferenceFields.INTEGER_ADAPTER);
    Cache.UserPreferences preferences =
        Cache.UserPreferences.newBuilder().putGeneral("someKey", repeated("100")).build();
    assertThat(field.get(preferences)).isEqualTo(100);
    assertThat(field.getOrDefault(preferences)).isEqualTo(100);
  }

  @Test
  public void repeatedFieldTest_noValue() {
    UserPreferenceFields.Field<ImmutableList<String>> field =
        UserPreferenceFields.Field.of(
            UserPreferenceSection.EDIT,
            "someKey",
            ImmutableList.of("foo", "bar"),
            UserPreferenceFields.REPEATED_STRING_ADAPTER);
    Cache.UserPreferences preferences = Cache.UserPreferences.newBuilder().build();
    assertThat(field.get(preferences)).isNull();
    assertThat(field.getOrDefault(preferences)).isEqualTo(ImmutableList.of("foo", "bar"));
  }

  @Test
  public void repeatedFieldTest_withValue() {
    UserPreferenceFields.Field<ImmutableList<String>> field =
        UserPreferenceFields.Field.of(
            UserPreferenceSection.EDIT,
            "someKey",
            ImmutableList.of("foo", "bar"),
            UserPreferenceFields.REPEATED_STRING_ADAPTER);
    Cache.UserPreferences preferences =
        Cache.UserPreferences.newBuilder().putEdit("someKey", repeated("override")).build();
    assertThat(field.get(preferences)).isEqualTo(ImmutableList.of("override"));
    assertThat(field.getOrDefault(preferences)).isEqualTo(ImmutableList.of("override"));
  }

  private static Cache.UserPreferences.RepeatedPreference repeated(String... val) {
    Cache.UserPreferences.RepeatedPreference.Builder b =
        Cache.UserPreferences.RepeatedPreference.newBuilder();
    Arrays.stream(val).forEach(e -> b.addValue(e));
    return b.build();
  }
}
