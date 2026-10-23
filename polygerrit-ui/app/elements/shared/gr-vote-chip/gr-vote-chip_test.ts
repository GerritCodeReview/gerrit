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

  for (const [value, expected] of [
    [-2, 'CR'],
    [-1, 'CR-1'],
    [1, 'CR+1'],
    [2, 'CR'],
  ] as const) {
    test(`self-contained badge for ${value}`, async () => {
      const element = await fixture<GrVoteChip>(html`<gr-vote-chip
        label-name="CR"
        .label=${{
          values: {
            '-2': 'Block',
            '-1': 'Dislike',
            '0': 'Neutral',
            '+1': 'Recommend',
            '+2': 'Approve',
          },
        }}
        .vote=${{...createApproval(), value}}
        tooltip-with-who-voted
      ></gr-vote-chip>`);
      assert.equal(
        element.shadowRoot!.querySelector('.vote-chip')!.textContent?.trim(),
        expected
      );
      const icon = element.shadowRoot!.querySelector('gr-icon');
      if (Math.abs(value) === 2) {
        assert.equal(icon?.icon, value > 0 ? 'check' : 'close');
        assert.include(
          element
            .shadowRoot!.querySelector('gr-tooltip-content')!
            .getAttribute('title')!,
          value > 0 ? 'Approve' : 'Block'
        );
      } else {
        assert.isNull(icon);
      }
      element.labelName = undefined;
      await element.updateComplete;
      assert.equal(
        element.shadowRoot!.querySelector('.vote-chip')!.textContent?.trim(),
        value > 0 ? `+${value}` : `${value}`
      );
    });
  }

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
      const container = await fixture<HTMLDivElement>(
        html`<div>
          <gr-vote-chip .label=${labelInfo} .vote=${vote} more></gr-vote-chip>
        </div>`
      );
      element = container.querySelector('gr-vote-chip')!;
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container more"
          has-tooltip=""
          title=""
        >
          <div class="positive vote-chip">+2</div>
          <div aria-hidden="true" class="chip-angle positive">+2</div>
        </gr-tooltip-content>`
      );
      const voteChip = element.shadowRoot!.querySelector('.vote-chip')!;
      const chipAngle = element.shadowRoot!.querySelector('.chip-angle')!;
      assert.equal(
        chipAngle.getBoundingClientRect().width,
        voteChip.getBoundingClientRect().width
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

    test('renders AI sparkle inside vote-chip when is-ai is set', async () => {
      const labelInfoWithPlus2 = {
        ...labelInfo,
        values: {
          ...labelInfo.values,
          '+2': 'Approved',
        },
      };
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .label=${labelInfoWithPlus2}
          .vote=${vote}
          is-ai
        ></gr-vote-chip>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container"
          has-tooltip=""
          title="Approved (AI vote)"
        >
          <div class="max vote-chip">
            +2
            <gr-icon class="ai-icon" custom="" icon="spark"> </gr-icon>
          </div>
        </gr-tooltip-content>`
      );
    });

    test('renders AI sparkle with tooltip-with-who-voted', async () => {
      vote.name = 'AI Reviewer';
      const labelInfoWithPlus2 = {
        all: [{value: 2}, {value: 1}],
        values: {'+2': 'Approved'},
      };
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .label=${labelInfoWithPlus2}
          .vote=${vote}
          tooltip-with-who-voted
          is-ai
        ></gr-vote-chip>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container"
          has-tooltip=""
          title="AI Reviewer: Approved (AI vote)"
        >
          <div class="max vote-chip">
            +2
            <gr-icon class="ai-icon" custom="" icon="spark"> </gr-icon>
          </div>
        </gr-tooltip-content>`
      );
    });

    test('renders stacked AI vote-chip when is-ai and more are set', async () => {
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .label=${labelInfo}
          .vote=${vote}
          is-ai
          more
        ></gr-vote-chip>`
      );
      assert.shadowDom.equal(
        element,
        /* HTML */ ` <gr-tooltip-content
          class="container more"
          has-tooltip=""
          title="(AI vote)"
        >
          <div class="positive vote-chip">
            +2
            <gr-icon class="ai-icon" custom="" icon="spark"> </gr-icon>
          </div>
          <div aria-hidden="true" class="chip-angle positive">+2</div>
        </gr-tooltip-content>`
      );

      const voteChip = element.shadowRoot!.querySelector('.vote-chip')!;
      const chipAngle = element.shadowRoot!.querySelector('.chip-angle')!;
      const voteChipStyle = getComputedStyle(voteChip);
      const chipAngleStyle = getComputedStyle(chipAngle);
      assert.equal(voteChipStyle.alignItems, 'center');
      assert.equal(voteChipStyle.paddingLeft, '2px');
      assert.equal(voteChipStyle.paddingRight, '1px');
      assert.equal(chipAngleStyle.boxSizing, 'border-box');
      const voteChipRect = voteChip.getBoundingClientRect();
      const chipAngleRect = chipAngle.getBoundingClientRect();
      assert.equal(chipAngleRect.width, voteChipRect.width);
      assert.equal(chipAngleRect.height, voteChipRect.height);
    });

    test('resolves AI status from label and vote without is-ai attribute', async () => {
      const aiVote: ApprovalInfo = {...createApproval(), value: 2, is_ai: true};
      const aiLabelInfo = {
        ...createDetailedLabelInfo(),
        all: [aiVote],
      };
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .label=${aiLabelInfo}
          .vote=${aiVote}
        ></gr-vote-chip>`
      );
      assert.isNotNull(element.shadowRoot!.querySelector('gr-icon.ai-icon'));

      // Mixed AI and non-AI votes of the same value should not render AI sparkle
      const humanVote: ApprovalInfo = {
        ...createApproval(),
        value: 2,
      };
      element.label = {
        ...createDetailedLabelInfo(),
        all: [aiVote, humanVote],
      };
      await element.updateComplete;
      assert.isNull(element.shadowRoot!.querySelector('gr-icon.ai-icon'));
    });

    test('retains fixed width when circle-shape and is-ai are both set', async () => {
      element = await fixture<GrVoteChip>(
        html`<gr-vote-chip
          .label=${labelInfo}
          .vote=${vote}
          circle-shape
          is-ai
        ></gr-vote-chip>`
      );
      const voteChip = element.shadowRoot!.querySelector('.vote-chip')!;
      const aiIcon = element.shadowRoot!.querySelector('gr-icon.ai-icon')!;
      const voteChipStyle = getComputedStyle(voteChip);
      const aiIconStyle = getComputedStyle(aiIcon);
      assert.equal(voteChipStyle.width, '16px');
      assert.equal(aiIconStyle.display, 'none');
    });
  });
});
