/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import './gr-checks-runs';
import {GrChecksRun, GrChecksRuns} from './gr-checks-runs';
import {html} from 'lit';
import {assert, fixture} from '@open-wc/testing';
import {checksModelToken} from '../../models/checks/checks-model';
import {checkRun0, setAllcheckRuns} from '../../test/test-data-generators';
import {resolve} from '../../models/dependency';
import {queryAll} from '../../utils/common-util';

suite('gr-checks-runs test', () => {
  let element: GrChecksRuns;

  setup(async () => {
    element = await fixture<GrChecksRuns>(
      html`<gr-checks-runs></gr-checks-runs>`
    );
    const getChecksModel = resolve(element, checksModelToken);
    setAllcheckRuns(getChecksModel());
    element.errorMessages = {'test-plugin-name': 'test-error-message'};
    await element.updateComplete;
  });

  test('filterRegExp', async () => {
    // Without a filter all 6 fake runs (0-5) will be rendered.
    assert.equal(queryAll(element, 'gr-checks-run').length, 6);

    // This filter will only match checkRun2 (checkName: 'FAKE Mega Analysis').
    element.filterRegExp = 'Mega';
    await element.updateComplete;
    assert.equal(queryAll(element, 'gr-checks-run').length, 1);
  });

  test('renders', async () => {
    assert.equal(element.runs.length, 44);
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <h2 class="title">
          <div class="heading-2">Runs</div>
          <div class="flex-space"></div>
          <gr-tooltip-content has-tooltip="" title="Collapse runs panel">
            <gr-button
              aria-checked="false"
              aria-label="Collapse runs panel"
              class="expandButton font-normal"
              link=""
              role="switch"
            >
              <div>
                <gr-icon class="expandIcon" icon="chevron_left"> </gr-icon>
              </div>
            </gr-button>
          </gr-tooltip-content>
        </h2>
        <div class="error">
          <div class="left">
            <gr-icon filled="" icon="error"> </gr-icon>
          </div>
          <div class="right">
            <div class="message">
              Error while fetching results for test-plugin-name:
              <br />
              test-error-message
            </div>
          </div>
        </div>
        <input
          id="filterInput"
          placeholder="Filter runs by regular expression"
          type="text"
        />
        <div class="expanded running">
          <div class="sectionHeader">
            <gr-icon class="expandIcon" icon="expand_less"> </gr-icon>
            <h3 class="heading-3">Running / Scheduled (2)</h3>
          </div>
          <div class="sectionRuns">
            <gr-checks-run> </gr-checks-run>
            <gr-checks-run> </gr-checks-run>
          </div>
        </div>
        <div class="completed expanded">
          <div class="sectionHeader">
            <gr-icon class="expandIcon" icon="expand_less"> </gr-icon>
            <h3 class="heading-3">Completed (3)</h3>
          </div>
          <div class="sectionRuns">
            <gr-checks-run> </gr-checks-run>
            <gr-checks-run> </gr-checks-run>
            <gr-checks-run> </gr-checks-run>
          </div>
        </div>
        <div class="expanded runnable">
          <div class="sectionHeader">
            <gr-icon class="expandIcon" icon="expand_less"> </gr-icon>
            <h3 class="heading-3">Not run (1)</h3>
          </div>
          <div class="sectionRuns">
            <gr-checks-run> </gr-checks-run>
          </div>
        </div>
      `,
      {ignoreAttributes: ['tabindex', 'aria-disabled']}
    );
  });

  test('renders collapsed', async () => {
    element.collapsed = true;
    await element.updateComplete;
    assert.equal(element.runs.length, 44);
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <h2 class="title">
          <div class="heading-2">Runs</div>
          <div class="flex-space"></div>
          <gr-tooltip-content has-tooltip="" title="Expand runs panel">
            <gr-button
              aria-checked="true"
              aria-label="Expand runs panel"
              class="expandButton font-normal"
              link=""
              role="switch"
            >
              <div>
                <gr-icon icon="chevron_right" class="expandIcon"></gr-icon>
              </div>
            </gr-button>
          </gr-tooltip-content>
        </h2>
        <div class="error">
          <div class="left">
            <gr-icon filled="" icon="error"> </gr-icon>
          </div>
          <div class="right">
            <div class="message">Error</div>
          </div>
        </div>
        <input
          hidden
          id="filterInput"
          placeholder="Filter runs by regular expression"
          type="text"
        />
        <div class="expanded running">
          <div class="sectionHeader">
            <h3 class="heading-3">Running / Scheduled</h3>
          </div>
          <div class="sectionRuns">
            <gr-checks-run condensed></gr-checks-run>
            <gr-checks-run condensed></gr-checks-run>
          </div>
        </div>
        <div class="completed expanded">
          <div class="sectionHeader">
            <h3 class="heading-3">Completed</h3>
          </div>
          <div class="sectionRuns">
            <gr-checks-run condensed></gr-checks-run>
            <gr-checks-run condensed></gr-checks-run>
            <gr-checks-run condensed></gr-checks-run>
          </div>
        </div>
        <div class="expanded runnable">
          <div class="sectionHeader">
            <h3 class="heading-3">Not run</h3>
          </div>
          <div class="sectionRuns">
            <gr-checks-run condensed></gr-checks-run>
          </div>
        </div>
      `,
      {ignoreAttributes: ['tabindex', 'aria-disabled']}
    );
  });
});

suite('gr-checks-run test', () => {
  let element: GrChecksRun;

  setup(async () => {
    element = await fixture<GrChecksRun>(html`<gr-checks-run></gr-checks-run>`);
    const getChecksModel = resolve(element, checksModelToken);
    setAllcheckRuns(getChecksModel());
    await element.updateComplete;
  });

  test('renders loading', async () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ ' <div class="chip">Loading ...</div> '
    );
  });

  test('renders checkRun0', async () => {
    element.shouldRender = true;
    element.run = checkRun0;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="chip error" tabindex="0">
          <div class="left" tabindex="0">
            <gr-hovercard-run> </gr-hovercard-run>
            <gr-icon class="error" filled="" icon="error"> </gr-icon>
            <span class="name">
              FAKE Error Finder Finder Finder Finder Finder Finder Finder
            </span>
          </div>
          <div class="middle">
            <gr-checks-attempt> </gr-checks-attempt>
          </div>
          <div class="right"></div>
        </div>
        <div class="attemptDetails" hidden="">
          <div class="attemptDetail">
            <md-radio
              checked=""
              id="attempt-latest"
              name="fakeerrorfinderfinderfinderfinderfinderfinderfinder-attempt-choice"
              tabindex="0"
            >
            </md-radio>
            <gr-icon icon=""> </gr-icon>
            <label for="attempt-latest"> Latest Attempt </label>
          </div>
          <div class="attemptDetail">
            <md-radio
              id="attempt-all"
              name="fakeerrorfinderfinderfinderfinderfinderfinderfinder-attempt-choice"
              tabindex="-1"
            >
            </md-radio>
            <gr-icon icon=""> </gr-icon>
            <label for="attempt-all"> All Attempts </label>
          </div>
        </div>
      `
    );
  });
});
