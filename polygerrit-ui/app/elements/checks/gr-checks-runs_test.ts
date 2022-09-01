/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import './gr-checks-runs';
import {GrChecksRun, GrChecksRuns} from './gr-checks-runs';
import {html} from 'lit';
import {fixture, assert} from '@open-wc/testing';
import {checksModelToken} from '../../models/checks/checks-model';
import {fakeRun0, setAllFakeRuns} from '../../models/checks/checks-fakes';
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
                <gr-icon icon="chevron_left" class="expandIcon"></gr-icon>
              </div>
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
            <gr-icon icon="expand_less" class="expandIcon"></gr-icon>
            <h3 class="heading-3">Running / Scheduled (2)</h3>
          </div>
          <div class="sectionRuns">
            <gr-checks-run></gr-checks-run>
            <gr-checks-run></gr-checks-run>
          </div>
        </div>
        <div class="completed expanded">
          <div class="sectionHeader">
            <gr-icon icon="expand_less" class="expandIcon"></gr-icon>
            <h3 class="heading-3">Completed (3)</h3>
          </div>
          <div class="sectionRuns">
            <gr-checks-run></gr-checks-run>
            <gr-checks-run></gr-checks-run>
            <gr-checks-run></gr-checks-run>
          </div>
        </div>
        <div class="expanded runnable">
          <div class="sectionHeader">
            <gr-icon icon="expand_less" class="expandIcon"></gr-icon>
            <h3 class="heading-3">Not run (1)</h3>
          </div>
          <div class="sectionRuns">
            <gr-checks-run></gr-checks-run>
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
    setAllFakeRuns(getChecksModel());
  });

  test('renders loading', async () => {
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ ' <div class="chip">Loading ...</div> '
    );
  });

  test('renders fakeRun0', async () => {
    element.shouldRender = true;
    element.run = fakeRun0;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="chip error" tabindex="0">
          <div class="left">
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
              <input
                checked=""
                id="attempt-latest"
                name="fakeerrorfinderfinderfinderfinderfinderfinderfinder-attempt-choice"
                type="radio"
              />
              <gr-icon icon=""> </gr-icon>
              <label for="attempt-latest"> Latest Attempt </label>
            </div>
            <div class="attemptDetail">
              <input
                id="attempt-all"
                name="fakeerrorfinderfinderfinderfinderfinderfinderfinder-attempt-choice"
                type="radio"
              />
              <gr-icon icon=""> </gr-icon>
              <label for="attempt-all"> All Attempts </label>
            </div>
          </div>
        </div>
      `
    );
  });
});
