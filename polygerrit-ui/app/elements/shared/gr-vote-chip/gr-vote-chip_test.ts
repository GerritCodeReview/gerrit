/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
      expect(element).shadowDom.to.equal(/* HTML */ ` <gr-tooltip-content
        class="container"
        has-tooltip=""
        title=""
      >
        <div class="max vote-chip">👍</div>
      </gr-tooltip-content>`);
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
      expect(element).shadowDom.to.equal(/* HTML */ ` <gr-tooltip-content
        class="container"
        has-tooltip=""
        title=""
      >
        <div class="positive vote-chip">+2</div>
      </gr-tooltip-content>`);
    });

    test('renders negative vote', async () => {
      const vote: ApprovalInfo = {
        ...createApproval,
        value: -1,
      };
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip .label=${labelInfo} .vote=${vote}></gr-vote-chip>`
      );
      expect(element).shadowDom.to.equal(/* HTML */ ` <gr-tooltip-content
        class="container"
        has-tooltip=""
        title="Wrong Style or Formatting"
      >
        <div class="min vote-chip">-1</div>
      </gr-tooltip-content>`);
    });

    test('renders for more than 1 vote', async () => {
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .label=${labelInfo}
          .vote=${vote}
          more
        ></gr-vote-chip>`
      );
      expect(element).shadowDom.to.equal(/* HTML */ ` <gr-tooltip-content
        class="container more"
        has-tooltip=""
        title=""
      >
        <div class="positive vote-chip">+2</div>
        <div class="chip-angle positive">+2</div>
      </gr-tooltip-content>`);
    });
  });
});
