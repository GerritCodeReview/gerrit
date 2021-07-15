// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.index;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.lucene.LuceneChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import javax.inject.Inject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Test to check that the expected index backend was bound depending on sys/env properties. */
public class LuceneIndexBindingIT extends AbstractDaemonTest {

  @Inject private ChangeIndexCollection changeIndex;

  private static String propertyBeforeTest;

  @BeforeClass
  public static void setup() {
    propertyBeforeTest = System.getProperty(IndexType.SYS_PROP);
    System.setProperty(IndexType.SYS_PROP, "lucene");
  }

  @AfterClass
  public static void teardown() {
    System.setProperty(IndexType.SYS_PROP, propertyBeforeTest);
  }

  @Test
  public void luceneIsBoundWhenConfigured() throws Exception {
    assertThat(System.getProperty(IndexType.SYS_PROP)).isEqualTo("lucene");
    assertThat(changeIndex.getSearchIndex()).isInstanceOf(LuceneChangeIndex.class);
  }
}
