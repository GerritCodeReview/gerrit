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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.workflow.NoOpFunction;
import com.google.gerrit.server.workflow.SubmitFunction;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;

import junit.framework.TestCase;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;

public class SchemaCreatorTest extends TestCase {
  private ApprovalCategory.Id codeReview = new ApprovalCategory.Id("CRVW");
  private InMemoryDatabase db;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    db = new InMemoryDatabase();
  }

  @Override
  protected void tearDown() throws Exception {
    InMemoryDatabase.drop(db);
    super.tearDown();
  }

  public void testGetCauses_CreateSchema() throws OrmException, SQLException {
    // Initially the schema should be empty.
    //
    {
      final JdbcSchema d = (JdbcSchema) db.open();
      try {
        final String[] types = {"TABLE", "VIEW"};
        final ResultSet rs =
            d.getConnection().getMetaData().getTables(null, null, null, types);
        try {
          assertFalse(rs.next());
        } finally {
          rs.close();
        }
      } finally {
        d.close();
      }
    }

    // Create the schema using the current schema version.
    //
    db.create();
    db.assertSchemaVersion();
    final SystemConfig config = db.getSystemConfig();
    assertNotNull(config);
    assertNotNull(config.adminGroupId);
    assertNotNull(config.anonymousGroupId);
    assertNotNull(config.registeredGroupId);

    // By default sitePath is set to the current working directory.
    //
    File sitePath = new File(".").getAbsoluteFile();
    if (sitePath.getName().equals(".")) {
      sitePath = sitePath.getParentFile();
    }
    assertEquals(sitePath.getAbsolutePath(), config.sitePath);

    // This is randomly generated and should be at least 20 bytes long.
    //
    assertNotNull(config.registerEmailPrivateKey);
    assertTrue(20 < config.registerEmailPrivateKey.length());
  }

  public void testSubsequentGetReads() throws OrmException {
    db.create();
    final SystemConfig exp = db.getSystemConfig();
    final SystemConfig act = db.getSystemConfig();

    assertNotSame(exp, act);
    assertEquals(exp.adminGroupId, act.adminGroupId);
    assertEquals(exp.anonymousGroupId, act.anonymousGroupId);
    assertEquals(exp.registeredGroupId, act.registeredGroupId);
    assertEquals(exp.sitePath, act.sitePath);
    assertEquals(exp.registerEmailPrivateKey, act.registerEmailPrivateKey);
  }

  public void testCreateSchema_Group_Administrators() throws OrmException {
    db.create();
    final SystemConfig config = db.getSystemConfig();
    final ReviewDb c = db.open();
    try {
      final AccountGroup admin = c.accountGroups().get(config.adminGroupId);
      assertNotNull(admin);
      assertEquals(config.adminGroupId, admin.getId());
      assertEquals("Administrators", admin.getName());
      assertSame(AccountGroup.Type.INTERNAL, admin.getType());
    } finally {
      c.close();
    }
  }

  public void testCreateSchema_Group_AnonymousUsers() throws OrmException {
    db.create();
    final SystemConfig config = db.getSystemConfig();
    final ReviewDb c = db.open();
    try {
      final AccountGroup anon = c.accountGroups().get(config.anonymousGroupId);
      assertNotNull(anon);
      assertEquals(config.anonymousGroupId, anon.getId());
      assertEquals("Anonymous Users", anon.getName());
      assertSame(AccountGroup.Type.SYSTEM, anon.getType());
    } finally {
      c.close();
    }
  }

  public void testCreateSchema_Group_RegisteredUsers() throws OrmException {
    db.create();
    final SystemConfig config = db.getSystemConfig();
    final ReviewDb c = db.open();
    try {
      final AccountGroup reg = c.accountGroups().get(config.registeredGroupId);
      assertNotNull(reg);
      assertEquals(config.registeredGroupId, reg.getId());
      assertEquals("Registered Users", reg.getName());
      assertSame(AccountGroup.Type.SYSTEM, reg.getType());
    } finally {
      c.close();
    }
  }

  public void testCreateSchema_WildCardProject() throws OrmException {
    final ReviewDb c = db.create().open();
    try {
      final SystemConfig cfg;
      final Project all;

      cfg = c.systemConfig().get(new SystemConfig.Key());
      all = c.projects().get(cfg.wildProjectName);
      assertNotNull(all);
      assertEquals("-- All Projects --", all.getName());
      assertFalse(all.isUseContributorAgreements());
      assertFalse(all.isUseSignedOffBy());
      assertFalse(all.isRequireChangeID());
    } finally {
      c.close();
    }
  }

  public void testCreateSchema_ApprovalCategory_CodeReview()
      throws OrmException {
    final ReviewDb c = db.create().open();
    try {
      final ApprovalCategory cat;

      cat = c.approvalCategories().get(codeReview);
      assertNotNull(cat);
      assertEquals(codeReview, cat.getId());
      assertEquals("Code Review", cat.getName());
      assertEquals("R", cat.getAbbreviatedName());
      assertEquals("MaxWithBlock", cat.getFunctionName());
      assertTrue(cat.isCopyMinScore());
      assertFalse(cat.isAction());
      assertTrue(0 <= cat.getPosition());
    } finally {
      c.close();
    }
    assertValueRange(codeReview, -2, -1, 0, 1, 2);
  }

  public void testCreateSchema_ApprovalCategory_Read() throws OrmException {
    final ReviewDb c = db.create().open();
    try {
      final ApprovalCategory cat;

      cat = c.approvalCategories().get(ApprovalCategory.READ);
      assertNotNull(cat);
      assertEquals(ApprovalCategory.READ, cat.getId());
      assertEquals("Read Access", cat.getName());
      assertNull(cat.getAbbreviatedName());
      assertEquals(NoOpFunction.NAME, cat.getFunctionName());
      assertTrue(cat.isAction());
    } finally {
      c.close();
    }
    assertValueRange(ApprovalCategory.READ, -1, 1, 2, 3);
  }

  public void testCreateSchema_ApprovalCategory_Submit() throws OrmException {
    final ReviewDb c = db.create().open();
    try {
      final ApprovalCategory cat;

      cat = c.approvalCategories().get(ApprovalCategory.SUBMIT);
      assertNotNull(cat);
      assertEquals(ApprovalCategory.SUBMIT, cat.getId());
      assertEquals("Submit", cat.getName());
      assertNull(cat.getAbbreviatedName());
      assertEquals(SubmitFunction.NAME, cat.getFunctionName());
      assertTrue(cat.isAction());
    } finally {
      c.close();
    }
    assertValueRange(ApprovalCategory.SUBMIT, 1);
  }

  public void testCreateSchema_ApprovalCategory_PushTag() throws OrmException {
    final ReviewDb c = db.create().open();
    try {
      final ApprovalCategory cat;

      cat = c.approvalCategories().get(ApprovalCategory.PUSH_TAG);
      assertNotNull(cat);
      assertEquals(ApprovalCategory.PUSH_TAG, cat.getId());
      assertEquals("Push Tag", cat.getName());
      assertNull(cat.getAbbreviatedName());
      assertEquals(NoOpFunction.NAME, cat.getFunctionName());
      assertTrue(cat.isAction());
    } finally {
      c.close();
    }
    assertValueRange(ApprovalCategory.PUSH_TAG, //
        ApprovalCategory.PUSH_TAG_SIGNED, //
        ApprovalCategory.PUSH_TAG_ANNOTATED);
  }

  public void testCreateSchema_ApprovalCategory_PushHead() throws OrmException {
    final ReviewDb c = db.create().open();
    try {
      final ApprovalCategory cat;

      cat = c.approvalCategories().get(ApprovalCategory.PUSH_HEAD);
      assertNotNull(cat);
      assertEquals(ApprovalCategory.PUSH_HEAD, cat.getId());
      assertEquals("Push Branch", cat.getName());
      assertNull(cat.getAbbreviatedName());
      assertEquals(NoOpFunction.NAME, cat.getFunctionName());
      assertTrue(cat.isAction());
    } finally {
      c.close();
    }
    assertValueRange(ApprovalCategory.PUSH_HEAD, //
        ApprovalCategory.PUSH_HEAD_UPDATE, //
        ApprovalCategory.PUSH_HEAD_CREATE, //
        ApprovalCategory.PUSH_HEAD_REPLACE);
  }

  public void testCreateSchema_ApprovalCategory_Owner() throws OrmException {
    final ReviewDb c = db.create().open();
    try {
      final ApprovalCategory cat;

      cat = c.approvalCategories().get(ApprovalCategory.OWN);
      assertNotNull(cat);
      assertEquals(ApprovalCategory.OWN, cat.getId());
      assertEquals("Owner", cat.getName());
      assertNull(cat.getAbbreviatedName());
      assertEquals(NoOpFunction.NAME, cat.getFunctionName());
      assertTrue(cat.isAction());
    } finally {
      c.close();
    }
    assertValueRange(ApprovalCategory.OWN, 1);
  }

  private void assertValueRange(ApprovalCategory.Id cat, int... range)
      throws OrmException {
    final HashSet<ApprovalCategoryValue.Id> act =
        new HashSet<ApprovalCategoryValue.Id>();
    final ReviewDb c = db.open();
    try {
      for (ApprovalCategoryValue v : c.approvalCategoryValues().byCategory(cat)) {
        assertNotNull(v.getId());
        assertNotNull(v.getName());
        assertEquals(cat, v.getCategoryId());
        assertFalse(v.getName().isEmpty());

        act.add(v.getId());
      }
    } finally {
      c.close();
    }

    for (int value : range) {
      final ApprovalCategoryValue.Id exp =
          new ApprovalCategoryValue.Id(cat, (short) value);
      if (!act.remove(exp)) {
        fail("Category " + cat + " lacks value " + value);
      }
    }
    if (!act.isEmpty()) {
      fail("Category " + cat + " has additional values: " + act);
    }
  }

  public void testCreateSchema_DefaultAccess_AnonymousUsers()
      throws OrmException {
    db.create();
    final SystemConfig config = db.getSystemConfig();
    assertDefaultRight("refs/*", config.anonymousGroupId,
        ApprovalCategory.READ, 1, 1);
  }

  public void testCreateSchema_DefaultAccess_RegisteredUsers()
      throws OrmException {
    db.create();
    final SystemConfig config = db.getSystemConfig();
    assertDefaultRight("refs/*", config.registeredGroupId,
        ApprovalCategory.READ, 1, 2);
    assertDefaultRight("refs/heads/*", config.registeredGroupId, codeReview,
        -1, 1);
  }

  public void testCreateSchema_DefaultAccess_Administrators()
      throws OrmException {
    db.create();
    final SystemConfig config = db.getSystemConfig();
    assertDefaultRight("refs/*", config.adminGroupId, ApprovalCategory.READ, 1,
        1);
  }

  private void assertDefaultRight(final String pattern,
      final AccountGroup.Id group, final ApprovalCategory.Id category, int min,
      int max) throws OrmException {
    final ReviewDb c = db.open();
    try {
      final SystemConfig cfg;
      final Project all;
      final RefRight right;

      cfg = c.systemConfig().get(new SystemConfig.Key());
      all = c.projects().get(cfg.wildProjectName);
      right =
          c.refRights().get(
              new RefRight.Key(all.getNameKey(), new RefRight.RefPattern(
                  pattern), category, group));

      assertNotNull(right);
      assertEquals(all.getNameKey(), right.getProjectNameKey());
      assertEquals(group, right.getAccountGroupId());
      assertEquals(category, right.getApprovalCategoryId());
      assertEquals(min, right.getMinValue());
      assertEquals(max, right.getMaxValue());
    } finally {
      c.close();
    }
  }
}
