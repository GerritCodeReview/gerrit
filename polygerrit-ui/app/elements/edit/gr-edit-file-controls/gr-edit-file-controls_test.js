/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma.js';
import '../gr-edit-constants.js';
import './gr-edit-file-controls.js';
import {GrEditConstants} from '../gr-edit-constants.js';

const basicFixture = fixtureFromElement('gr-edit-file-controls');

suite('gr-edit-file-controls tests', () => {
  let element;

  let fileActionHandler;

  setup(() => {
    element = basicFixture.instantiate();
    fileActionHandler = sinon.stub();
    element.addEventListener('file-action-tap', fileActionHandler);
  });

  test('open tap emits event', () => {
    const actions = element.$.actions;
    element.filePath = 'foo';
    actions._open();
    flush();

    MockInteractions.tap(actions.shadowRoot
        .querySelector('li [data-id="open"]'));
    assert.isTrue(fileActionHandler.called);
    assert.deepEqual(fileActionHandler.lastCall.args[0].detail,
        {action: GrEditConstants.Actions.OPEN.id, path: 'foo'});
  });

  test('delete tap emits event', () => {
    const actions = element.$.actions;
    element.filePath = 'foo';
    actions._open();
    flush();

    MockInteractions.tap(actions.shadowRoot
        .querySelector('li [data-id="delete"]'));
    assert.isTrue(fileActionHandler.called);
    assert.deepEqual(fileActionHandler.lastCall.args[0].detail,
        {action: GrEditConstants.Actions.DELETE.id, path: 'foo'});
  });

  test('restore tap emits event', () => {
    const actions = element.$.actions;
    element.filePath = 'foo';
    actions._open();
    flush();

    MockInteractions.tap(actions.shadowRoot
        .querySelector('li [data-id="restore"]'));
    assert.isTrue(fileActionHandler.called);
    assert.deepEqual(fileActionHandler.lastCall.args[0].detail,
        {action: GrEditConstants.Actions.RESTORE.id, path: 'foo'});
  });

  test('rename tap emits event', () => {
    const actions = element.$.actions;
    element.filePath = 'foo';
    actions._open();
    flush();

    MockInteractions.tap(actions.shadowRoot
        .querySelector('li [data-id="rename"]'));
    assert.isTrue(fileActionHandler.called);
    assert.deepEqual(fileActionHandler.lastCall.args[0].detail,
        {action: GrEditConstants.Actions.RENAME.id, path: 'foo'});
  });

  test('computed properties', () => {
    assert.equal(element._allFileActions.length, 4);
  });
});

