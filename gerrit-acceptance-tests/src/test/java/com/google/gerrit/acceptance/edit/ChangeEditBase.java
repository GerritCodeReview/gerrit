// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.edit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeUtils.MillisProvider;
import org.junit.After;
import org.junit.Before;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicLong;

public class ChangeEditBase extends AbstractDaemonTest {

  protected final static String FILE_NAME = "foo";
  protected final static String FILE_NAME2 = "foo2";
  protected final static byte[] CONTENT_OLD = "bar".getBytes(UTF_8);
  protected final static byte[] CONTENT_NEW = "baz".getBytes(UTF_8);
  protected final static byte[] CONTENT_NEW2 = "qux".getBytes(UTF_8);

  @Inject
  protected PushOneCommit.Factory pushFactory;

  @Inject
  protected ChangeEditUtil editUtil;

  @Inject
  protected ChangeEditModifier modifier;

  @Inject
  protected FileContentUtil fileUtil;

  protected Change change;
  protected String changeId;
  protected Change change2;
  protected PatchSet ps;
  protected PatchSet ps2;

  @Before
  public void setUp() throws Exception {
    reset();
    final long clockStepMs = MILLISECONDS.convert(1, SECONDS);
    final AtomicLong clockMs = new AtomicLong(
        new DateTime(2009, 9, 30, 17, 0, 0).getMillis());
    DateTimeUtils.setCurrentMillisProvider(new MillisProvider() {
      @Override
      public long getMillis() {
        return clockMs.getAndAdd(clockStepMs);
      }
    });
  }

  @Override
  protected void init() throws Exception {
    super.init();
    changeId = newChange(git, admin.getIdent());
    ps = getCurrentPatchSet(changeId);
    amendChange(git, admin.getIdent(), changeId);
    change = getChange(changeId);
    assertNotNull(ps);
    String changeId2 = newChange2(git, admin.getIdent());
    change2 = getChange(changeId2);
    assertNotNull(change2);
    ps2 = getCurrentPatchSet(changeId2);
    assertNotNull(ps2);
  }

  @After
  public void cleanup() {
    DateTimeUtils.setCurrentMillisSystem();
  }

  protected String newChange(Git git, PersonIdent ident) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, ident, PushOneCommit.SUBJECT, FILE_NAME,
            new String(CONTENT_OLD));
    return push.to(git, "refs/for/master").getChangeId();
  }

  protected String amendChange(Git git, PersonIdent ident, String changeId) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, ident, PushOneCommit.SUBJECT, FILE_NAME2,
            new String(CONTENT_NEW2), changeId);
    return push.to(git, "refs/for/master").getChangeId();
  }

  protected String newChange2(Git git, PersonIdent ident) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, ident, PushOneCommit.SUBJECT, FILE_NAME,
            new String(CONTENT_OLD));
    return push.rm(git, "refs/for/master").getChangeId();
  }

  protected Change getChange(String changeId) throws Exception {
    return Iterables.getOnlyElement(db.changes()
        .byKey(new Change.Key(changeId)));
  }

  protected PatchSet getCurrentPatchSet(String changeId) throws Exception {
    return db.patchSets()
        .get(getChange(changeId).currentPatchSetId());
  }

  protected static byte[] toBytes(BinaryResult content) throws Exception {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    content.writeTo(os);
    return os.toByteArray();
  }
}
