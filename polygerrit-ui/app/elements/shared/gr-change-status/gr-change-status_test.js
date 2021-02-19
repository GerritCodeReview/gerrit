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
import './gr-change-status.js';
import {MERGE_CONFLICT_TOOLTIP} from './gr-change-status.js';

const basicFixture = fixtureFromElement('gr-change-status');

const WIP_TOOLTIP = 'This change isn\'t ready to be reviewed or submitted. ' +
    'It will not appear on dashboards unless you are CC\'ed or assigned, ' +
    'and email notifications will be silenced until the review is started.';

const PRIVATE_TOOLTIP = 'This change is only visible to its owner and ' +
    'current reviewers (or anyone with "View Private Changes" permission).';

suite('gr-change-status tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('WIP', () => {
    element.status = 'WIP';
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, 'Work in Progress');
    assert.equal(element.tooltipText, WIP_TOOLTIP);
    assert.isTrue(element.classList.contains('wip'));
  });

  test('WIP flat', () => {
    element.flat = true;
    element.status = 'WIP';
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, 'WIP');
    assert.isDefined(element.tooltipText);
    assert.isTrue(element.classList.contains('wip'));
    assert.isTrue(element.hasAttribute('flat'));
  });

  test('merged', () => {
    element.status = 'Merged';
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('merged'));
  });

  test('abandoned', () => {
    element.status = 'Abandoned';
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('abandoned'));
  });

  test('merge conflict', () => {
    element.status = 'Merge Conflict';
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, MERGE_CONFLICT_TOOLTIP);
    assert.isTrue(element.classList.contains('merge-conflict'));
  });

  test('private', () => {
    element.status = 'Private';
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, PRIVATE_TOOLTIP);
    assert.isTrue(element.classList.contains('private'));
  });

  test('active', () => {
    element.status = 'Active';
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('active'));
  });

  test('ready to submit', () => {
    element.status = 'Ready to submit';
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('ready-to-submit'));
  });

  test('updating status removes the previous class', () => {
    element.status = 'Private';
    assert.isTrue(element.classList.contains('private'));
    assert.isFalse(element.classList.contains('wip'));

    element.status = 'WIP';
    assert.isFalse(element.classList.contains('private'));
    assert.isTrue(element.classList.contains('wip'));
  });
});

