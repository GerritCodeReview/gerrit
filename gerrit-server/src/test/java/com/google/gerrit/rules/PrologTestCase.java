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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.testutil.GerritBaseTests;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.googlecode.prolog_cafe.exceptions.CompileException;
import com.googlecode.prolog_cafe.lang.BufferingPrologControl;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologClassLoader;
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Base class for any tests written in Prolog. */
public abstract class PrologTestCase extends GerritBaseTests {
  private static final SymbolTerm test_1 = SymbolTerm.intern("test", 1);

  private String pkg;
  private boolean hasSetup;
  private boolean hasTeardown;
  private List<Term> tests;
  protected PrologMachineCopy machine;
  protected PrologEnvironment.Factory envFactory;

  protected void load(String pkg, String prologResource, Module... modules)
      throws CompileException, IOException {
    ArrayList<Module> moduleList = new ArrayList<>();
    moduleList.add(new PrologModule.EnvironmentModule());
    moduleList.addAll(Arrays.asList(modules));

    envFactory = Guice.createInjector(moduleList).getInstance(PrologEnvironment.Factory.class);
    PrologEnvironment env = envFactory.create(newMachine());
    consult(env, getClass(), prologResource);

    this.pkg = pkg;
    hasSetup = has(env, "setup");
    hasTeardown = has(env, "teardown");

    StructureTerm head =
        new StructureTerm(
            ":", SymbolTerm.intern(pkg), new StructureTerm(test_1, new VariableTerm()));

    tests = new ArrayList<>();
    for (Term[] pair : env.all(Prolog.BUILTIN, "clause", head, new VariableTerm())) {
      tests.add(pair[0]);
    }
    assertThat(tests).isNotEmpty();
    machine = PrologMachineCopy.save(env);
  }

  /**
   * Set up the Prolog environment.
   *
   * @param env Prolog environment.
   */
  protected void setUpEnvironment(PrologEnvironment env) {}

  private PrologMachineCopy newMachine() {
    BufferingPrologControl ctl = new BufferingPrologControl();
    ctl.setMaxDatabaseSize(16 * 1024);
    ctl.setPrologClassLoader(new PrologClassLoader(getClass().getClassLoader()));
    return PrologMachineCopy.save(ctl);
  }

  protected void consult(BufferingPrologControl env, Class<?> clazz, String prologResource)
      throws CompileException, IOException {
    try (InputStream in = clazz.getResourceAsStream(prologResource)) {
      if (in == null) {
        throw new FileNotFoundException(prologResource);
      }
      SymbolTerm pathTerm = SymbolTerm.create(prologResource);
      JavaObjectTerm inTerm =
          new JavaObjectTerm(
              new PushbackReader(
                  new BufferedReader(new InputStreamReader(in, UTF_8)), Prolog.PUSHBACK_SIZE));
      if (!env.execute(Prolog.BUILTIN, "consult_stream", pathTerm, inTerm)) {
        throw new CompileException("Cannot consult " + prologResource);
      }
    }
  }

  private boolean has(BufferingPrologControl env, String name) {
    StructureTerm head = SymbolTerm.create(pkg, name, 0);
    return env.execute(Prolog.BUILTIN, "clause", head, new VariableTerm());
  }

  public void runPrologBasedTests() {
    int errors = 0;
    long start = TimeUtil.nowMs();

    for (Term test : tests) {
      PrologEnvironment env = envFactory.create(machine);
      setUpEnvironment(env);
      env.setEnabled(Prolog.Feature.IO, true);

      System.out.format("Prolog %-60s ...", removePackage(test));
      System.out.flush();

      if (hasSetup) {
        call(env, "setup");
      }

      List<Term> all = env.all(Prolog.BUILTIN, "call", test);

      if (hasTeardown) {
        call(env, "teardown");
      }

      System.out.println(all.size() == 1 ? "OK" : "FAIL");

      if (all.size() > 0 && !test.equals(all.get(0))) {
        for (Term t : all) {
          Term head = ((StructureTerm) removePackage(t)).args()[0];
          Term[] args = ((StructureTerm) head).args();
          System.out.print("  Result: ");
          for (int i = 0; i < args.length; i++) {
            if (0 < i) {
              System.out.print(", ");
            }
            System.out.print(args[i]);
          }
          System.out.println();
        }
        System.out.println();
      }

      if (all.size() != 1) {
        errors++;
      }
    }

    long end = TimeUtil.nowMs();
    System.out.println("-------------------------------");
    System.out.format(
        "Prolog tests: %d, Failures: %d, Time elapsed %.3f sec",
        tests.size(), errors, (end - start) / 1000.0);
    System.out.println();

    assertThat(errors).isEqualTo(0);
  }

  private void call(BufferingPrologControl env, String name) {
    StructureTerm head = SymbolTerm.create(pkg, name, 0);
    assert_()
        .withFailureMessage("Cannot invoke " + pkg + ":" + name)
        .that(env.execute(Prolog.BUILTIN, "call", head))
        .isTrue();
  }

  private Term removePackage(Term test) {
    Term name = test;
    if (name instanceof StructureTerm && ":".equals(name.name())) {
      name = name.arg(1);
    }
    return name;
  }
}
