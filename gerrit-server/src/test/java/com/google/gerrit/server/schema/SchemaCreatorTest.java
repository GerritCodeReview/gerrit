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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SchemaCreatorTest {
  @Inject private AllProjectsName allProjects;

  @Inject private GitRepositoryManager repoManager;

  @Inject private InMemoryDatabase db;

  private LifecycleManager lifecycle;

  @Before
  public void setUp() throws Exception {
    lifecycle = new LifecycleManager();
    new InMemoryModule().inject(this);
    lifecycle.start();
  }

  @After
  public void tearDown() throws Exception {
    if (lifecycle != null) {
      lifecycle.stop();
    }
    InMemoryDatabase.drop(db);
  }

  @Test
  public void testGetCauses_CreateSchema() throws OrmException, SQLException, IOException {
    // Initially the schema should be empty.
    String[] types = {"TABLE", "VIEW"};
    try (JdbcSchema d = (JdbcSchema) db.open();
        ResultSet rs = d.getConnection().getMetaData().getTables(null, null, null, types)) {
      assertThat(rs.next()).isFalse();
    }

    // Create the schema using the current schema version.
    //
    db.create();
    db.assertSchemaVersion();

    // By default sitePath is set to the current working directory.
    //
    File sitePath = new File(".").getAbsoluteFile();
    if (sitePath.getName().equals(".")) {
      sitePath = sitePath.getParentFile();
    }
    assertThat(db.getSystemConfig().sitePath).isEqualTo(sitePath.getCanonicalPath());
  }

  private LabelTypes getLabelTypes() throws Exception {
    db.create();
    ProjectConfig c = new ProjectConfig(allProjects);
    try (Repository repo = repoManager.openRepository(allProjects)) {
      c.load(repo);
      return new LabelTypes(ImmutableList.copyOf(c.getLabelSections().values()));
    }
  }

  @Test
  public void testCreateSchema_LabelTypes() throws Exception {
    List<String> labels = new ArrayList<>();
    for (LabelType label : getLabelTypes().getLabelTypes()) {
      labels.add(label.getName());
    }
    assertThat(labels).containsExactly("Code-Review");
  }

  @Test
  public void testCreateSchema_Label_CodeReview() throws Exception {
    LabelType codeReview = getLabelTypes().byLabel("Code-Review");
    assertThat(codeReview).isNotNull();
    assertThat(codeReview.getName()).isEqualTo("Code-Review");
    assertThat(codeReview.getDefaultValue()).isEqualTo(0);
    assertThat(codeReview.getFunctionName()).isEqualTo("MaxWithBlock");
    assertThat(codeReview.isCopyMinScore()).isTrue();
    assertValueRange(codeReview, 2, 1, 0, -1, -2);
  }

  private void assertValueRange(LabelType label, Integer... range) {
    assertThat(label.getValuesAsList()).containsExactlyElementsIn(Arrays.asList(range)).inOrder();
    assertThat(label.getMax().getValue()).isEqualTo(range[0]);
    assertThat(label.getMin().getValue()).isEqualTo(range[range.length - 1]);
    for (LabelValue v : label.getValues()) {
      assertThat(Strings.isNullOrEmpty(v.getText())).isFalse();
    }
  }
}
