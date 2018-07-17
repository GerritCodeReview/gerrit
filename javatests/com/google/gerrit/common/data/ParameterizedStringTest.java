// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.common.data;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ParameterizedStringTest {
  @Test
  public void emptyString() {
    ParameterizedString p = new ParameterizedString("");
    assertThat(p.getPattern()).isEmpty();
    assertThat(p.getRawPattern()).isEmpty();
    assertThat(p.getParameterNames()).isEmpty();

    Map<String, String> a = new HashMap<>();
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).isEmpty();
    assertThat(p.replace(a)).isEmpty();
  }

  @Test
  public void asis1() {
    ParameterizedString p = ParameterizedString.asis("${bar}c");
    assertThat(p.getPattern()).isEqualTo("${bar}c");
    assertThat(p.getRawPattern()).isEqualTo("${bar}c");
    assertThat(p.getParameterNames()).isEmpty();

    Map<String, String> a = new HashMap<>();
    a.put("bar", "frobinator");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).isEmpty();
    assertThat(p.replace(a)).isEqualTo("${bar}c");
  }

  @Test
  public void replace1() {
    ParameterizedString p = new ParameterizedString("${bar}c");
    assertThat(p.getPattern()).isEqualTo("${bar}c");
    assertThat(p.getRawPattern()).isEqualTo("{0}c");
    assertThat(p.getParameterNames()).containsExactly("bar");

    Map<String, String> a = new HashMap<>();
    a.put("bar", "frobinator");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("frobinator");
    assertThat(p.replace(a)).isEqualTo("frobinatorc");
  }

  @Test
  public void replace2() {
    ParameterizedString p = new ParameterizedString("a${bar}c");
    assertThat(p.getPattern()).isEqualTo("a${bar}c");
    assertThat(p.getRawPattern()).isEqualTo("a{0}c");
    assertThat(p.getParameterNames()).containsExactly("bar");

    Map<String, String> a = new HashMap<>();
    a.put("bar", "frobinator");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("frobinator");
    assertThat(p.replace(a)).isEqualTo("afrobinatorc");
  }

  @Test
  public void replace3() {
    ParameterizedString p = new ParameterizedString("a${bar}");
    assertThat(p.getPattern()).isEqualTo("a${bar}");
    assertThat(p.getRawPattern()).isEqualTo("a{0}");
    assertThat(p.getParameterNames()).containsExactly("bar");

    Map<String, String> a = new HashMap<>();
    a.put("bar", "frobinator");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("frobinator");
    assertThat(p.replace(a)).isEqualTo("afrobinator");
  }

  @Test
  public void replace4() {
    ParameterizedString p = new ParameterizedString("a${bar}c");
    assertThat(p.getPattern()).isEqualTo("a${bar}c");
    assertThat(p.getRawPattern()).isEqualTo("a{0}c");
    assertThat(p.getParameterNames()).containsExactly("bar");

    Map<String, String> a = new HashMap<>();
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEmpty();
    assertThat(p.replace(a)).isEqualTo("ac");
  }

  @Test
  public void replaceToLowerCase() {
    ParameterizedString p = new ParameterizedString("${a.toLowerCase}");
    assertThat(p.getParameterNames()).containsExactly("a");

    Map<String, String> a = new HashMap<>();

    a.put("a", "foo");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo");
    assertThat(p.replace(a)).isEqualTo("foo");

    a.put("a", "FOO");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo");
    assertThat(p.replace(a)).isEqualTo("foo");
  }

  @Test
  public void replaceToUpperCase() {
    ParameterizedString p = new ParameterizedString("${a.toUpperCase}");
    assertThat(p.getParameterNames()).containsExactly("a");

    Map<String, String> a = new HashMap<>();

    a.put("a", "foo");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("FOO");
    assertThat(p.replace(a)).isEqualTo("FOO");

    a.put("a", "FOO");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("FOO");
    assertThat(p.replace(a)).isEqualTo("FOO");
  }

  @Test
  public void replaceLocalName() {
    ParameterizedString p = new ParameterizedString("${a.localPart}");
    assertThat(p.getParameterNames()).containsExactly("a");

    Map<String, String> a = new HashMap<>();

    a.put("a", "foo@example.com");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo");
    assertThat(p.replace(a)).isEqualTo("foo");

    a.put("a", "foo");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo");
    assertThat(p.replace(a)).isEqualTo("foo");
  }

  @Test
  public void undefinedFunctionName() {
    ParameterizedString p =
        new ParameterizedString(
            "hi, ${userName.toUpperCase},your eamil address is '${email.toLowerCase.localPart}'.right?");
    assertThat(p.getParameterNames()).containsExactly("userName", "email");

    Map<String, String> a = new HashMap<>();
    a.put("userName", "firstName lastName");
    a.put("email", "FIRSTNAME.LASTNAME@EXAMPLE.COM");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(2);

    assertThat(p.bind(a)[0]).isEqualTo("FIRSTNAME LASTNAME");
    assertThat(p.bind(a)[1]).isEqualTo("firstname.lastname");
    assertThat(p.replace(a))
        .isEqualTo("hi, FIRSTNAME LASTNAME,your eamil address is 'firstname.lastname'.right?");
  }

  @Test
  public void replaceToUpperCaseToLowerCase() {
    ParameterizedString p = new ParameterizedString("${a.toUpperCase.toLowerCase}");
    assertThat(p.getParameterNames()).containsExactly("a");

    Map<String, String> a = new HashMap<>();

    a.put("a", "FOO@EXAMPLE.COM");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo@example.com");
    assertThat(p.replace(a)).isEqualTo("foo@example.com");

    a.put("a", "foo@example.com");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo@example.com");
    assertThat(p.replace(a)).isEqualTo("foo@example.com");
  }

  @Test
  public void replaceToUpperCaseLocalName() {
    ParameterizedString p = new ParameterizedString("${a.toUpperCase.localPart}");
    assertThat(p.getParameterNames()).containsExactly("a");

    Map<String, String> a = new HashMap<>();

    a.put("a", "foo@example.com");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("FOO");
    assertThat(p.replace(a)).isEqualTo("FOO");

    a.put("a", "FOO@EXAMPLE.COM");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("FOO");
    assertThat(p.replace(a)).isEqualTo("FOO");
  }

  @Test
  public void replaceToUpperCaseAnUndefinedMethod() {
    ParameterizedString p = new ParameterizedString("${a.toUpperCase.anUndefinedMethod}");
    assertThat(p.getParameterNames()).containsExactly("a");

    Map<String, String> a = new HashMap<>();

    a.put("a", "foo@example.com");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("FOO@EXAMPLE.COM");
    assertThat(p.replace(a)).isEqualTo("FOO@EXAMPLE.COM");

    a.put("a", "FOO@EXAMPLE.COM");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("FOO@EXAMPLE.COM");
    assertThat(p.replace(a)).isEqualTo("FOO@EXAMPLE.COM");
  }

  @Test
  public void replaceLocalNameToUpperCase() {
    ParameterizedString p = new ParameterizedString("${a.localPart.toUpperCase}");
    assertThat(p.getParameterNames()).containsExactly("a");

    Map<String, String> a = new HashMap<>();

    a.put("a", "foo@example.com");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("FOO");
    assertThat(p.replace(a)).isEqualTo("FOO");

    a.put("a", "FOO@EXAMPLE.COM");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("FOO");
    assertThat(p.replace(a)).isEqualTo("FOO");
  }

  @Test
  public void replaceLocalNameToLowerCase() {
    ParameterizedString p = new ParameterizedString("${a.localPart.toLowerCase}");
    assertThat(p.getParameterNames()).containsExactly("a");

    Map<String, String> a = new HashMap<>();

    a.put("a", "FOO@EXAMPLE.COM");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo");
    assertThat(p.replace(a)).isEqualTo("foo");

    a.put("a", "foo@example.com");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo");
    assertThat(p.replace(a)).isEqualTo("foo");
  }

  @Test
  public void replaceLocalNameAnUndefinedMethod() {
    ParameterizedString p = new ParameterizedString("${a.localPart.anUndefinedMethod}");
    assertThat(p.getParameterNames()).containsExactly("a");

    Map<String, String> a = new HashMap<>();

    a.put("a", "FOO@EXAMPLE.COM");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("FOO");
    assertThat(p.replace(a)).isEqualTo("FOO");

    a.put("a", "foo@example.com");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo");
    assertThat(p.replace(a)).isEqualTo("foo");
  }

  @Test
  public void replaceToLowerCaseToUpperCase() {
    ParameterizedString p = new ParameterizedString("${a.toLowerCase.toUpperCase}");
    assertThat(p.getParameterNames()).hasSize(1);
    assertThat(p.getParameterNames()).containsExactly("a");

    Map<String, String> a = new HashMap<>();

    a.put("a", "FOO@EXAMPLE.COM");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("FOO@EXAMPLE.COM");
    assertThat(p.replace(a)).isEqualTo("FOO@EXAMPLE.COM");

    a.put("a", "foo@example.com");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("FOO@EXAMPLE.COM");
    assertThat(p.replace(a)).isEqualTo("FOO@EXAMPLE.COM");
  }

  @Test
  public void replaceToLowerCaseLocalName() {
    ParameterizedString p = new ParameterizedString("${a.toLowerCase.localPart}");
    assertThat(p.getParameterNames()).containsExactly("a");

    Map<String, String> a = new HashMap<>();

    a.put("a", "FOO@EXAMPLE.COM");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo");
    assertThat(p.replace(a)).isEqualTo("foo");

    a.put("a", "foo@example.com");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo");
    assertThat(p.replace(a)).isEqualTo("foo");
  }

  @Test
  public void replaceToLowerCaseAnUndefinedMethod() {
    ParameterizedString p = new ParameterizedString("${a.toLowerCase.anUndefinedMethod}");
    assertThat(p.getParameterNames()).containsExactly("a");

    Map<String, String> a = new HashMap<>();

    a.put("a", "foo@example.com");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo@example.com");
    assertThat(p.replace(a)).isEqualTo("foo@example.com");

    a.put("a", "FOO@EXAMPLE.COM");
    assertThat(p.bind(a)).isNotNull();
    assertThat(p.bind(a)).hasLength(1);
    assertThat(p.bind(a)[0]).isEqualTo("foo@example.com");
    assertThat(p.replace(a)).isEqualTo("foo@example.com");
  }

  @Test
  public void replaceSubmitTooltipWithVariables() {
    ParameterizedString p = new ParameterizedString("Submit patch set ${patchSet} into ${branch}");
    assertThat(p.getParameterNames()).hasSize(2);
    assertThat(p.getParameterNames()).containsExactly("patchSet", "branch");

    Map<String, String> params =
        ImmutableMap.of(
            "patchSet", "42",
            "branch", "foo");
    assertThat(p.bind(params)).isNotNull();
    assertThat(p.bind(params)).hasLength(2);
    assertThat(p.bind(params)[0]).isEqualTo("42");
    assertThat(p.bind(params)[1]).isEqualTo("foo");
    assertThat(p.replace(params)).isEqualTo("Submit patch set 42 into foo");
  }

  @Test
  public void replaceSubmitTooltipWithoutVariables() {
    ParameterizedString p = new ParameterizedString("Submit patch set 40 into master");
    Map<String, String> params =
        ImmutableMap.of(
            "patchSet", "42",
            "branch", "foo");
    assertThat(p.bind(params)).isEmpty();
    assertThat(p.replace(params)).isEqualTo("Submit patch set 40 into master");
  }
}
