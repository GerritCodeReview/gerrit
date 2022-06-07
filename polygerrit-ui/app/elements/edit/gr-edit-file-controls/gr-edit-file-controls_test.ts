/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-edit-file-controls';
import {GrEditFileControls} from './gr-edit-file-controls';
import {GrEditConstants} from '../gr-edit-constants';
import {queryAndAssert} from '../../../test/test-utils';
import {GrDropdown} from '../../shared/gr-dropdown/gr-dropdown';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';

const basicFixture = fixtureFromElement('gr-edit-file-controls');

suite('gr-edit-file-controls tests', () => {
  let element: GrEditFileControls;

  let fileActionHandler: sinon.SinonStub;

  setup(async () => {
    element = basicFixture.instantiate();
    fileActionHandler = sinon.stub();
    element.addEventListener('file-action-tap', fileActionHandler);
    await flush();
  });

  test('open tap emits event', () => {
    const actions = queryAndAssert<GrDropdown>(element, '#actions');
    element.filePath = 'foo';
    actions._open();
    flush();

    const row = queryAndAssert(actions, 'li [data-id="open"]');
    MockInteractions.tap(row);
    assert.isTrue(fileActionHandler.called);
    assert.deepEqual(fileActionHandler.lastCall.args[0].detail, {
      action: GrEditConstants.Actions.OPEN.id,
      path: 'foo',
    });
  });

  test('delete tap emits event', () => {
    const actions = queryAndAssert<GrDropdown>(element, '#actions');
    element.filePath = 'foo';
    actions._open();
    flush();

    const row = queryAndAssert(actions, 'li [data-id="delete"]');
    MockInteractions.tap(row);
    assert.isTrue(fileActionHandler.called);
    assert.deepEqual(fileActionHandler.lastCall.args[0].detail, {
      action: GrEditConstants.Actions.DELETE.id,
      path: 'foo',
    });
  });

  test('restore tap emits event', () => {
    const actions = queryAndAssert<GrDropdown>(element, '#actions');
    element.filePath = 'foo';
    actions._open();
    flush();

    const row = queryAndAssert(actions, 'li [data-id="restore"]');
    MockInteractions.tap(row);
    assert.isTrue(fileActionHandler.called);
    assert.deepEqual(fileActionHandler.lastCall.args[0].detail, {
      action: GrEditConstants.Actions.RESTORE.id,
      path: 'foo',
    });
  });

  test('rename tap emits event', () => {
    const actions = queryAndAssert<GrDropdown>(element, '#actions');
    element.filePath = 'foo';
    actions._open();
    flush();

    const row = queryAndAssert(actions, 'li [data-id="rename"]');
    MockInteractions.tap(row);
    assert.isTrue(fileActionHandler.called);
    assert.deepEqual(fileActionHandler.lastCall.args[0].detail, {
      action: GrEditConstants.Actions.RENAME.id,
      path: 'foo',
    });
  });

  test('computed properties', () => {
    assert.equal(element._allFileActions.length, 4);
  });
});
