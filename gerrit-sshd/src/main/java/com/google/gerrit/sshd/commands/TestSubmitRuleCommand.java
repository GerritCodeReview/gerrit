// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.TestSubmitRule;
import com.google.gerrit.server.change.TestSubmitRule.Input;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.inject.Inject;
import com.google.inject.Provider;

/** Command that allows testing of prolog submit-rules in a live instance. */
@CommandMetaData(name = "rule", description = "Test prolog submit rules")
final class TestSubmitRuleCommand extends BaseTestPrologCommand {
  @Inject
  private Provider<TestSubmitRule> view;

  @Override
  protected RestModifyView<RevisionResource, Input> createView() {
    return view.get();
  }
}
