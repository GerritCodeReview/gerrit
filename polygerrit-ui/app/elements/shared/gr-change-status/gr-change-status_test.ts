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

import '../../../test/common-test-setup-karma';
import {createChange} from '../../../test/test-data-generators';
import './gr-change-status';
import {ChangeStates, GrChangeStatus, WIP_TOOLTIP} from './gr-change-status';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {MERGE_CONFLICT_TOOLTIP} from './gr-change-status';

const basicFixture = fixtureFromElement('gr-change-status');

const PRIVATE_TOOLTIP =
  'This change is only visible to its owner and ' +
  'current reviewers (or anyone with "View Private Changes" permission).';

suite('gr-change-status tests', () => {
  let element: GrChangeStatus;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('WIP', () => {
    element.status = ChangeStates.WIP;
    flush();
    assert.equal(
      element.shadowRoot!.querySelector<HTMLDivElement>('.chip')!.innerText,
      'Work in Progress'
    );
    assert.equal(element.tooltipText, WIP_TOOLTIP);
    assert.isTrue(element.classList.contains('wip'));
  });

  test('WIP flat', () => {
    element.flat = true;
    element.status = ChangeStates.WIP;
    flush();
    assert.equal(
      element.shadowRoot!.querySelector<HTMLDivElement>('.chip')!.innerText,
      'WIP'
    );
    assert.isDefined(element.tooltipText);
    assert.isTrue(element.classList.contains('wip'));
    assert.isTrue(element.hasAttribute('flat'));
  });

  test('merged', () => {
    element.status = ChangeStates.MERGED;
    flush();
    assert.equal(
      element.shadowRoot!.querySelector<HTMLDivElement>('.chip')!.innerText,
      'Merged'
    );
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('merged'));
    assert.isFalse(
      element.showResolveIcon([{url: 'http://google.com'}], ChangeStates.MERGED)
    );
  });

  test('abandoned', () => {
    element.status = ChangeStates.ABANDONED;
    flush();
    assert.equal(
      element.shadowRoot!.querySelector<HTMLDivElement>('.chip')!.innerText,
      'Abandoned'
    );
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('abandoned'));
  });

  test('merge conflict', () => {
    const status = ChangeStates.MERGE_CONFLICT;
    element.status = status;
    flush();

    assert.equal(
      element.shadowRoot!.querySelector<HTMLDivElement>('.chip')!.innerText,
      'Merge Conflict'
    );
    assert.equal(element.tooltipText, MERGE_CONFLICT_TOOLTIP);
    assert.isTrue(element.classList.contains('merge-conflict'));
    assert.isFalse(element.hasStatusLink(undefined, [], status));
    assert.isFalse(element.showResolveIcon([], status));
  });

  test('merge conflict with resolve link', () => {
    const status = ChangeStates.MERGE_CONFLICT;
    const url = 'http://google.com';
    const weblinks = [{url}];

    assert.isTrue(element.hasStatusLink(undefined, weblinks, status));
    assert.equal(element.getStatusLink(undefined, weblinks, status), url);
    assert.isTrue(element.showResolveIcon(weblinks, status));
  });

  test('reverted change', () => {
    const url = 'http://google.com';
    const status = ChangeStates.REVERT_SUBMITTED;
    const revertedChange = createChange();
    sinon.stub(GerritNav, 'getUrlForSearchQuery').returns(url);

    assert.isTrue(element.hasStatusLink(revertedChange, [], status));
    assert.equal(element.getStatusLink(revertedChange, [], status), url);
  });

  test('private', () => {
    element.status = ChangeStates.PRIVATE;
    flush();
    assert.equal(
      element.shadowRoot!.querySelector<HTMLDivElement>('.chip')!.innerText,
      'Private'
    );
    assert.equal(element.tooltipText, PRIVATE_TOOLTIP);
    assert.isTrue(element.classList.contains('private'));
  });

  test('active', () => {
    element.status = ChangeStates.ACTIVE;
    flush();
    assert.equal(
      element.shadowRoot!.querySelector<HTMLDivElement>('.chip')!.innerText,
      'Active'
    );
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('active'));
  });

  test('ready to submit', () => {
    element.status = ChangeStates.READY_TO_SUBMIT;
    flush();
    assert.equal(
      element.shadowRoot!.querySelector<HTMLDivElement>('.chip')!.innerText,
      'Ready to submit'
    );
    assert.equal(element.tooltipText, '');
    assert.isTrue(element.classList.contains('ready-to-submit'));
  });

  test('updating status removes the previous class', () => {
    element.status = ChangeStates.PRIVATE;
    flush();
    assert.isTrue(element.classList.contains('private'));
    assert.isFalse(element.classList.contains('wip'));

    element.status = ChangeStates.WIP;
    flush();
    assert.isFalse(element.classList.contains('private'));
    assert.isTrue(element.classList.contains('wip'));
  });
});
