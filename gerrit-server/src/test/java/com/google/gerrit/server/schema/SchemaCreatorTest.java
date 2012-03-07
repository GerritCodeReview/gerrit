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

import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
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

  public void testGetCauses_CreateSchema() throws OrmException, SQLException,
      IOException {
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

    // By default sitePath is set to the current working directory.
    //
    File sitePath = new File(".").getAbsoluteFile();
    if (sitePath.getName().equals(".")) {
      sitePath = sitePath.getParentFile();
    }
    assertEquals(sitePath.getCanonicalPath(), db.getSystemConfig().sitePath);
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
      assertTrue(0 <= cat.getPosition());
    } finally {
      c.close();
    }
    assertValueRange(codeReview, -2, -1, 0, 1, 2);
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
}
