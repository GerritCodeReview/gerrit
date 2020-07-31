/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import './gr-label-info.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {isHidden} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-label-info');

suite('gr-account-link tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();

    // Needed to trigger computed bindings.
    element.account = {};
    element.change = {labels: {}};
  });

  suite('remove reviewer votes', () => {
    setup(() => {
      sinon.stub(element, '_computeValueTooltip').returns('');
      element.account = {
        _account_id: 1,
        name: 'bojack',
      };
      const test = {
        all: [{_account_id: 1, name: 'bojack', value: 1}],
        default_value: 0,
        values: [],
      };
      element.change = {
        _number: 42,
        change_id: 'the id',
        actions: [],
        topic: 'the topic',
        status: 'NEW',
        submit_type: 'CHERRY_PICK',
        labels: {test},
        removable_reviewers: [],
      };
      element.labelInfo = test;
      element.label = 'test';

      flushAsynchronousOperations();
    });

    test('_computeCanDeleteVote', () => {
      element.mutable = false;
      const button = element.shadowRoot
          .querySelector('gr-button');
      assert.isTrue(isHidden(button));
      element.change.removable_reviewers = [element.account];
      element.mutable = true;
      assert.isFalse(isHidden(button));
    });

    test('deletes votes', () => {
      const deleteResponse = Promise.resolve({ok: true});
      const deleteStub = sinon.stub(
          element.$.restAPI, 'deleteVote').returns(deleteResponse);

      element.change.removable_reviewers = [element.account];
      element.change.labels.test.recommended = {_account_id: 1};
      element.mutable = true;
      const button = element.shadowRoot
          .querySelector('gr-button');
      MockInteractions.tap(button);
      assert.isTrue(button.disabled);
      return deleteResponse.then(() => {
        assert.isFalse(button.disabled);
        assert.isTrue(deleteStub.calledWithExactly(42, 1, 'test'));
      });
    });
  });

  suite('label color and order', () => {
    test('valueless label rejected', () => {
      element.labelInfo = {rejected: {name: 'someone'}};
      flushAsynchronousOperations();
      const labels = dom(element.root).querySelectorAll('gr-label');
      assert.isTrue(labels[0].classList.contains('negative'));
    });

    test('valueless label approved', () => {
      element.labelInfo = {approved: {name: 'someone'}};
      flushAsynchronousOperations();
      const labels = dom(element.root).querySelectorAll('gr-label');
      assert.isTrue(labels[0].classList.contains('positive'));
    });

    test('-2 to +2', () => {
      element.labelInfo = {
        all: [
          {value: 2, name: 'user 2'},
          {value: 1, name: 'user 1'},
          {value: -1, name: 'user 3'},
          {value: -2, name: 'user 4'},
        ],
        values: {
          '-2': 'Awful',
          '-1': 'Don\'t submit as-is',
          ' 0': 'No score',
          '+1': 'Looks good to me',
          '+2': 'Ready to submit',
        },
      };
      flushAsynchronousOperations();
      const labels = dom(element.root).querySelectorAll('gr-label');
      assert.isTrue(labels[0].classList.contains('max'));
      assert.isTrue(labels[1].classList.contains('positive'));
      assert.isTrue(labels[2].classList.contains('negative'));
      assert.isTrue(labels[3].classList.contains('min'));
    });

    test('-1 to +1', () => {
      element.labelInfo = {
        all: [
          {value: 1, name: 'user 1'},
          {value: -1, name: 'user 2'},
        ],
        values: {
          '-1': 'Don\'t submit as-is',
          ' 0': 'No score',
          '+1': 'Looks good to me',
        },
      };
      flushAsynchronousOperations();
      const labels = dom(element.root).querySelectorAll('gr-label');
      assert.isTrue(labels[0].classList.contains('max'));
      assert.isTrue(labels[1].classList.contains('min'));
    });

    test('0 to +2', () => {
      element.labelInfo = {
        all: [
          {value: 1, name: 'user 2'},
          {value: 2, name: 'user '},
        ],
        values: {
          ' 0': 'Don\'t submit as-is',
          '+1': 'No score',
          '+2': 'Looks good to me',
        },
      };
      flushAsynchronousOperations();
      const labels = dom(element.root).querySelectorAll('gr-label');
      assert.isTrue(labels[0].classList.contains('max'));
      assert.isTrue(labels[1].classList.contains('positive'));
    });

    test('self votes at top', () => {
      element.account = {
        _account_id: 1,
        name: 'bojack',
      };
      element.labelInfo = {
        all: [
          {value: 1, name: 'user 1', _account_id: 2},
          {value: -1, name: 'bojack', _account_id: 1},
        ],
        values: {
          '-1': 'Don\'t submit as-is',
          ' 0': 'No score',
          '+1': 'Looks good to me',
        },
      };
      flushAsynchronousOperations();
      const chips =
          dom(element.root).querySelectorAll('gr-account-link');
      assert.equal(chips[0].account._account_id, element.account._account_id);
    });
  });

  test('_computeValueTooltip', () => {
    // Existing label.
    let labelInfo = {values: {0: 'Baz'}};
    let score = '0';
    assert.equal(element._computeValueTooltip(labelInfo, score), 'Baz');

    // Non-existent score.
    score = '2';
    assert.equal(element._computeValueTooltip(labelInfo, score), '');

    // No values on label.
    labelInfo = {values: {}};
    score = '0';
    assert.equal(element._computeValueTooltip(labelInfo, score), '');
  });

  test('placeholder', () => {
    element.labelInfo = {};
    assert.isFalse(isHidden(element.shadowRoot
        .querySelector('.placeholder')));
    element.labelInfo = {all: []};
    assert.isFalse(isHidden(element.shadowRoot
        .querySelector('.placeholder')));
    element.labelInfo = {all: [{value: 1}]};
    assert.isTrue(isHidden(element.shadowRoot
        .querySelector('.placeholder')));
    element.labelInfo = {rejected: []};
    assert.isTrue(isHidden(element.shadowRoot
        .querySelector('.placeholder')));
    element.labelInfo = {values: [], rejected: [], all: [{value: 1}]};
    assert.isTrue(isHidden(element.shadowRoot
        .querySelector('.placeholder')));
    element.labelInfo = {approved: []};
    assert.isTrue(isHidden(element.shadowRoot
        .querySelector('.placeholder')));
    element.labelInfo = {values: [], approved: [], all: [{value: 1}]};
    assert.isTrue(isHidden(element.shadowRoot
        .querySelector('.placeholder')));
  });
});

