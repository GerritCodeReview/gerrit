/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-message-scores';
import {
  createChange,
  createChangeMessage,
  createDetailedLabelInfo,
} from '../../../test/test-data-generators';
import {queryAll, stubFlags} from '../../../test/test-utils';
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

  test('reset vote', async () => {
    stubFlags('isEnabled').returns(true);
    element = basicFixture.instantiate();
    element.change = {
      ...createChange(),
      labels: {
        'Commit-Queue': createDetailedLabelInfo(),
        'Auto-Submit': createDetailedLabelInfo(),
      },
    };
    element.message = {
      ...createChangeMessage(),
      author: {},
      expanded: false,
      message: 'Patch Set 10: Auto-Submit+1 -Commit-Queue',
    };
    element.labelExtremes = {
      'Commit-Queue': {max: 2, min: 0},
      'Auto-Submit': {max: 1, min: 0},
    };
    await element.updateComplete;
    const triggerChips =
      element.shadowRoot?.querySelectorAll('gr-trigger-vote');
    assert.equal(triggerChips?.length, 1);
    const triggerChip = triggerChips?.[0];
    expect(triggerChip).shadowDom.equal(`<div class="container">
      <span class="label">Auto-Submit</span>
      <gr-vote-chip></gr-vote-chip>
    </div>`);
    const voteChips = triggerChip?.shadowRoot?.querySelectorAll('gr-vote-chip');
    assert.equal(voteChips?.length, 1);
    expect(voteChips?.[0]).shadowDom.equal('');
    const scoreChips = element.shadowRoot?.querySelectorAll('.score');
    assert.equal(scoreChips?.length, 1);
    expect(scoreChips?.[0]).dom.equal(`<span class="removed score">
    Commit-Queue 0 (vote reset)
    </span>`);
  });
});
