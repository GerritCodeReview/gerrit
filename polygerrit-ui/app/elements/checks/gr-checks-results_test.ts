/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import './gr-checks-results';
import {GrChecksResults, GrResultRow} from './gr-checks-results';
import {html} from 'lit';
import {fixture} from '@open-wc/testing-helpers';
import {checksModelToken} from '../../models/checks/checks-model';
import {fakeRun0, setAllFakeRuns} from '../../models/checks/checks-fakes';
import {resolve} from '../../models/dependency';
import {createLabelInfo} from '../../test/test-data-generators';
import {queryAndAssert, query} from '../../utils/common-util';
import {PatchSetNumber} from '../../api/rest-api';

suite('gr-result-row test', () => {
  let element: GrResultRow;

  setup(async () => {
    const result = {...fakeRun0, ...fakeRun0.results![0]};
    element = await fixture<GrResultRow>(
      html`<gr-result-row .result=${result}></gr-result-row>`
    );
    element.shouldRender = true;
  });

  test('renders label association', async () => {
    element.result = {...element.result!, labelName: 'test-label', patchset: 1};
    element.labels = {'test-label': createLabelInfo()};

    // don't show when patchset does not match latest
    element.latestPatchNum = 2 as PatchSetNumber;
    await element.updateComplete;
    let labelDiv = query(element, '.label');
    assert.isNotOk(labelDiv);

    element.latestPatchNum = 1 as PatchSetNumber;
    await element.updateComplete;
    labelDiv = queryAndAssert(element, '.label');
    expect(labelDiv).dom.to.equal(/* HTML */ `
      <div class="approved label">
        <span> test-label +1 </span>
        <paper-tooltip
          fittovisiblebounds=""
          offset="5"
          role="tooltip"
          tabindex="-1"
        >
          The check result has (probably) influenced this label vote.
        </paper-tooltip>
      </div>
    `);
  });

  test('renders', async () => {
    await element.updateComplete;
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div class="flex">
        <gr-hovercard-run> </gr-hovercard-run>
        <div class="name" role="button" tabindex="0">
          FAKE Error Finder Finder Finder Finder Finder Finder Finder
        </div>
        <div class="space"></div>
      </div>
        <div class="summary-cell">
          <a class="link" href="https://www.google.com" target="_blank">
            <iron-icon
              aria-label="external link to details"
              class="link"
              icon="gr-icons:launch"
            >
            </iron-icon>
            <paper-tooltip offset="5" role="tooltip" tabindex="-1">
              Link to details
            </paper-tooltip>
          </a>
          <div
            class="summary"
            title="I would like to point out this error: 1 is not equal to 2!"
          >
            I would like to point out this error: 1 is not equal to 2!
          </div>
          <div class="message"></div>
          <div class="tags">
            <button class="tag">
              <span> OBSOLETE </span>
              <paper-tooltip
                fittovisiblebounds=""
                offset="5"
                role="tooltip"
                tabindex="-1"
              >
                A category tag for this check result. Click to filter.
              </paper-tooltip>
            </button>
            <button class="tag">
              <span> E2E </span>
              <paper-tooltip
                fittovisiblebounds=""
                offset="5"
                role="tooltip"
                tabindex="-1"
              >
                A category tag for this check result. Click to filter.
              </paper-tooltip>
            </button>
          </div>
        </div>
        <div
          aria-checked="false"
          aria-label="Expand result row"
          class="show-hide"
          hidden=""
          role="switch"
          tabindex="0"
        >
          <iron-icon icon="gr-icons:expand-more"> </iron-icon>
        </div>
      </div>
    `);
  });
});

suite('gr-checks-results test', () => {
  let element: GrChecksResults;

  setup(async () => {
    element = await fixture<GrChecksResults>(
      html`<gr-checks-results></gr-checks-results>`
    );
    const getChecksModel = resolve(element, checksModelToken);
    setAllFakeRuns(getChecksModel());
  });

  test('renders', async () => {
    await element.updateComplete;
    expect(element).shadowDom.to.equal(
      /* HTML */ `
        <div class="header">
          <div class="headerTopRow">
            <div class="left">
              <h2 class="heading-2">Results</h2>
              <div class="loading" hidden="">
                <span> Loading results </span>
                <span class="loadingSpin"> </span>
              </div>
            </div>
            <div class="right">
              <div class="goToLatest">
                <gr-button link=""> Go to latest patchset </gr-button>
              </div>
              <gr-dropdown-list value="0"> </gr-dropdown-list>
            </div>
          </div>
          <div class="headerBottomRow">
            <div class="left"></div>
            <div class="right">
              <a href="https://www.google.com" target="_blank">
                <iron-icon
                  aria-label="Fake Bug Report 1"
                  class="link"
                  icon="gr-icons:bug"
                >
                </iron-icon>
                <paper-tooltip offset="5"> </paper-tooltip>
              </a>
              <a href="https://www.google.com" target="_blank">
                <iron-icon
                  aria-label="Fake Link 1"
                  class="link"
                  icon="gr-icons:launch"
                >
                </iron-icon>
                <paper-tooltip offset="5"> </paper-tooltip>
              </a>
              <a href="https://www.google.com" target="_blank">
                <iron-icon
                  aria-label="Fake Code Link"
                  class="link"
                  icon="gr-icons:code"
                >
                </iron-icon>
                <paper-tooltip offset="5"> </paper-tooltip>
              </a>
              <a href="https://www.google.com" target="_blank">
                <iron-icon
                  aria-label="Fake Image Link"
                  class="link"
                  icon="gr-icons:insert-photo"
                >
                </iron-icon>
                <paper-tooltip offset="5"> </paper-tooltip>
              </a>
              <div class="space"></div>
              <gr-checks-action context="results"> </gr-checks-action>
              <gr-dropdown
                horizontal-align="right"
                id="moreActions"
                link=""
                vertical-offset="32"
              >
                <iron-icon
                  aria-labelledby="moreMessage"
                  icon="gr-icons:more-vert"
                >
                </iron-icon>
                <span id="moreMessage"> More </span>
              </gr-dropdown>
            </div>
          </div>
        </div>
        <div class="body">
          <div class="collapsed">
            <h3 class="categoryHeader empty error heading-3">
              <iron-icon class="expandIcon" icon="gr-icons:expand-more">
              </iron-icon>
              <div class="statusIconWrapper">
                <iron-icon class="error statusIcon" icon="gr-icons:error">
                </iron-icon>
                <span class="title"> error </span>
                <span class="count"> (0) </span>
                <paper-tooltip offset="5"> </paper-tooltip>
              </div>
            </h3>
          </div>
          <div class="collapsed">
            <h3 class="categoryHeader empty heading-3 warning">
              <iron-icon class="expandIcon" icon="gr-icons:expand-more">
              </iron-icon>
              <div class="statusIconWrapper">
                <iron-icon class="statusIcon warning" icon="gr-icons:warning">
                </iron-icon>
                <span class="title"> warning </span>
                <span class="count"> (0) </span>
                <paper-tooltip offset="5"> </paper-tooltip>
              </div>
            </h3>
          </div>
          <div class="collapsed">
            <h3 class="categoryHeader empty heading-3 info">
              <iron-icon class="expandIcon" icon="gr-icons:expand-more">
              </iron-icon>
              <div class="statusIconWrapper">
                <iron-icon class="info statusIcon" icon="gr-icons:info-outline">
                </iron-icon>
                <span class="title"> info </span>
                <span class="count"> (0) </span>
                <paper-tooltip offset="5"> </paper-tooltip>
              </div>
            </h3>
          </div>
          <div class="collapsed">
            <h3 class="categoryHeader empty heading-3 success">
              <iron-icon class="expandIcon" icon="gr-icons:expand-more">
              </iron-icon>
              <div class="statusIconWrapper">
                <iron-icon
                  class="statusIcon success"
                  icon="gr-icons:check-circle-outline"
                >
                </iron-icon>
                <span class="title"> success </span>
                <span class="count"> (0) </span>
                <paper-tooltip offset="5"> </paper-tooltip>
              </div>
            </h3>
          </div>
        </div>
      `,
      {
        ignoreChildren: ['paper-tooltip'],
        ignoreAttributes: ['tabindex', 'aria-disabled', 'role'],
      }
    );
  });
});
