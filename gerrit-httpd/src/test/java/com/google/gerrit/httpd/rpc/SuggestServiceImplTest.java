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
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.impl.ListResultSet;
import com.google.inject.Provider;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    final List<ChangeLabel> all = new ArrayList<ChangeLabel>();
    // change 2 has "a" and "b" labels
    // change 3 has "a" and "c" labels
    // change 4 has "d" label
    all.add(new ChangeLabel(new Change.Id(2), "a"));
    all.add(new ChangeLabel(new Change.Id(3), "a"));
    all.add(new ChangeLabel(new Change.Id(2), "b"));
    all.add(new ChangeLabel(new Change.Id(3), "c"));
    all.add(new ChangeLabel(new Change.Id(4), "d"));

    // Defining the suggestion list expected to get
    final List<LabelKey> expected = new ArrayList<LabelKey>();
    expected.add(new LabelKey("a"));
    expected.add(new LabelKey("b"));
    expected.add(new LabelKey("c"));
    expected.add(new LabelKey("d"));

    doLabelsSuggestion(new Change.Id(1), all, expected);
  }

  @Test
  public void testSuggestLabelsToChangeWithLabels() throws Exception {
    // Defining the list with all ChangeLabel rows to be considered

    final List<ChangeLabel> all = new ArrayList<ChangeLabel>();
    // change 1 has "a" and "b" labels
    // change 2 has "b" label
    // change 3 has "c" label
    // change 4 has "d" label
    all.add(new ChangeLabel(new Change.Id(1), "a"));
    all.add(new ChangeLabel(new Change.Id(1), "b"));
    all.add(new ChangeLabel(new Change.Id(2), "b"));
    all.add(new ChangeLabel(new Change.Id(3), "c"));
    all.add(new ChangeLabel(new Change.Id(4), "d"));

    // Defining the suggestion list expected to get: it should not consider
    // labels exclusive of change 1 ("a" label should not be in this list)
    final List<LabelKey> expected = new ArrayList<LabelKey>();
    expected.add(new LabelKey("b"));
    expected.add(new LabelKey("c"));
    expected.add(new LabelKey("d"));

    doLabelsSuggestion(new Change.Id(1), all, expected);
  }

  private void doLabelsSuggestion(final Change.Id changeId,
      final List<ChangeLabel> all, final List<LabelKey> expected)
      throws Exception {
    expect(cfg.getEnum("suggest", null, "accounts", SuggestAccountsEnum.ALL))
        .andReturn(null);

    expect(schema.get()).andReturn(db);
    expect(db.changeLabels()).andReturn(changeLabels);

    expect(changeLabels.all()).andReturn(new ListResultSet<ChangeLabel>(all));

    doReplay();

    SuggestService suggestService =
        new SuggestServiceImpl(schema, null, null, null, null, null, null,
            null, cfg, null);

    // Execute suggestLabel to changeId
    suggestService.suggestLabel(changeId, new AsyncCallback<List<LabelKey>>() {

      @Override
      public void onFailure(Throwable caught) {
        fail(caught.getMessage());
      }

      @Override
      public void onSuccess(List<LabelKey> result) {
        doVerify();

        if (!result.equals(expected)) {
          fail("The LabelKey list returned is not the one expected.");
        }
      }

    });
  }

}
