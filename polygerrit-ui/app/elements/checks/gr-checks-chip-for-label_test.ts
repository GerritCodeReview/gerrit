/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {Category} from '../../api/checks';
import '../../test/common-test-setup';
import {createCheckResult, createRun} from '../../test/test-data-generators';
import {queryAndAssert} from '../../utils/common-util';
import {GrChecksChip} from '../change/gr-change-summary/gr-checks-chip';
import './gr-checks-chip-for-label';
import {GrChecksChipForLabel} from './gr-checks-chip-for-label';
import {fixture, assert} from '@open-wc/testing';
import {html} from 'lit';

suite('gr-checks-chip-for-label test', () => {
  let element: GrChecksChipForLabel;

  setup(async () => {
    element = await fixture<GrChecksChipForLabel>(
      html`<gr-checks-chip-for-label></gr-checks-chip-for-label>`
    );
    element.runs = [
      createRun({
        labelName: 'Verified',

        results: [
          createCheckResult({
            category: Category.ERROR,
          }),
        ],
      }),
    ];
    await element.updateComplete;

    element.labels = ['Verified'];
    await element.updateComplete;
  });

  test('renders loading', async () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ '<gr-checks-chip></gr-checks-chip>'
    );

    const checksChip = queryAndAssert<GrChecksChip>(element, 'gr-checks-chip');
    assert.shadowDom.equal(
      checksChip,
      /* HTML */ `<div
        aria-label="1 error result"
        class="checksChip error font-small"
        role="link"
        tabindex="0"
      >
        <gr-icon filled="" icon="error"> </gr-icon>
        <div class="text">1</div>
      </div>`
    );
  });
});
