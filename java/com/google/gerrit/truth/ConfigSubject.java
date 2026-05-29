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

package com.google.gerrit.truth;

import static com.google.common.truth.Truth.assertAbout;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.truth.BooleanSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.LongSubject;
import com.google.common.truth.MultimapSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.common.Nullable;
import java.util.Arrays;
import org.eclipse.jgit.lib.Config;

public class ConfigSubject extends Subject {
  public static ConfigSubject assertThat(Config config) {
    return assertAbout(ConfigSubject::new).that(config);
  }

  private final Config config;

  private ConfigSubject(FailureMetadata metadata, Config actual) {
    super(metadata, actual);
    this.config = actual;
  }

  public IterableSubject sections() {
    isNotNull();
    return check("getSections()").that(config.getSections());
  }

  public IterableSubject subsections(String section) {
    requireNonNull(section);
    isNotNull();
    return check("getSubsections(%s)", section).that(config.getSubsections(section));
  }

  public MultimapSubject sectionValues(String section) {
    requireNonNull(section);
    return sectionValuesImpl(section, null);
  }

  public MultimapSubject subsectionValues(String section, String subsection) {
    requireNonNull(section);
    requireNonNull(subsection);
    return sectionValuesImpl(section, subsection);
  }

  private MultimapSubject sectionValuesImpl(String section, @Nullable String subsection) {
    isNotNull();
    ImmutableListMultimap.Builder<String, String> b = ImmutableListMultimap.builder();
    config
        .getNames(section, subsection, true)
        .forEach(
            n ->
                Arrays.stream(config.getStringList(section, subsection, n))
                    .forEach(v -> b.put(n, v)));
    return check("getSection(%s, %s)", section, subsection).that(b.build());
  }

  public void isEmpty() {
    sections().isEmpty();
  }

  public StringSubject text() {
    isNotNull();
    return check("toText()").that(config.toText());
  }

  public IterableSubject stringValues(String section, @Nullable String subsection, String name) {
    requireNonNull(section);
    requireNonNull(name);
    isNotNull();
    return check("getStringList(%s, %s, %s)", section, subsection, name)
        .that(Arrays.asList(config.getStringList(section, subsection, name)));
  }

  public StringSubject stringValue(String section, @Nullable String subsection, String name) {
    requireNonNull(section);
    requireNonNull(name);
    isNotNull();
    return check("getString(%s, %s, %s)", section, subsection, name)
        .that(config.getString(section, subsection, name));
  }

  public IntegerSubject intValue(
      String section, @Nullable String subsection, String name, int defaultValue) {
    requireNonNull(section);
    requireNonNull(name);
    isNotNull();
    return check("getInt(%s, %s, %s, %s)", section, subsection, name, defaultValue)
        .that(config.getInt(section, subsection, name, defaultValue));
  }

  public LongSubject longValue(String section, String subsection, String name, long defaultValue) {
    requireNonNull(section);
    requireNonNull(name);
    isNotNull();
    return check("getLong(%s, %s, %s, %s)", section, subsection, name, defaultValue)
        .that(config.getLong(section, subsection, name, defaultValue));
  }

  public BooleanSubject booleanValue(
      String section, String subsection, String name, boolean defaultValue) {
    requireNonNull(section);
    requireNonNull(name);
    isNotNull();
    return check("getBoolean(%s, %s, %s, %s)", section, subsection, name, defaultValue)
        .that(config.getBoolean(section, subsection, name, defaultValue));
  }
}
