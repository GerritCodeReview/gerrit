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

import sinon from 'sinon/pkg/sinon-esm';
import '../../../test/common-test-setup-karma.js';
import { createChange } from '../../../test/test-data-generators.js';
import './gr-change-status.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {MERGE_CONFLICT_TOOLTIP} from './gr-change-status.js';

const basicFixture = fixtureFromElement('gr-change-status');

const WIP_TOOLTIP = 'This change isn\'t ready to be reviewed or submitted. ' +
    'It will not appear on dashboards unless you are CC\'ed or assigned, ' +
    'and email notifications will be silenced until the review is started.';

const PRIVATE_TOOLTIP = 'This change is only visible to its owner and ' +
    'current reviewers (or anyone with "View Private Changes" permission).';

suite.only('gr-change-status tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('WIP', () => {
    element.status = 'WIP';
    flush();
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, 'Work in Progress');
    assert.equal(element.tooltipText, WIP_TOOLTIP);
    assert.isTrue(element.classList.contains('wip'));
  });

  test('WIP flat', () => {
    element.flat = true;
    element.status = 'WIP';
    flush();
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, 'WIP');
    assert.isDefined(element.tooltipText);
    assert.isTrue(element.classList.contains('wip'));
    assert.isTrue(element.hasAttribute('flat'));
  });

  test('merged', () => {
    element.status = 'Merged';
    flush();
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('merged'));
    assert.isFalse(
      element.showResolveIcon('Merged', [{url: 'http://google.com'}]));
  });

  test('abandoned', () => {
    element.status = 'Abandoned';
    flush();
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('abandoned'));
  });

  test('merge conflict', () => {
    const status = 'Merge Conflict';
    element.status = status;
    flush();

    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, MERGE_CONFLICT_TOOLTIP);
    assert.isTrue(element.classList.contains('merge-conflict'));
    assert.isFalse(element.hasStatusLink(undefined, [], status));
    assert.isFalse(element.showResolveIcon(status, []));
  });

  test('merge conflict with resolve link', () => {
    const status = 'Merge Conflict';
    const url = 'http://google.com';
    const weblinks = [{url}];

    assert.isTrue(element.hasStatusLink(undefined, weblinks, status));
    assert.equal(element.getStatusLink(undefined, weblinks, status), url);
    assert.isTrue(element.showResolveIcon('Merge Conflict', weblinks));
  });

  test('reverted change', () => {
    const url = 'http://google.com';
    const status = 'Revert Submitted';
    const revertedChange = createChange();
    sinon.stub(GerritNav, 'getUrlForSearchQuery').returns(url);

    assert.isTrue(element.hasStatusLink(revertedChange, [], status));
    assert.equal(element.getStatusLink(revertedChange, [], status), url);
  });

  test('private', () => {
    element.status = 'Private';
    flush();
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, PRIVATE_TOOLTIP);
    assert.isTrue(element.classList.contains('private'));
  });

  test('active', () => {
    element.status = 'Active';
    flush();
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('active'));
  });

  test('ready to submit', () => {
    element.status = 'Ready to submit';
    flush();
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('ready-to-submit'));
  });

  test('updating status removes the previous class', () => {
    element.status = 'Private';
    flush();
    assert.isTrue(element.classList.contains('private'));
    assert.isFalse(element.classList.contains('wip'));

    element.status = 'WIP';
    flush();
    assert.isFalse(element.classList.contains('private'));
    assert.isTrue(element.classList.contains('wip'));
  });
});

