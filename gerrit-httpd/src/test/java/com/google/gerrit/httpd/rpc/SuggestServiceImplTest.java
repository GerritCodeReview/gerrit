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

package com.google.gerrit.httpd.rpc;

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.common.data.SuggestService;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeLabel;
import com.google.gerrit.reviewdb.ChangeLabelAccess;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.ChangeLabel.LabelKey;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.impl.ListResultSet;
import com.google.inject.Provider;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SuggestServiceImplTest extends TestCase {
  private Config cfg;
  private Provider<ReviewDb> schema;
  private ReviewDb db;
  private ChangeLabelAccess changeLabels;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    cfg = createStrictMock(Config.class);
    schema = createStrictMock(Provider.class);
    db = createStrictMock(ReviewDb.class);
    changeLabels = createStrictMock(ChangeLabelAccess.class);
  }

  private void doReplay() {
    replay(schema, db, changeLabels);
  }

  private void doVerify() {
    verify(schema, db, changeLabels);
  }

  @Test
  public void testSuggestLabelsToChangeWithoutThem() throws Exception {
    // Defining the list with all ChangeLabel rows to be considered
    final List<ChangeLabel> allExcludingOneChange =
        new ArrayList<ChangeLabel>();
    allExcludingOneChange.add(new ChangeLabel(new Change.Id(2), "a"));
    allExcludingOneChange.add(new ChangeLabel(new Change.Id(3), "a"));
    allExcludingOneChange.add(new ChangeLabel(new Change.Id(2), "b"));
    allExcludingOneChange.add(new ChangeLabel(new Change.Id(3), "c"));
    allExcludingOneChange.add(new ChangeLabel(new Change.Id(4), "d"));

    // Defining the suggestion set expected to get
    final Set<LabelKey> expected = new HashSet<LabelKey>();
    expected.add(new LabelKey("a"));
    expected.add(new LabelKey("b"));
    expected.add(new LabelKey("c"));
    expected.add(new LabelKey("d"));

    doLabelsSuggestion(new Change.Id(1), allExcludingOneChange, expected);
  }

  @Test
  public void testSuggestLabelsToChangeWithLabels() throws Exception {
    // Defining the list with all ChangeLabel rows to be considered
    final List<ChangeLabel> allExcludingOneChange =
        new ArrayList<ChangeLabel>();
    allExcludingOneChange.add(new ChangeLabel(new Change.Id(2), "b"));
    allExcludingOneChange.add(new ChangeLabel(new Change.Id(3), "c"));
    allExcludingOneChange.add(new ChangeLabel(new Change.Id(4), "d"));

    // Defining the suggestion list expected to get
    final Set<LabelKey> expected = new HashSet<LabelKey>();
    expected.add(new LabelKey("b"));
    expected.add(new LabelKey("c"));
    expected.add(new LabelKey("d"));

    doLabelsSuggestion(new Change.Id(1), allExcludingOneChange, expected);
  }

  private void doLabelsSuggestion(final Change.Id changeId,
      final List<ChangeLabel> all, final Set<LabelKey> expected)
      throws Exception {
    expect(cfg.getEnum("suggest", null, "accounts", SuggestAccountsEnum.ALL))
        .andReturn(null);

    expect(schema.get()).andReturn(db);
    expect(db.changeLabels()).andReturn(changeLabels);

    expect(changeLabels.allExcludingOneChange(changeId)).andReturn(
        new ListResultSet<ChangeLabel>(all));

    doReplay();

    SuggestService suggestService =
        new SuggestServiceImpl(schema, null, null, null, null, null, null,
            null, cfg, null);

    // Execute suggestLabel to changeId
    suggestService.suggestLabel(changeId, new AsyncCallback<Set<LabelKey>>() {
      @Override
      public void onFailure(Throwable caught) {
        fail(caught.getMessage());
      }

      @Override
      public void onSuccess(Set<LabelKey> result) {
        doVerify();

        if (!result.equals(expected)) {
          fail("The LabelKey set returned is not the one expected.");
        }
      }
    });
  }
}
