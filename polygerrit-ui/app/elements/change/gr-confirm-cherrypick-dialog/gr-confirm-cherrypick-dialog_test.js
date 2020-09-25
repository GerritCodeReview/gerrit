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

const basicFixture = fixtureFromElement('gr-confirm-cherrypick-dialog');

const CHERRY_PICK_TYPES = {
  SINGLE_CHANGE: 1,
  TOPIC: 2,
};
suite('gr-confirm-cherrypick-dialog tests', () => {
  let element;

  setup(() => {
    stub('gr-rest-api-interface', {
      getRepoBranches(input) {
        if (input.startsWith('test')) {
          return Promise.resolve([
            {
              ref: 'refs/heads/test-branch',
              revision: '67ebf73496383c6777035e374d2d664009e2aa5c',
              can_delete: true,
            },
          ]);
        } else {
          return Promise.resolve({});
        }
      },
    });
    element = basicFixture.instantiate();
    element.project = 'test-project';
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

  test('_getProjectBranchesSuggestions empty', done => {
    element._getProjectBranchesSuggestions('nonexistent').then(branches => {
      assert.equal(branches.length, 0);
      done();
    });
  });

  suite('cherry pick topic', () => {
    const changes = [
      {
        change_id: '12345678901234', topic: 'T', subject: 'random',
        project: 'A',
        _number: 1,
        revisions: {
          a: {_number: 1},
        },
        current_revision: 'a',
      },
      {
        change_id: '23456', topic: 'T', subject: 'a'.repeat(100),
        project: 'B',
        _number: 2,
        revisions: {
          a: {_number: 1},
        },
        current_revision: 'a',
      },
    ];
    setup(() => {
      element.updateChanges(changes);
      element._cherryPickType = CHERRY_PICK_TYPES.TOPIC;
    });

    test('cherry pick topic submit', done => {
      element.branch = 'master';
      const executeChangeActionStub = sinon.stub(element.$.restAPI,
          'executeChangeAction').returns(Promise.resolve([]));
      MockInteractions.tap(element.shadowRoot.
          querySelector('gr-dialog').$.confirm);
      flush(() => {
        const args = executeChangeActionStub.args[0];
        assert.equal(args[0], 1);
        assert.equal(args[1], 'POST');
        assert.equal(args[2], '/cherrypick');
        assert.equal(args[4].destination, 'master');
        assert.isTrue(args[4].allow_conflicts);
        assert.isTrue(args[4].allow_empty);
        done();
      });
    });

    test('_computeStatusClass', () => {
      assert.equal(element._computeStatusClass({id: 1}, {1: {status: 'RUNNING'},
      }), '');
      assert.equal(element._computeStatusClass({id: 1}, {1: {status: 'FAILED'}}
      ), 'error');
    });

    test('submit button is blocked while cherry picks is running', done => {
      const confirmButton = element.shadowRoot.querySelector('gr-dialog').$
          .confirm;
      assert.isFalse(confirmButton.hasAttribute('disabled'));
      element.updateStatus(changes[0], {status: 'RUNNING'});
      flush(() => {
        assert.isTrue(confirmButton.hasAttribute('disabled'));
        done();
      });
    });
  });

  test('resetFocus', () => {
    const focusStub = sinon.stub(element.$.branchInput, 'focus');
    element.resetFocus();
    assert.isTrue(focusStub.called);
  });

  test('_getProjectBranchesSuggestions non-empty', done => {
    element._getProjectBranchesSuggestions('test-branch').then(branches => {
      assert.equal(branches.length, 1);
      assert.equal(branches[0].name, 'test-branch');
      done();
    });
  });
});

