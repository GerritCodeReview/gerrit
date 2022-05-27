/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import './gr-checks-runs';
import {GrChecksRuns} from './gr-checks-runs';
import {html} from 'lit';
import {fixture} from '@open-wc/testing-helpers';
import {checksModelToken} from '../../models/checks/checks-model';
import {setAllFakeRuns} from '../../models/checks/checks-fakes';
import {resolve} from '../../models/dependency';

suite('gr-checks-runs test', () => {
  let element: GrChecksRuns;

  setup(async () => {
    element = await fixture<GrChecksRuns>(
      html`<gr-checks-runs></gr-checks-runs>`
    );
    const getChecksModel = resolve(element, checksModelToken);
    setAllFakeRuns(getChecksModel());
  });

  test('tabState filter', async () => {
    element.tabState = {filter: 'fff'};
    await element.updateComplete;
    assert.equal(element.filterRegExp?.source, 'fff');
  });

  test('renders', async () => {
    await element.updateComplete;
    assert.equal(element.runs.length, 44);
    expect(element).shadowDom.to.equal(
      /* HTML */ `
        <h2 class="title">
          <div class="heading-2">Runs</div>
          <div class="flex-space"></div>
          <gr-tooltip-content has-tooltip="" title="Collapse runs panel">
            <gr-button
              aria-checked="false"
              aria-label="Collapse runs panel"
              class="expandButton"
              link=""
              role="switch"
            >
              <iron-icon
                class="expandIcon"
                icon="gr-icons:chevron-left"
              ></iron-icon>
            </gr-button>
          </gr-tooltip-content>
        </h2>
        <input
          id="filterInput"
          placeholder="Filter runs by regular expression"
          type="text"
        />
        <div class="expanded running">
          <div class="sectionHeader">
            <iron-icon
              class="expandIcon"
              icon="gr-icons:expand-less"
            ></iron-icon>
            <h3 class="heading-3">Running / Scheduled</h3>
          </div>
          <div class="sectionRuns">
            <gr-checks-run></gr-checks-run>
            <gr-checks-run></gr-checks-run>
          </div>
        </div>
        <div class="completed expanded">
          <div class="sectionHeader">
            <iron-icon
              class="expandIcon"
              icon="gr-icons:expand-less"
            ></iron-icon>
            <h3 class="heading-3">Completed</h3>
          </div>
          <div class="sectionRuns">
            <gr-checks-run></gr-checks-run>
            <gr-checks-run></gr-checks-run>
            <gr-checks-run></gr-checks-run>
          </div>
        </div>
        <div class="expanded runnable">
          <div class="sectionHeader">
            <iron-icon
              class="expandIcon"
              icon="gr-icons:expand-less"
            ></iron-icon>
            <h3 class="heading-3">Not run</h3>
          </div>
          <div class="sectionRuns">
            <gr-checks-run></gr-checks-run>
          </div>
        </div>
      `,
      {ignoreAttributes: ['tabindex', 'aria-disabled']}
    );
  });
});
