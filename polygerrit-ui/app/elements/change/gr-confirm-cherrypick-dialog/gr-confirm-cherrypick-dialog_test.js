/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import './gr-confirm-cherrypick-dialog.js';
import {stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-confirm-cherrypick-dialog');

const CHERRY_PICK_TYPES = {
  SINGLE_CHANGE: 1,
  TOPIC: 2,
};
suite('gr-confirm-cherrypick-dialog tests', () => {
  let element;

  setup(() => {
    stubRestApi('getRepoBranches').callsFake(input => {
      if (input.startsWith('test')) {
        return Promise.resolve([
          {
            ref: 'refs/heads/test-branch',
            revision: '67ebf73496383c6777035e374d2d664009e2aa5c',
            can_delete: true,
          },
        ]);
      } else {
        return Promise.resolve([]);
      }
    });
    element = basicFixture.instantiate();
    element.project = 'test-project';
  });

  test('with message missing newline', () => {
    element.changeStatus = 'MERGED';
    element.commitMessage = 'message';
    element.commitNum = '123';
    element.branch = 'master';
    flush();
    const expectedMessage = 'message\n(cherry picked from commit 123)';
    assert.equal(element.message, expectedMessage);
  });

  test('with merged change', () => {
    element.changeStatus = 'MERGED';
    element.commitMessage = 'message\n';
    element.commitNum = '123';
    element.branch = 'master';
    flush();
    const expectedMessage = 'message\n(cherry picked from commit 123)';
    assert.equal(element.message, expectedMessage);
  });

  test('with unmerged change', () => {
    element.changeStatus = 'OPEN';
    element.commitMessage = 'message\n';
    element.commitNum = '123';
    element.branch = 'master';
    flush();
    const expectedMessage = 'message\n';
    assert.equal(element.message, expectedMessage);
  });

  test('with updated commit message', () => {
    element.changeStatus = 'OPEN';
    element.commitMessage = 'message\n';
    element.commitNum = '123';
    element.branch = 'master';
    const myNewMessage = 'updated commit message';
    element.message = myNewMessage;
    flush();
    assert.equal(element.message, myNewMessage);
  });

  test('_getProjectBranchesSuggestions empty', async () => {
    const branches = await element._getProjectBranchesSuggestions('asdf');
    assert.isEmpty(branches);
  });

  suite('cherry pick topic', () => {
    const changes = [
      {
        id: '1234',
        change_id: '12345678901234', topic: 'T', subject: 'random',
        project: 'A',
        _number: 1,
        revisions: {
          a: {_number: 1},
        },
        current_revision: 'a',
      },
      {
        id: '5678',
        change_id: '23456', topic: 'T', subject: 'a'.repeat(100),
        project: 'B',
        _number: 2,
        revisions: {
          a: {_number: 1},
        },
        current_revision: 'a',
      },
    ];
    setup(async () => {
      element.updateChanges(changes);
      element._cherryPickType = CHERRY_PICK_TYPES.TOPIC;
      await flush();
    });

    test('cherry pick topic submit', async () => {
      element.branch = 'master';
      const executeChangeActionStub = stubRestApi(
          'executeChangeAction').returns(Promise.resolve([]));
      MockInteractions.tap(element.shadowRoot.
          querySelector('gr-dialog').$.confirm);
      await flush();
      const args = executeChangeActionStub.args[0];
      assert.equal(args[0], 1);
      assert.equal(args[1], 'POST');
      assert.equal(args[2], '/cherrypick');
      assert.equal(args[4].destination, 'master');
      assert.isTrue(args[4].allow_conflicts);
      assert.isTrue(args[4].allow_empty);
    });

    test('deselecting a change removes it from being cherry picked', () => {
      const duplicateChangesStub = sinon.stub(element,
          'containsDuplicateProject');
      element.branch = 'master';
      const executeChangeActionStub = stubRestApi(
          'executeChangeAction').returns(Promise.resolve([]));
      const checkboxes = element.shadowRoot.querySelectorAll(
          'input[type="checkbox"]');
      assert.equal(checkboxes.length, 2);
      assert.isTrue(checkboxes[0].checked);
      MockInteractions.tap(checkboxes[0]);
      MockInteractions.tap(element.shadowRoot.
          querySelector('gr-dialog').$.confirm);
      flush();
      assert.equal(executeChangeActionStub.callCount, 1);
      assert.isTrue(duplicateChangesStub.called);
    });

    test('deselecting all change shows error message', () => {
      element.branch = 'master';
      const executeChangeActionStub = stubRestApi(
          'executeChangeAction').returns(Promise.resolve([]));
      const checkboxes = element.shadowRoot.querySelectorAll(
          'input[type="checkbox"]');
      assert.equal(checkboxes.length, 2);
      MockInteractions.tap(checkboxes[0]);
      MockInteractions.tap(checkboxes[1]);
      MockInteractions.tap(element.shadowRoot.
          querySelector('gr-dialog').$.confirm);
      flush();
      assert.equal(executeChangeActionStub.callCount, 0);
      assert.equal(element.shadowRoot.querySelector('.error-message').innerText
          , 'No change selected');
    });

    test('_computeStatusClass', () => {
      assert.equal(element._computeStatusClass({id: 1}, {1: {status: 'RUNNING'},
      }), '');
      assert.equal(element._computeStatusClass({id: 1}, {1: {status: 'FAILED'}}
      ), 'error');
    });

    test('submit button is blocked while cherry picks is running', async () => {
      const confirmButton = element.shadowRoot.querySelector('gr-dialog').$
          .confirm;
      assert.isTrue(confirmButton.hasAttribute('disabled'));
      element.branch = 'b';
      await flush();
      assert.isFalse(confirmButton.hasAttribute('disabled'));
      element.updateStatus(changes[0], {status: 'RUNNING'});
      await flush();
      assert.isTrue(confirmButton.hasAttribute('disabled'));
    });
  });

  test('resetFocus', () => {
    const focusStub = sinon.stub(element.$.branchInput, 'focus');
    element.resetFocus();
    assert.isTrue(focusStub.called);
  });

  test('_getProjectBranchesSuggestions non-empty', async () => {
    const branches = await element._getProjectBranchesSuggestions(
        'test-branch');
    assert.equal(branches.length, 1);
    assert.equal(branches[0].name, 'test-branch');
  });
});

