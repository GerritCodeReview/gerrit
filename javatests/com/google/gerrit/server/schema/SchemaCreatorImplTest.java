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

package com.google.gerrit.server.schema;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class SchemaCreatorImplTest {
  @Inject private AllProjectsName allProjects;

  @Inject private GitRepositoryManager repoManager;

  @Inject private SchemaCreator schemaCreator;

  @Inject private ProjectConfig.Factory projectConfigFactory;

  @Before
  public void setUp() throws Exception {
    new InMemoryModule().inject(this);
    schemaCreator.create();
  }

  private LabelTypes getLabelTypes() throws Exception {
    ProjectConfig c = projectConfigFactory.create(allProjects);
    try (Repository repo = repoManager.openRepository(allProjects)) {
      c.load(repo);
      return new LabelTypes(ImmutableList.copyOf(c.getLabelSections().values()));
    }
  }

  @Test
  public void createSchema_LabelTypes() throws Exception {
    List<String> labels = new ArrayList<>();
    for (LabelType label : getLabelTypes().getLabelTypes()) {
      labels.add(label.getName());
    }
    assertThat(labels).containsExactly("Code-Review");
  }

  @Test
  public void createSchema_Label_CodeReview() throws Exception {
    LabelType codeReview = getLabelTypes().byLabel("Code-Review");
    assertThat(codeReview).isNotNull();
    assertThat(codeReview.getName()).isEqualTo("Code-Review");
    assertThat(codeReview.getDefaultValue()).isEqualTo(0);
    assertThat(codeReview.getFunction()).isEqualTo(LabelFunction.MAX_WITH_BLOCK);
    assertThat(codeReview.isCopyMinScore()).isTrue();
    assertValueRange(codeReview, -2, -1, 0, 1, 2);
  }

  private void assertValueRange(LabelType label, Integer... range) {
    List<Integer> rangeList = Arrays.asList(range);
    assertThat(rangeList).isNotEmpty();
    assertThat(rangeList).isInStrictOrder();

    assertThat(label.getValues().stream().map(v -> (int) v.getValue()))
        .containsExactlyElementsIn(rangeList)
        .inOrder();
    assertThat(label.getMax().getValue()).isEqualTo(Collections.max(rangeList));
    assertThat(label.getMin().getValue()).isEqualTo(Collections.min(rangeList));
    for (LabelValue v : label.getValues()) {
      assertThat(v.getText()).isNotNull();
      assertThat(v.getText()).isNotEmpty();
    }
  }
}
