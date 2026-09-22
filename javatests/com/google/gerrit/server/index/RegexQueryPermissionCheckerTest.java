// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.index;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.permissions.RegexPermissionPolicy;
import com.google.inject.util.Providers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RegexQueryPermissionCheckerTest {

  @FunctionalInterface
  interface ValidateFunction {
    void validate() throws QueryParseException;
  }

  @Mock private RegexPermissionPolicy regexPermissionPolicyMock;

  private RegexQueryPermissionChecker regexPermissionChecker;

  @Before
  public void setUp() throws Exception {
    regexPermissionChecker =
        new RegexQueryPermissionChecker(Providers.of(regexPermissionPolicyMock));
  }

  @Test
  public void shouldCheckForRegexPermission_defaultFieldWithRegex() throws Exception {
    verifyChecked(() -> regexPermissionChecker.check(" ^.* "));
  }

  @Test
  public void shouldCheckForRegexPermission_defaultFieldWithNegativeRegex() throws Exception {
    verifyChecked(() -> regexPermissionChecker.check(" -^.* "));
  }

  @Test
  public void shouldNotCheckForRegexPermission_defaultFieldWithoutRegex() throws Exception {
    neverChecked(() -> regexPermissionChecker.check(" some^thing "));
  }

  @Test
  public void shouldCheckForRegexPermission_namedFieldWithRegex() throws Exception {
    verifyChecked(() -> regexPermissionChecker.check("somefield: ^.*"));
  }

  @Test
  public void shouldCheckForRegexPermission_namedFieldWithRegexInBrackets() throws Exception {
    verifyChecked(() -> regexPermissionChecker.check("(somefield: ^.*)"));
  }

  @Test
  public void shouldCheckForRegexPermission_namedFieldPlusDefaultFieldWithRegex() throws Exception {
    verifyChecked(() -> regexPermissionChecker.check("firstfield:foo AND  ^.*"));
  }

  @Test
  public void shouldCheckForRegexPermission_namedFieldWithQuotedCaret() throws Exception {
    verifyChecked(() -> regexPermissionChecker.check("somefield: \"^.*\""));
  }

  @Test
  public void shouldCheckForRegexPermission_namedFieldWithQuotedCurlyCaret() throws Exception {
    verifyChecked(() -> regexPermissionChecker.check("somefield:{^.*}"));
  }

  private void verifyChecked(ValidateFunction body) throws Exception {
    body.validate();
    verify(regexPermissionPolicyMock).check();
  }

  private void neverChecked(ValidateFunction body) throws Exception {
    body.validate();
    verify(regexPermissionPolicyMock, never()).check();
  }
}
