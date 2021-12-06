/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import {fixture} from '@open-wc/testing-helpers';
import {html} from 'lit';
import {getAppContext} from '../../../services/app-context';
import './gr-vote-chip';
import {GrVoteChip} from './gr-vote-chip';
import {
  createAccountWithIdNameAndEmail,
  createApproval,
  createDetailedLabelInfo,
  createQuickLabelInfo,
} from '../../../test/test-data-generators';
import {ApprovalInfo} from '../../../api/rest-api';

suite('gr-vote-chip tests', () => {
  setup(() => {
    sinon.stub(getAppContext().flagsService, 'isEnabled').returns(true);
  });

  suite('with QuickLabelInfo', () => {
    let element: GrVoteChip;

    setup(async () => {
      const labelInfo = {
        ...createQuickLabelInfo(),
        approved: createAccountWithIdNameAndEmail(),
      };
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip .label=${labelInfo}></gr-vote-chip>`
      );
    });

    test('renders', () => {
      expect(element).shadowDom.to.equal(`<span class="container">
        <div class="max vote-chip">👍</div>
      </span>`);
    });
  });

  suite('with DetailedLabelInfo', () => {
    let element: GrVoteChip;
    const labelInfo = createDetailedLabelInfo();
    const vote: ApprovalInfo = {
      ...createApproval(),
      value: 2,
    };

    setup(async () => {
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip .label=${labelInfo} .vote=${vote}></gr-vote-chip>`
      );
    });

    test('renders', () => {
      expect(element).shadowDom.to.equal(`<span class="container">
        <div class="positive vote-chip">
            +2
        </div>
      </span>`);
    });

    test('renders negative vote', async () => {
      const vote: ApprovalInfo = {
        ...createApproval,
        value: -1,
      };
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip .label=${labelInfo} .vote=${vote}></gr-vote-chip>`
      );
      expect(element).shadowDom.to.equal(`<span class="container">
        <div class="min vote-chip">
            -1
        </div>
      </span>`);
    });

    test('renders for more than 1 vote', async () => {
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .label=${labelInfo}
          .vote=${vote}
          more
        ></gr-vote-chip>`
      );
      expect(element).shadowDom.to.equal(`<span class="container more">
        <div class="positive vote-chip">+2</div>
        <div class="chip-angle positive">+2</div>
      </span>`);
    });
  });
});
