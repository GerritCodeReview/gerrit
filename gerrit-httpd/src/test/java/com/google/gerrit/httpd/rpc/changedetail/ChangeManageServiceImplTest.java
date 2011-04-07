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

package com.google.gerrit.httpd.rpc.changedetail;

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeLabel;
import com.google.gerrit.reviewdb.ChangeLabelAccess;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.inject.Provider;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

public class ChangeManageServiceImplTest extends TestCase {
  private Provider<ReviewDb> schema;
  private ChangeControl.Factory changeControlFactory;
  private DeleteLabel.Factory deleteLabelFactory;
  private AddLabel.Factory addLabelFactory;
  private ChangeControl changeControl;
  private ReviewDb db;
  private ChangeLabelAccess changeLabels;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    schema = createStrictMock(Provider.class);
    addLabelFactory = createStrictMock(AddLabel.Factory.class);
    deleteLabelFactory = createStrictMock(DeleteLabel.Factory.class);
    changeControlFactory = createStrictMock(ChangeControl.Factory.class);
    changeControl = createStrictMock(ChangeControl.class);
    db = createStrictMock(ReviewDb.class);
    changeLabels = createStrictMock(ChangeLabelAccess.class);
  }

  private void doReplay() {
    replay(schema, addLabelFactory, deleteLabelFactory, changeControlFactory,
        changeControl, db, changeLabels);
  }

  private void doVerify() {
    verify(schema, addLabelFactory, deleteLabelFactory, changeControlFactory,
        changeControl, db, changeLabels);
  }

  @Test
  public void testDeleteLabel() throws Exception {
    final ChangeLabel changeLabelToDelete =
        new ChangeLabel(new Change.Id(0), "test-label");
    final DeleteLabel deleteLabel =
        new DeleteLabel(changeControlFactory, db, changeLabelToDelete);

    expect(deleteLabelFactory.create(changeLabelToDelete)).andReturn(
        deleteLabel);
    expect(changeControlFactory.controlFor(changeLabelToDelete.getChangeId()))
        .andReturn(changeControl);
    expect(changeControl.canEditChangeLabels()).andReturn(Boolean.TRUE);
    expect(db.changeLabels()).andReturn(changeLabels);
    changeLabels.delete(Collections.singletonList(changeLabelToDelete));

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null, null,
            deleteLabelFactory);

    changeManageServiceImpl.deleteLabel(changeLabelToDelete,
        getExpectsSuccessCallback());
  }

  @Test
  public void testDeleteLabelWithoutPermission() throws Exception {
    final ChangeLabel changeLabelToDelete =
        new ChangeLabel(new Change.Id(0), "test-label");
    final DeleteLabel deleteLabel =
      new DeleteLabel(changeControlFactory, db, changeLabelToDelete);

    expect(deleteLabelFactory.create(changeLabelToDelete)).andReturn(
        deleteLabel);
    expect(changeControlFactory.controlFor(changeLabelToDelete.getChangeId()))
        .andReturn(changeControl);
    expect(changeControl.canEditChangeLabels()).andReturn(Boolean.FALSE);

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null, null,
            deleteLabelFactory);
    changeManageServiceImpl.deleteLabel(changeLabelToDelete,
        getExpectsFailureCallback());
  }

  @Test
  public void testDeleteLabelChangeNotFoundFailure() throws Exception {
    final ChangeLabel changeLabelToDelete =
        new ChangeLabel(new Change.Id(0), "test-label");
    final DeleteLabel deleteLabel =
      new DeleteLabel(changeControlFactory, db, changeLabelToDelete);

    expect(deleteLabelFactory.create(changeLabelToDelete)).andReturn(
        deleteLabel);
    expect(changeControlFactory.controlFor(changeLabelToDelete.getChangeId()))
        .andThrow(new NoSuchChangeException(changeLabelToDelete.getChangeId()));

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null, null,
            deleteLabelFactory);
    changeManageServiceImpl.deleteLabel(changeLabelToDelete,
        getExpectsFailureCallback());
  }

  @Test
  public void testAddLabelChangeNotFoundFailure() throws Exception {
    final ChangeLabel changeLabel =
        new ChangeLabel(new Change.Id(0), "test-label");
    final AddLabel addLabel =
        new AddLabel(changeControlFactory, db, changeLabel);

    expect(addLabelFactory.create(changeLabel)).andReturn(addLabel);
    expect(changeControlFactory.controlFor(changeLabel.getChangeId()))
        .andThrow(new NoSuchChangeException(changeLabel.getChangeId()));

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null,
            addLabelFactory, null);
    changeManageServiceImpl.addLabel(changeLabel, getExpectsFailureCallback());
  }

  @Test
  public void testAddLabelWithoutPermission() throws Exception {
    final ChangeLabel changeLabel = new ChangeLabel(new Change.Id(0), "test");
    final AddLabel addLabel =
        new AddLabel(changeControlFactory, db, changeLabel);

    expect(addLabelFactory.create(changeLabel)).andReturn(addLabel);
    expect(changeControlFactory.controlFor(changeLabel.getChangeId()))
        .andReturn(changeControl);
    expect(changeControl.canEditChangeLabels()).andReturn(Boolean.FALSE);

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null,
            addLabelFactory, null);
    changeManageServiceImpl.addLabel(changeLabel, getExpectsFailureCallback());
  }

  @Test
  public void testAddLabel() throws Exception {
    doValidValueAddLabelTest("test");
  }

  @Test
  public void testAddLabelWithCompoundValue() throws Exception {
    doValidValueAddLabelTest("testValue");
  }

  @Test
  public void testAddLabelBeginCapitalLetter() throws Exception {
    doValidValueAddLabelTest("TestValue");
  }

  @Test
  public void testAddLabelOnlyNumbers() throws Exception {
    doValidValueAddLabelTest("0123456789");
  }

  @Test
  public void testAddLabelLettersAndNumbers() throws Exception {
    doValidValueAddLabelTest("a01b");
  }

  @Test
  public void testAddLabelCapitalLetters() throws Exception {
    doValidValueAddLabelTest("ABC");
  }

  @Test
  public void testAddLabelCompoundByHyphen() throws Exception {
    doValidValueAddLabelTest("A-Special-label");
  }

  @Test
  public void testAddLabelStartingWithHyphen() throws Exception {
    doValidValueAddLabelTest("-label");
  }

  @Test
  public void testAddLabelEndingWithHyphen() throws Exception {
    doValidValueAddLabelTest("label-");
  }

  @Test
  public void testAddLabelEndingWithDoubleHyphen() throws Exception {
    doInvalidValueAddLabelTest("label--");
  }

  @Test
  public void testAddLabelStartingWithDoubleHyphen() throws Exception {
    doInvalidValueAddLabelTest("--label");
  }

  @Test
  public void testAddLabelCompoundByDoubleHyphen() throws Exception {
    doInvalidValueAddLabelTest("Special--label");
  }

  @Test
  public void testAddLabelCompoundByLotsOfHyphen() throws Exception {
    doInvalidValueAddLabelTest("A---Special-----label");
  }

  @Test
  public void testAddLabelWithWhitespaces() throws Exception {
    doInvalidValueAddLabelTest("An invalid label");
  }

  @Test
  public void testAddLabelCompoundByDot() throws Exception {
    doInvalidValueAddLabelTest("An.invalid.label");
  }

  @Test
  public void testAddLabelWithInvalidCharacters() throws Exception {
    doInvalidValueAddLabelTest("#,;?&%+=/\\|~`'\"");
  }

  @Test
  public void testAddLabelWithQuotes() throws Exception {
    doInvalidValueAddLabelTest("\"myLabel\"");
  }

  private void doValidValueAddLabelTest(String labelValue) throws Exception {
    final ChangeLabel changeLabel =
        new ChangeLabel(new Change.Id(0), labelValue);
    final AddLabel addLabel =
        new AddLabel(changeControlFactory, db, changeLabel);

    expect(addLabelFactory.create(changeLabel)).andReturn(addLabel);
    expect(changeControlFactory.controlFor(changeLabel.getChangeId()))
        .andReturn(changeControl);
    expect(changeControl.canEditChangeLabels()).andReturn(Boolean.TRUE);
    expect(db.changeLabels()).andReturn(changeLabels);
    changeLabels.insert(Collections.singletonList(changeLabel));

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null,
            addLabelFactory, null);
    changeManageServiceImpl.addLabel(changeLabel, getExpectsSuccessCallback());
  }

  private void doInvalidValueAddLabelTest(String labelValue) throws Exception {
    final ChangeLabel changeLabel =
        new ChangeLabel(new Change.Id(0), labelValue);
    final AddLabel addLabel =
      new AddLabel(changeControlFactory, db, changeLabel);

    expect(addLabelFactory.create(changeLabel)).andReturn(addLabel);
    expect(changeControlFactory.controlFor(changeLabel.getChangeId()))
        .andReturn(changeControl);
    expect(changeControl.canEditChangeLabels()).andReturn(Boolean.TRUE);

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null,
            addLabelFactory, null);
    changeManageServiceImpl.addLabel(changeLabel, getExpectsFailureCallback());
  }

  private AsyncCallback<VoidResult> getExpectsFailureCallback() {
    return new AsyncCallback<VoidResult>() {

      @Override
      public void onFailure(Throwable caught) {
        doVerify();
      }

      @Override
      public void onSuccess(VoidResult result) {
        fail("Label operation failure expected.");
      }
    };
  }

  private AsyncCallback<VoidResult> getExpectsSuccessCallback() {
    return new AsyncCallback<VoidResult>() {

      @Override
      public void onFailure(Throwable caught) {
        fail("No failure expected.");
      }

      @Override
      public void onSuccess(VoidResult result) {
        doVerify();
      }
    };
  }
}
