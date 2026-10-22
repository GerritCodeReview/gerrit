/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-vote-chip';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {createApproval} from '../../../test/test-data-generators';
import {DetailedLabelInfo} from '../../../api/rest-api';

const labelInfo: DetailedLabelInfo = {
  values: {
    '-2': 'Do not submit',
    '-1': 'I would prefer that you did not submit this',
    ' 0': 'No score',
    '+1': 'Looks good to me, but someone else must approve',
    '+2': 'Looks good to me, approved',
  },
};

suite('gr-vote-chip screenshot tests', () => {
  test('single AI vote chips', async () => {
    const container = await fixture<HTMLDivElement>(html`
      <div
        style="display: inline-flex; gap: 8px; padding: 8px; background-color: var(--view-background-color);"
      >
        <gr-vote-chip
          .label=${labelInfo}
          .vote=${{...createApproval(), value: 2}}
          is-ai
        ></gr-vote-chip>
        <gr-vote-chip
          .label=${labelInfo}
          .vote=${{...createApproval(), value: 1}}
          is-ai
        ></gr-vote-chip>
        <gr-vote-chip
          .label=${labelInfo}
          .vote=${{...createApproval(), value: -1}}
          is-ai
        ></gr-vote-chip>
        <gr-vote-chip
          .label=${labelInfo}
          .vote=${{...createApproval(), value: -2}}
          is-ai
        ></gr-vote-chip>
      </div>
    `);

    await visualDiff(container, 'gr-vote-chip-ai-single');
    await visualDiffDarkTheme(container, 'gr-vote-chip-ai-single');
  });

  test('stacked AI vote chips', async () => {
    const container = await fixture<HTMLDivElement>(html`
      <div
        style="display: inline-flex; gap: 8px; padding: 8px; background-color: var(--view-background-color);"
      >
        <gr-vote-chip
          .label=${labelInfo}
          .vote=${{...createApproval(), value: 2}}
          is-ai
          more
        ></gr-vote-chip>
        <gr-vote-chip
          .label=${labelInfo}
          .vote=${{...createApproval(), value: 1}}
          is-ai
          more
        ></gr-vote-chip>
        <gr-vote-chip
          .label=${labelInfo}
          .vote=${{...createApproval(), value: -1}}
          is-ai
          more
        ></gr-vote-chip>
        <gr-vote-chip
          .label=${labelInfo}
          .vote=${{...createApproval(), value: -2}}
          is-ai
          more
        ></gr-vote-chip>
      </div>
    `);

    await visualDiff(container, 'gr-vote-chip-ai-stacked');
    await visualDiffDarkTheme(container, 'gr-vote-chip-ai-stacked');
  });

  test('circle-shape AI vote chips', async () => {
    const container = await fixture<HTMLDivElement>(html`
      <div
        style="display: inline-flex; gap: 8px; padding: 8px; background-color: var(--view-background-color);"
      >
        <gr-vote-chip
          .label=${labelInfo}
          .vote=${{...createApproval(), value: 2}}
          circle-shape
          is-ai
        ></gr-vote-chip>
        <gr-vote-chip
          .label=${labelInfo}
          .vote=${{...createApproval(), value: 1}}
          circle-shape
          is-ai
        ></gr-vote-chip>
        <gr-vote-chip
          .label=${labelInfo}
          .vote=${{...createApproval(), value: -1}}
          circle-shape
          is-ai
        ></gr-vote-chip>
        <gr-vote-chip
          .label=${labelInfo}
          .vote=${{...createApproval(), value: -2}}
          circle-shape
          is-ai
        ></gr-vote-chip>
      </div>
    `);

    await visualDiff(container, 'gr-vote-chip-ai-circle');
    await visualDiffDarkTheme(container, 'gr-vote-chip-ai-circle');
  });
});
