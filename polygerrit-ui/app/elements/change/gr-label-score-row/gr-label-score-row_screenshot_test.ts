/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-label-score-row';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrLabelScoreRow} from './gr-label-score-row';

suite('gr-label-score-row screenshot tests', () => {
  let element: GrLabelScoreRow;

  setup(async () => {
    element = await fixture<GrLabelScoreRow>(
      html`<gr-label-score-row
        .label=${{name: 'Code-Review', value: '+1'}}
        .labels=${{
          'Code-Review': {
            values: {
              '-2': 'This shall not be merged',
              '-1': 'I would prefer this is not merged as is',
              ' 0': 'No score',
              '+1': 'Looks good to me, but someone else must approve',
              '+2': 'This looks good to me, approved',
            },
          },
        }}
        .permittedLabels=${{
          'Code-Review': ['-2', '-1', ' 0', '+1', '+2'],
        }}
        .orderedLabelValues=${[-2, -1, 0, 1, 2]}
      ></gr-label-score-row>`
    );
    await element.updateComplete;
  });

  test('label score row screenshot', async () => {
    // Create a container with a fixed width to stabilize the component's dimensions.
    const container = document.createElement('div');

    container.style.width = '392px';
    container.style.display = 'inline-block';
    container.appendChild(element);

    document.body.appendChild(container);

    await visualDiff(container, 'gr-label-score-row');

    document.body.removeChild(container);
  });
});
