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

import '../../../test/common-test-setup-karma';
import './gr-message-scores';
import {createChangeMessage} from '../../../test/test-data-generators';
import {queryAll} from '../../../test/test-utils';
import {GrMessageScores} from './gr-message-scores';

const basicFixture = fixtureFromElement('gr-message-scores');

suite('gr-message-score tests', () => {
  let element: GrMessageScores;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('votes', async () => {
    element.message = {
      ...createChangeMessage(),
      author: {},
      expanded: false,
      message: 'Patch Set 1: Verified+1 Code-Review-2 Trybot-Label3+1 Blub+1',
    };
    element.labelExtremes = {
      Verified: {max: 1, min: -1},
      'Code-Review': {max: 2, min: -2},
      'Trybot-Label3': {max: 3, min: 0},
    };
    await element.updateComplete;
    const scoreChips = queryAll(element, '.score');
    assert.equal(scoreChips.length, 3);

    assert.isTrue(scoreChips[0].classList.contains('positive'));
    assert.isTrue(scoreChips[0].classList.contains('max'));

    assert.isTrue(scoreChips[1].classList.contains('negative'));
    assert.isTrue(scoreChips[1].classList.contains('min'));

    assert.isTrue(scoreChips[2].classList.contains('positive'));
    assert.isFalse(scoreChips[2].classList.contains('min'));
  });

  test('Uploaded patch set X', async () => {
    element.message = {
      ...createChangeMessage(),
      author: {},
      expanded: false,
      message:
        'Uploaded patch set 1:' +
        'Verified+1 Code-Review-2 Trybot-Label3+1 Blub+1',
    };
    element.labelExtremes = {
      Verified: {max: 1, min: -1},
      'Code-Review': {max: 2, min: -2},
      'Trybot-Label3': {max: 3, min: 0},
    };
    await element.updateComplete;
    const scoreChips = queryAll(element, '.score');
    assert.equal(scoreChips.length, 3);

    assert.isTrue(scoreChips[0].classList.contains('positive'));
    assert.isTrue(scoreChips[0].classList.contains('max'));

    assert.isTrue(scoreChips[1].classList.contains('negative'));
    assert.isTrue(scoreChips[1].classList.contains('min'));

    assert.isTrue(scoreChips[2].classList.contains('positive'));
    assert.isFalse(scoreChips[2].classList.contains('min'));
  });

  test('Uploaded and rebased', async () => {
    element.message = {
      ...createChangeMessage(),
      author: {},
      expanded: false,
      message: 'Uploaded patch set 4: Commit-Queue+1: Patch Set 3 was rebased.',
    };
    element.labelExtremes = {
      'Commit-Queue': {max: 2, min: -2},
    };
    await element.updateComplete;
    const scoreChips = queryAll(element, '.score');
    assert.equal(scoreChips.length, 1);
    assert.isTrue(scoreChips[0].classList.contains('positive'));
  });

  test('removed votes', async () => {
    element.message = {
      ...createChangeMessage(),
      author: {},
      expanded: false,
      message: 'Patch Set 1: Verified+1 -Code-Review -Commit-Queue',
    };
    element.labelExtremes = {
      Verified: {max: 1, min: -1},
      'Code-Review': {max: 2, min: -2},
      'Commit-Queue': {max: 3, min: 0},
    };
    await element.updateComplete;
    const scoreChips = queryAll(element, '.score');
    assert.equal(scoreChips.length, 3);

    assert.isTrue(scoreChips[1].classList.contains('removed'));
    assert.isTrue(scoreChips[2].classList.contains('removed'));
  });

  test('false negative vote', async () => {
    element.message = {
      ...createChangeMessage(),
      author: {},
      expanded: false,
      message: 'Patch Set 1: Cherry Picked from branch stable-2.14.',
    };
    element.labelExtremes = {};
    await element.updateComplete;
    const scoreChips = element.shadowRoot?.querySelectorAll('.score');
    assert.equal(scoreChips?.length, 0);
  });
});
