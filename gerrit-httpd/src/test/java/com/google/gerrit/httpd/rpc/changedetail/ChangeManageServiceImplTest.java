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
  private ChangeControl changeControl;
  private ReviewDb db;
  private ChangeLabelAccess changeLabels;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    schema = createStrictMock(Provider.class);
    changeControlFactory = createStrictMock(ChangeControl.Factory.class);
    changeControl = createStrictMock(ChangeControl.class);
    db = createStrictMock(ReviewDb.class);
    changeLabels = createStrictMock(ChangeLabelAccess.class);
  }

  private void doReplay() {
    replay(schema, changeControlFactory, changeControl, db, changeLabels);
  }

  private void doVerify() {
    verify(schema, changeControlFactory, changeControl, db, changeLabels);
  }

  @Test
  public void testDeleteLabel() throws Exception {
    final ChangeLabel changeLabelToDelete =
        new ChangeLabel(new Change.Id(0), "test-label");

    expect(schema.get()).andReturn(db);

    expect(changeControlFactory.controlFor(changeLabelToDelete.getChangeId()))
        .andReturn(changeControl);
    expect(changeControl.canEditArbitraryLabels()).andReturn(Boolean.TRUE);
    expect(db.changeLabels()).andReturn(changeLabels);
    changeLabels.delete(Collections.singletonList(changeLabelToDelete));

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null,
            changeControlFactory);
    changeManageServiceImpl.deleteLabel(changeLabelToDelete,
        new AsyncCallback<VoidResult>() {

          @Override
          public void onFailure(Throwable caught) {
            fail("No failure expected.");
          }

          @Override
          public void onSuccess(VoidResult result) {
            doVerify();
          }
        });
  }

  @Test
  public void testDeleteLabelWithoutPermission() throws Exception {
    final ChangeLabel changeLabelToDelete =
        new ChangeLabel(new Change.Id(0), "test-label");

    expect(schema.get()).andReturn(db);

    expect(changeControlFactory.controlFor(changeLabelToDelete.getChangeId()))
        .andReturn(changeControl);
    expect(changeControl.canEditArbitraryLabels()).andReturn(Boolean.FALSE);

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null,
            changeControlFactory);
    changeManageServiceImpl.deleteLabel(changeLabelToDelete,
        new AsyncCallback<VoidResult>() {

          @Override
          public void onFailure(Throwable caught) {
            fail("No failure expected.");
          }

          @Override
          public void onSuccess(VoidResult result) {
            doVerify();
          }
        });
  }

  @Test
  public void testDeleteLabelChangeNotFoundFailure() throws Exception {
    final ChangeLabel changeLabelToDelete =
        new ChangeLabel(new Change.Id(0), "test-label");

    expect(schema.get()).andReturn(db);

    expect(changeControlFactory.controlFor(changeLabelToDelete.getChangeId()))
        .andThrow(new NoSuchChangeException(changeLabelToDelete.getChangeId()));

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null,
            changeControlFactory);
    changeManageServiceImpl.deleteLabel(changeLabelToDelete,
        new AsyncCallback<VoidResult>() {

          @Override
          public void onFailure(Throwable caught) {
            doVerify();
          }

          @Override
          public void onSuccess(VoidResult result) {
            fail("Label removal failure expected.");
          }
        });
  }

  @Test
  public void testAddLabelChangeNotFoundFailure() throws Exception {
    final ChangeLabel changeLabel =
        new ChangeLabel(new Change.Id(0), "test-label");

    expect(schema.get()).andReturn(db);

    expect(changeControlFactory.controlFor(changeLabel.getChangeId()))
        .andThrow(new NoSuchChangeException(changeLabel.getChangeId()));

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null,
            changeControlFactory);
    changeManageServiceImpl.addLabel(changeLabel,
        new AsyncCallback<VoidResult>() {

          @Override
          public void onFailure(Throwable caught) {
            doVerify();
          }

          @Override
          public void onSuccess(VoidResult result) {
            fail("Label removal failure expected.");
          }
        });
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
  public void testAddLabelWithoutPermission() throws Exception {
    final ChangeLabel changeLabel = new ChangeLabel(new Change.Id(0), "test");

    expect(schema.get()).andReturn(db);

    expect(changeControlFactory.controlFor(changeLabel.getChangeId()))
        .andReturn(changeControl);
    expect(changeControl.canEditArbitraryLabels()).andReturn(Boolean.FALSE);

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null,
            changeControlFactory);
    changeManageServiceImpl.addLabel(changeLabel,
        new AsyncCallback<VoidResult>() {

          @Override
          public void onFailure(Throwable caught) {
            fail("No failure expected.");
          }

          @Override
          public void onSuccess(VoidResult result) {
            doVerify();
          }
        });
  }

  @Test
  public void testAddLabelCompoundByHyphen() throws Exception {
    doValidValueAddLabelTest("A-Special-label");
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
  public void testAddLabelEndingWithHyphen() throws Exception {
    doInvalidValueAddLabelTest("label-");
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

    expect(schema.get()).andReturn(db);

    expect(changeControlFactory.controlFor(changeLabel.getChangeId()))
        .andReturn(changeControl);
    expect(changeControl.canEditArbitraryLabels()).andReturn(Boolean.TRUE);
    expect(db.changeLabels()).andReturn(changeLabels);
    changeLabels.insert(Collections.singletonList(changeLabel));

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null,
            changeControlFactory);
    changeManageServiceImpl.addLabel(changeLabel,
        new AsyncCallback<VoidResult>() {

          @Override
          public void onFailure(Throwable caught) {
            fail("No failure expected.");
          }

          @Override
          public void onSuccess(VoidResult result) {
            doVerify();
          }
        });
  }

  private void doInvalidValueAddLabelTest(String labelValue)
      throws Exception {
    final ChangeLabel changeLabel =
        new ChangeLabel(new Change.Id(0), labelValue);

    expect(schema.get()).andReturn(db);

    expect(changeControlFactory.controlFor(changeLabel.getChangeId()))
        .andReturn(changeControl);
    expect(changeControl.canEditArbitraryLabels()).andReturn(Boolean.TRUE);

    doReplay();

    final ChangeManageServiceImpl changeManageServiceImpl =
        new ChangeManageServiceImpl(schema, null, null, null, null, null,
            changeControlFactory);
    changeManageServiceImpl.addLabel(changeLabel,
        new AsyncCallback<VoidResult>() {

          @Override
          public void onFailure(Throwable caught) {
            fail("No failure expected.");
          }

          @Override
          public void onSuccess(VoidResult result) {
            doVerify();
          }
        });
  }

}
