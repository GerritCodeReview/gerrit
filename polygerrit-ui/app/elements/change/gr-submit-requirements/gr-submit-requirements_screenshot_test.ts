/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-submit-requirements';
import '../../shared/gr-vote-chip/gr-vote-chip';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrSubmitRequirements} from './gr-submit-requirements';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {setViewport} from '@web/test-runner-commands';
import {
  createAccountWithIdNameAndEmail,
  createApproval,
  createDetailedLabelInfo,
  createParsedChange,
  createSubmitRequirementExpressionInfo,
  createSubmitRequirementResultInfo,
} from '../../../test/test-data-generators';
import {
  DetailedLabelInfo,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../../../api/rest-api';
import {LoadingStatus, ParsedChangeInfo} from '../../../types/types';
import {testResolver} from '../../../test/common-test-setup';
import {
  ChangeModel,
  changeModelToken,
} from '../../../models/change/change-model';
import {checksModelToken} from '../../../models/checks/checks-model';

suite('gr-submit-requirements screenshot tests', () => {
  let element: GrSubmitRequirements;
  let change: ParsedChangeInfo;
  let changeModel: ChangeModel;

  setup(async () => {
    testResolver(checksModelToken);
    changeModel = testResolver(changeModelToken);

    const covRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      name: 'Code-Coverage',
      description: 'Code Coverage check',
      status: SubmitRequirementStatus.SATISFIED,
      submittability_expression_result: {
        ...createSubmitRequirementExpressionInfo(),
        expression: 'label:Code-Coverage=+2',
        fulfilled: true,
      },
    };

    const crRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      name: 'Code-Review',
      description: 'Code Review approval',
      status: SubmitRequirementStatus.SATISFIED,
      submittability_expression_result: {
        ...createSubmitRequirementExpressionInfo(),
        expression: 'label:Code-Review=+2',
        fulfilled: true,
      },
    };

    const pvRequirement: SubmitRequirementResultInfo = {
      ...createSubmitRequirementResultInfo(),
      name: 'Presubmit-Verified',
      description: 'Presubmit checks passed',
      status: SubmitRequirementStatus.SATISFIED,
      submittability_expression_result: {
        ...createSubmitRequirementExpressionInfo(),
        expression: 'label:Presubmit-Verified=+1',
        fulfilled: true,
      },
    };

    const submitRequirements: SubmitRequirementResultInfo[] = [
      covRequirement,
      crRequirement,
      pvRequirement,
    ];

    change = {
      ...createParsedChange(),
      submittable: true,
      submit_requirements: submitRequirements,
      labels: {
        'Code-Coverage': {
          ...createDetailedLabelInfo(),
          all: [
            {
              ...createApproval(),
              value: 2,
              is_ai: true,
            },
          ],
          values: {
            '-2': 'Failing',
            '-1': 'Warning',
            ' 0': 'No score',
            '+1': 'Acceptable',
            '+2': 'High confidence pass',
          },
        },
        'Code-Review': {
          ...createDetailedLabelInfo(),
          all: [
            {
              ...createApproval(),
              value: 2,
              is_ai: false,
            },
          ],
          values: {
            '-2': 'Do not submit',
            '-1': 'I would prefer that you didn\'t submit this',
            ' 0': 'No score',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
        },
        'Presubmit-Verified': {
          ...createDetailedLabelInfo(),
          all: [
            {
              ...createApproval(),
              value: 1,
              is_ai: false,
            },
          ],
          values: {
            '-1': 'Fails',
            ' 0': 'No score',
            '+1': 'Verified',
          },
        },
      },
    };

    const account = createAccountWithIdNameAndEmail();
    changeModel.setState({
      change,
      submittabilityInfo: {
        changeNum: change._number,
        submitRequirements,
        submittable: true,
      },
      loadingStatus: LoadingStatus.LOADED,
      submittabilityLoadingStatus: LoadingStatus.LOADED,
    });

    element = await fixture<GrSubmitRequirements>(
      html`<gr-submit-requirements
        .change=${change}
        .account=${account}
      ></gr-submit-requirements>`
    );
    await element.updateComplete;
  });

  test('submit requirements with AI Code-Coverage vote', async () => {
    await setViewport({width: 800, height: 600});

    const container = document.createElement('div');
    container.style.width = '420px';
    container.style.padding = '16px';
    container.style.backgroundColor = 'var(--view-background-color)';
    container.appendChild(element);
    document.body.appendChild(container);

    try {
      await visualDiff(container, 'gr-submit-requirements-ai-indicator');
      await visualDiffDarkTheme(container, 'gr-submit-requirements-ai-indicator');
    } finally {
      document.body.removeChild(container);
    }
  });

  test('vote chips comparison AI vs Human', async () => {
    await setViewport({width: 800, height: 600});

    const testLabel: DetailedLabelInfo = {
      ...createDetailedLabelInfo(),
      values: {
        '-2': 'Do not merge',
        '-1': 'I would prefer that you didn\'t submit this',
        ' 0': 'No score',
        '+1': 'Looks good to me, but someone else must approve',
        '+2': 'Looks good to me, approved',
      },
    };

    const container = await fixture<HTMLDivElement>(html`
      <div style="display: inline-flex; flex-direction: column; gap: 16px; padding: 24px; background: var(--view-background-color); border: 1px solid var(--border-color); border-radius: 8px; font-family: var(--font-family);">
        <div style="font-size: 16px; font-weight: 500; color: var(--primary-text-color); margin-bottom: 4px;">
          Vote Provenance &amp; AI Indicator Comparison
        </div>
        <div style="font-size: 12px; color: var(--deemphasized-text-color); margin-bottom: 8px;">
          Distinct AI spark icon (✦) indicates votes predicted by AI vs runtime execution
        </div>
        <div style="display: grid; grid-template-columns: 140px 100px 100px; gap: 12px; align-items: center; color: var(--primary-text-color); font-size: 13px;">
          <span style="font-weight: 500; color: var(--deemphasized-text-color);">Vote Type</span>
          <span style="font-weight: 500; color: var(--deemphasized-text-color);">Runtime</span>
          <span style="font-weight: 500; color: var(--deemphasized-text-color);">AI Prediction</span>

          <span>Approval (+2)</span>
          <gr-vote-chip .vote=${{value: 2, is_ai: false}} .label=${testLabel}></gr-vote-chip>
          <gr-vote-chip .vote=${{value: 2, is_ai: true}} .label=${testLabel}></gr-vote-chip>

          <span>Recommendation (+1)</span>
          <gr-vote-chip .vote=${{value: 1, is_ai: false}} .label=${testLabel}></gr-vote-chip>
          <gr-vote-chip .vote=${{value: 1, is_ai: true}} .label=${testLabel}></gr-vote-chip>

          <span>Rejection (-2)</span>
          <gr-vote-chip .vote=${{value: -2, is_ai: false}} .label=${testLabel}></gr-vote-chip>
          <gr-vote-chip .vote=${{value: -2, is_ai: true}} .label=${testLabel}></gr-vote-chip>
        </div>
      </div>
    `);

    // Wait for all vote chips to finish rendering
    const chips = container.querySelectorAll('gr-vote-chip');
    await Promise.all(Array.from(chips).map(c => (c as any).updateComplete));

    await visualDiff(container, 'gr-vote-chips-ai-comparison');
    await visualDiffDarkTheme(container, 'gr-vote-chips-ai-comparison');
  });
});
