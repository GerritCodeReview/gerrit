/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {
  createChange,
  TEST_NUMERIC_CHANGE_ID,
} from '../../../test/test-data-generators';
import './gr-change-status';
import {ChangeStates, GrChangeStatus, WIP_TOOLTIP} from './gr-change-status';
import {MERGE_CONFLICT_TOOLTIP} from './gr-change-status';
import {fixture, html, assert} from '@open-wc/testing';
import {queryAndAssert} from '../../../test/test-utils';

const PRIVATE_TOOLTIP =
  'This change is only visible to its owner and ' +
  'current reviewers (or anyone with "View Private Changes" permission).';

suite('gr-change-status tests', () => {
  let element: GrChangeStatus;

  setup(async () => {
    element = await fixture<GrChangeStatus>(html`
      <gr-change-status></gr-change-status>
    `);
  });

  test('render', async () => {
    element.status = ChangeStates.WIP;
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-tooltip-content
          has-tooltip=""
          max-width="40em"
          position-below=""
          title="This change isn't ready to be reviewed or submitted. It will not appear on dashboards unless you are in the attention set, and email notifications will be silenced until the review is started."
        >
          <div aria-label="Label: WIP" class="chip">Work in Progress</div>
        </gr-tooltip-content>
      `
    );
  });

  test('WIP', async () => {
    element.status = ChangeStates.WIP;
    await element.updateComplete;
    assert.equal(
      queryAndAssert<HTMLDivElement>(element, '.chip').innerText,
      'Work in Progress'
    );
    assert.equal(element.tooltipText, WIP_TOOLTIP);
    assert.isTrue(element.classList.contains('wip'));
  });

  test('WIP flat', async () => {
    element.flat = true;
    element.status = ChangeStates.WIP;
    await element.updateComplete;
    assert.equal(
      queryAndAssert<HTMLDivElement>(element, '.chip').innerText,
      'WIP'
    );
    assert.isDefined(element.tooltipText);
    assert.isTrue(element.classList.contains('wip'));
    assert.isTrue(element.hasAttribute('flat'));
  });

  test('merged', async () => {
    element.status = ChangeStates.MERGED;
    await element.updateComplete;
    assert.equal(
      queryAndAssert<HTMLDivElement>(element, '.chip').innerText,
      'Merged'
    );
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('merged'));
    element.resolveWeblinks = [{url: 'http://google.com'}];
    element.status = ChangeStates.MERGED;
    assert.isFalse(element.showResolveIcon());
  });

  test('abandoned', async () => {
    element.status = ChangeStates.ABANDONED;
    await element.updateComplete;
    assert.equal(
      queryAndAssert<HTMLDivElement>(element, '.chip').innerText,
      'Abandoned'
    );
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('abandoned'));
  });

  test('merge conflict', async () => {
    const status = ChangeStates.MERGE_CONFLICT;
    element.status = status;
    await element.updateComplete;

    assert.equal(
      queryAndAssert<HTMLDivElement>(element, '.chip').innerText,
      'Merge Conflict'
    );
    assert.equal(element.tooltipText, MERGE_CONFLICT_TOOLTIP);
    assert.isTrue(element.classList.contains('merge-conflict'));
    element.revertedChange = undefined;
    element.resolveWeblinks = [];
    element.status = status;
    assert.isFalse(element.hasStatusLink());
    assert.isFalse(element.showResolveIcon());
  });

  test('merge conflict with resolve link', () => {
    const status = ChangeStates.MERGE_CONFLICT;
    const url = 'http://google.com';
    const weblinks = [{url}];

    element.revertedChange = undefined;
    element.resolveWeblinks = weblinks;
    element.status = status;
    assert.isTrue(element.hasStatusLink());
    assert.equal(element.getStatusLink(), url);
    assert.isTrue(element.showResolveIcon());
  });

  test('reverted change', () => {
    const status = ChangeStates.REVERT_SUBMITTED;
    const revertedChange = createChange();

    element.revertedChange = revertedChange;
    element.resolveWeblinks = [];
    element.status = status;
    assert.isTrue(element.hasStatusLink());
    assert.equal(element.getStatusLink(), `/q/${TEST_NUMERIC_CHANGE_ID}`);
  });

  test('private', async () => {
    element.status = ChangeStates.PRIVATE;
    await element.updateComplete;
    assert.equal(
      queryAndAssert<HTMLDivElement>(element, '.chip').innerText,
      'Private'
    );
    assert.equal(element.tooltipText, PRIVATE_TOOLTIP);
    assert.isTrue(element.classList.contains('private'));
  });

  test('active', async () => {
    element.status = ChangeStates.ACTIVE;
    await element.updateComplete;
    assert.equal(
      queryAndAssert<HTMLDivElement>(element, '.chip').innerText,
      'Active'
    );
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('active'));
  });

  test('ready to submit', async () => {
    element.status = ChangeStates.READY_TO_SUBMIT;
    await element.updateComplete;
    assert.equal(
      queryAndAssert<HTMLDivElement>(element, '.chip').innerText,
      'Ready to submit'
    );
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('ready-to-submit'));
  });

  test('updating status removes the previous class', async () => {
    element.status = ChangeStates.PRIVATE;
    await element.updateComplete;
    assert.isTrue(element.classList.contains('private'));
    assert.isFalse(element.classList.contains('wip'));

    element.status = ChangeStates.WIP;
    await element.updateComplete;
    assert.isFalse(element.classList.contains('private'));
    assert.isTrue(element.classList.contains('wip'));
  });
});
