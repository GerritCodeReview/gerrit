// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.rules;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;

import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

public class PrologEnvironmentTest extends TestCase {
  private static final String pkg = "com.google.gerrit.rules.common";

  private PrologEnvironment.Factory envFactory;
  private ApprovalTypes types;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    List<ApprovalType> typeList = new ArrayList<ApprovalType>();
    typeList.add(codeReviewCategory());
    typeList.add(verifiedCategory());
    types = new ApprovalTypes(typeList);

    envFactory = Guice.createInjector(new PrologModule(), new AbstractModule() {
      @Override
      protected void configure() {
        bind(ApprovalTypes.class).toInstance(types);
      }
    }).getInstance(PrologEnvironment.Factory.class);
  }

  public void test_get_legacy_approval_types() {
    PrologEnvironment env = newEnv();
    Term res = env.once(pkg, "get_legacy_approval_types", new VariableTerm());
    assertTrue(res.isList());

    @SuppressWarnings("unchecked")
    List<Term> list = (List<Term>) ((ListTerm) res).toJava();
    assertEquals(2, list.size());

    StructureTerm a = (StructureTerm) list.get(0);
    StructureTerm b = (StructureTerm) list.get(1);

    assertEquals("approval_type", a.name());
    assertEquals("Code-Review", ((SymbolTerm) a.args()[0]).name());

    assertEquals("approval_type", b.name());
    assertEquals("Verified", ((SymbolTerm) b.args()[0]).name());
  }

  public PrologEnvironment newEnv() {
    return envFactory.create(getClass().getClassLoader());
  }

  private static ApprovalType codeReviewCategory() {
    ApprovalCategory cat;
    ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("CRVW"), "Code Review");
    cat.setPosition((short) 0);
    cat.setAbbreviatedName("R");
    cat.setCopyMinScore(true);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 2, "Looks good to me, approved"));
    vals.add(value(cat, 1, "Looks good to me, but someone else must approve"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "I would prefer that you didn't submit this"));
    vals.add(value(cat, -2, "Do not submit"));
    return new ApprovalType(cat, vals);
  }

  private static ApprovalType verifiedCategory() {
    ApprovalCategory cat;
    ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("VRIF"), "Verified");
    cat.setPosition((short) 1);
    cat.setAbbreviatedName("V");
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Verified"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "Fails"));
    return new ApprovalType(cat, vals);
  }

  private static ApprovalCategoryValue value(final ApprovalCategory cat,
      final int value, final String name) {
    return new ApprovalCategoryValue(new ApprovalCategoryValue.Id(cat.getId(),
        (short) value), name);
  }
}
