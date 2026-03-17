/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-label-scores';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrLabelScores} from './gr-label-scores';
import {setViewport} from '@web/test-runner-commands';
import {
  createAccountWithId,
  createChange,
} from '../../../test/test-data-generators';
import {
  SubmitRequirementExpressionInfoStatus,
  SubmitRequirementStatus,
} from '../../../api/rest-api';

suite('gr-label-scores screenshot tests', () => {
  // Exactly 50 characters
  const longLabelName = 'Long-Label-Name-With-Exactly-Fifty-Characters-Here';

  test('submit requirements and trigger votes with long label name', async () => {
    const element = await fixture<GrLabelScores>(
      html`<gr-label-scores></gr-label-scores>`
    );

    // Code-Review is tied to a submit requirement → rendered under
    // "Submit requirements votes". The long label is not referenced in any
    // submit requirement → rendered under "Trigger Votes".
    element.change = {
      ...createChange(),
      labels: {
        'Code-Review': {
          values: {
            '-2': 'This shall not be merged',
            '-1': 'I would prefer this is not merged as is',
            ' 0': 'No score',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'This looks good to me, approved',
          },
          default_value: 0,
        },
        [longLabelName]: {
          values: {
            '-1': 'Bad',
            ' 0': 'No score',
            '+1': 'Good',
          },
          default_value: 0,
        },
      },
      submit_requirements: [
        {
          name: 'Code-Review',
          status: SubmitRequirementStatus.UNSATISFIED,
          submittability_expression_result: {
            expression: 'label:Code-Review=MAX',
            fulfilled: false,
            status: SubmitRequirementExpressionInfoStatus.FAIL,
          },
        },
      ],
    };
    element.account = createAccountWithId(1);
    element.permittedLabels = {
      'Code-Review': ['-2', '-1', ' 0', '+1', '+2'],
      [longLabelName]: ['-1', ' 0', '+1'],
    };
    await element.updateComplete;

    await setViewport({width: 1200, height: 800});

    const container = document.createElement('div');
    container.style.width = '700px';
    container.style.display = 'inline-block';
    container.appendChild(element);
    document.body.appendChild(container);

    await visualDiff(container, 'gr-label-scores-long-trigger-vote-label');

    document.body.removeChild(container);
  });
});
