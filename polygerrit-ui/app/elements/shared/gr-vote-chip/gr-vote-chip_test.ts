/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import {assert, fixture} from '@open-wc/testing';
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
    test('renders positive', async () => {
      const labelInfo = {
        ...createQuickLabelInfo(),
        approved: createAccountWithIdNameAndEmail(),
      };
      const element = await fixture<GrVoteChip>(
        html`<gr-vote-chip .label=${labelInfo}></gr-vote-chip>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container"
          has-tooltip=""
          title=""
        >
          <div class="max vote-chip">&#x2713;</div>
        </gr-tooltip-content>`
      );
    });

    test('renders negative', async () => {
      const labelInfo = {
        ...createQuickLabelInfo(),
        rejected: createAccountWithIdNameAndEmail(),
      };
      const element = await fixture<GrVoteChip>(
        html`<gr-vote-chip .label=${labelInfo}></gr-vote-chip>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container"
          has-tooltip=""
          title=""
        >
          <div class="min vote-chip">&#x2717;</div>
        </gr-tooltip-content>`
      );
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
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container"
          has-tooltip=""
          title=""
        >
          <div class="positive vote-chip">+2</div>
        </gr-tooltip-content>`
      );
    });

    test('renders negative vote', async () => {
      const vote: ApprovalInfo = {
        ...createApproval,
        value: -1,
      };
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip .label=${labelInfo} .vote=${vote}></gr-vote-chip>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container"
          has-tooltip=""
          title="Wrong Style or Formatting"
        >
          <div class="min vote-chip">-1</div>
        </gr-tooltip-content>`
      );
    });

    test('renders for more than 1 vote', async () => {
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .label=${labelInfo}
          .vote=${vote}
          more
        ></gr-vote-chip>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container more"
          has-tooltip=""
          title=""
        >
          <div class="positive vote-chip">+2</div>
          <div class="chip-angle positive">+2</div>
        </gr-tooltip-content>`
      );
    });

    test('renders with tooltip who voted', async () => {
      vote.name = 'Tester';
      const labelInfo = {
        all: [{value: 2}, {value: 1}],
        values: {'+2': 'Great'},
      };
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .label=${labelInfo}
          .vote=${vote}
          tooltip-with-who-voted
        ></gr-vote-chip>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container"
          has-tooltip=""
          title="Tester: Great"
        >
          <div class="max vote-chip">+2</div>
        </gr-tooltip-content>`
      );
    });

    test('renders with display value instead of latest vote', async () => {
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .displayValue=${-1}
          .label=${labelInfo}
          .vote=${vote}
        ></gr-vote-chip>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container"
          has-tooltip=""
          title=""
        >
          <div class="min vote-chip">-1</div>
        </gr-tooltip-content>`
      );
    });

    test('renders AI indicator when vote.is_ai is true', async () => {
      const aiVote: ApprovalInfo = {
        ...createApproval(),
        value: 2,
        is_ai: true,
      };
      const labelInfoWithValues = {
        ...createDetailedLabelInfo(),
        values: {'+2': 'Approved'},
      };
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .label=${labelInfoWithValues}
          .vote=${aiVote}
        ></gr-vote-chip>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container"
          has-tooltip=""
          title="Predicted by AI: Approved"
        >
          <div class="has-ai max vote-chip">
            <gr-icon class="ai-icon" custom="" icon="ai"></gr-icon>
            +2
          </div>
        </gr-tooltip-content>`
      );
    });

    test('renders AI indicator when isAi property is true', async () => {
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .label=${labelInfo}
          .vote=${vote}
          .isAi=${true}
        ></gr-vote-chip>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container"
          has-tooltip=""
          title="Predicted by AI"
        >
          <div class="has-ai positive vote-chip">
            <gr-icon class="ai-icon" custom="" icon="ai"></gr-icon>
            +2
          </div>
        </gr-tooltip-content>`
      );
    });

    test('renders AI indicator with tooltip who voted', async () => {
      const aiVote: ApprovalInfo = {
        ...createApproval(),
        name: 'Gerrit AI',
        value: 2,
        is_ai: true,
      };
      const labelInfoWithAll = {
        all: [{value: 2}],
        values: {'+2': 'Great'},
      };
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .label=${labelInfoWithAll}
          .vote=${aiVote}
          tooltip-with-who-voted
        ></gr-vote-chip>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container"
          has-tooltip=""
          title="Gerrit AI (Predicted by AI): Great"
        >
          <div class="has-ai max vote-chip">
            <gr-icon class="ai-icon" custom="" icon="ai"></gr-icon>
            +2
          </div>
        </gr-tooltip-content>`
      );
    });
  });
});
