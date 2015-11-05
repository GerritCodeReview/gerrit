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

import static com.google.gerrit.common.data.Permission.LABEL;
import static com.google.gerrit.server.project.Util.allow;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.value;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.Util;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.inject.AbstractModule;

import com.googlecode.prolog_cafe.exceptions.CompileException;
import com.googlecode.prolog_cafe.exceptions.ReductionLimitException;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;

import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

import java.io.PushbackReader;
import java.io.StringReader;
import java.util.Arrays;

public class GerritCommonTest extends PrologTestCase {
  private final LabelType V = category("Verified",
      value(1, "Verified"),
      value(0, "No score"),
      value(-1, "Fails"));
  private final LabelType Q = category("Qualified",
      value(1, "Qualified"),
      value(0, "No score"),
      value(-1, "Fails"));

  private final Project.NameKey localKey = new Project.NameKey("local");
  private ProjectConfig local;
  private Util util;

  @Before
  public void setUp() throws Exception {
    util = new Util();
    load("gerrit", "gerrit_common_test.pl", new AbstractModule() {
      @Override
      protected void configure() {
        Config cfg = new Config();
        cfg.setInt("rules", null, "reductionLimit", 1300);
        cfg.setInt("rules", null, "compileReductionLimit", (int) 1e6);
        bind(PrologEnvironment.Args.class).toInstance(
            new PrologEnvironment.Args(
                null,
                null,
                null,
                null,
                null,
                null,
                cfg));
      }
    });

    local = new ProjectConfig(localKey);
    local.load(InMemoryRepositoryManager.newRepository(localKey));
    Q.setRefPatterns(Arrays.asList("refs/heads/develop"));

    local.getLabelSections().put(V.getName(), V);
    local.getLabelSections().put(Q.getName(), Q);
    util.add(local);
    allow(local, LABEL + V.getName(), -1, +1, SystemGroupBackend.REGISTERED_USERS, "refs/heads/*");
    allow(local, LABEL + Q.getName(), -1, +1, SystemGroupBackend.REGISTERED_USERS, "refs/heads/master");
  }

  @Override
  protected void setUpEnvironment(PrologEnvironment env) {
    Change change =
        new Change(new Change.Key("Ibeef"), new Change.Id(1),
            new Account.Id(2),
            new Branch.NameKey(localKey, "refs/heads/master"),
            TimeUtil.nowTs());
    env.set(StoredValues.CHANGE_CONTROL, util.user(local).controlFor(change));
  }

  @Test
  public void testGerritCommon() {
    runPrologBasedTests();
  }

  @Test
  public void testReductionLimit() throws CompileException {
    PrologEnvironment env = envFactory.create(machine);
    setUpEnvironment(env);

    String script = "loopy :- b(5).\n"
        + "b(N) :- N > 0, !, S = N - 1, b(S).\n"
        + "b(_) :- true.\n";

    SymbolTerm nameTerm = SymbolTerm.create("testReductionLimit");
    JavaObjectTerm inTerm = new JavaObjectTerm(
        new PushbackReader(new StringReader(script), Prolog.PUSHBACK_SIZE));
    if (!env.execute(Prolog.BUILTIN, "consult_stream", nameTerm, inTerm)) {
      throw new CompileException("Cannot consult " + nameTerm);
    }

    exception.expect(ReductionLimitException.class);
    exception.expectMessage("exceeded reduction limit of 1300");
    env.once(Prolog.BUILTIN, "call", new StructureTerm(":",
        SymbolTerm.create("user"), SymbolTerm.create("loopy")));
  }
}
