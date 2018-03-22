// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitRequirement;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.Module;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;

public class ChangeSubmitRequirementIT extends AbstractDaemonTest {
  private static final SubmitRequirement req =
      SubmitRequirement.builder()
          .setType("custom_rule")
          .setFallbackText("Fallback text")
          .addCustomValue("key", "value")
          .build();
  private static final SubmitRequirementInfo reqInfo =
      new SubmitRequirementInfo(
          "NOT_READY", "Fallback text", "custom_rule", ImmutableMap.of("key", "value"));

  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        bind(SubmitRule.class)
            .annotatedWith(Exports.named("CustomSubmitRule"))
            .to(CustomSubmitRule.class);
      }
    };
  }

  @Test
  public void checkSubmitRequirementIsPropagated() throws Exception {
    PushOneCommit.Result r = createChange();

    ChangeInfo result = gApi.changes().id(r.getChangeId()).get();
    assertThat(result.requirements).containsExactly(reqInfo);
  }

  public static class CustomSubmitRule implements SubmitRule {
    @Override
    public Collection<SubmitRecord> evaluate(ChangeData changeData, SubmitRuleOptions options) {
      SubmitRecord record = new SubmitRecord();
      record.labels = new ArrayList<>();
      record.status = SubmitRecord.Status.NOT_READY;
      record.requirements = ImmutableList.of(req);
      return ImmutableList.of(record);
    }
  }
}
