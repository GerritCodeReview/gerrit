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

import {ChangeStatus, MessageTag} from '../../../constants/constants.js';
import '../../../test/common-test-setup-karma.js';
import {createChange, createChangeMessages} from '../../../test/test-data-generators.js';
import {stubRestApi} from '../../../test/test-utils.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import './gr-change-status.js';
import {ChangeStates, MERGE_CONFLICT_TOOLTIP} from './gr-change-status.js';

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
    element.status = 'Merge Conflict';
    flush();
    assert.equal(element.shadowRoot
        .querySelector('.chip').innerText, element.status);
    assert.equal(element.tooltipText, MERGE_CONFLICT_TOOLTIP);
    assert.isTrue(element.classList.contains('merge-conflict'));
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

  suite('revert', () => {
    test('show revert created if no revert is merged', () => {
      element.change = {
        ...createChange(),
        messages: createChangeMessages(2),
      };
      element.change.messages[0].message =
          'Created a revert of this change as 12345';
      element.change.messages[0].tag = MessageTag.TAG_REVERT;
      const getChangeStub = stubRestApi('getChange');
      getChangeStub.onFirstCall().returns(Promise.resolve({
        ...createChange(),
      }));
      getChangeStub.onSecondCall().returns(Promise.resolve({
        ...createChange(),
      }));
      element.status = ChangeStates.REVERT_CREATED_OR_SUBMITTED;
      flush(() => {
        assert.equal(element.status, ChangeStates.REVERT_CREATED);
        assert.equal(element.getStatusLink(element.change, element.status),
            GerritNav.getUrlForSearchQuery('12345'));
      });
    });

    test('show revert submitted if revert is merged', () => {
      element.change = {
        ...createChange(),
        messages: createChangeMessages(2),
      };
      element.change.messages[0].message =
          'Created a revert of this change as 12345';
      element.change.messages[0].tag = MessageTag.TAG_REVERT;
      const getChangeStub = stubRestApi('getChange');
      getChangeStub.onFirstCall().returns(Promise.resolve({
        ...createChange(),
        status: ChangeStatus.MERGED,
      }));
      getChangeStub.onSecondCall().returns(Promise.resolve({
        ...createChange(),
      }));
      element.status = ChangeStates.REVERT_CREATED_OR_SUBMITTED;
      flush(() => {
        assert.equal(element.status, ChangeStates.REVERT_SUBMITTED);
        assert.equal(element.getStatusLink(element.change, element.status),
            GerritNav.getUrlForSearchQuery('42'));
      });
    });
  });
});

