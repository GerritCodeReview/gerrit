/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import './gr-checks-results';
import {GrChecksResults} from './gr-checks-results';
import {html} from 'lit';
import {assert, fixture} from '@open-wc/testing';
import {checksModelToken} from '../../models/checks/checks-model';
import {
  setAllFakeRuns,
} from '../../models/checks/checks-fakes';
import {resolve} from '../../models/dependency';
import {assertIsDefined, queryAndAssert} from '../../utils/common-util';
import {GrDropdownList} from '../shared/gr-dropdown-list/gr-dropdown-list';


suite('gr-checks-results test', () => {
  let element: GrChecksResults;

  setup(async () => {
    element = await fixture<GrChecksResults>(
      html`<gr-checks-results></gr-checks-results>`
    );
    const getChecksModel = resolve(element, checksModelToken);
    getChecksModel().allRunsSelectedPatchset$.subscribe(
      runs => (element.runs = runs)
    );
    setAllFakeRuns(getChecksModel());
    await element.updateComplete;
  });

  test('attempt dropdown items', async () => {
    const attemptDropdown = queryAndAssert<GrDropdownList>(
      element,
      'gr-dropdown-list'
    );
    assertIsDefined(attemptDropdown.items);
    assert.equal(attemptDropdown.items.length, 42);
    assert.deepEqual(attemptDropdown.items[0], {
      text: 'Latest Attempt',
      value: 'latest',
    });
    assert.deepEqual(attemptDropdown.items[1], {
      text: 'All Attempts',
      value: 'all',
    });
    assert.deepEqual(attemptDropdown.items[2], {
      text: 'Attempt 0',
      value: 0,
    });
    assert.deepEqual(attemptDropdown.items[41], {
      text: 'Attempt 40',
      value: 40,
    });
  });

  test('renders', async () => {
    assert.shadowDom.equal(
      element,
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
                <gr-button link=""> Go To Latest Patchset </gr-button>
              </div>
              <gr-dropdown-list value="latest"> </gr-dropdown-list>
              <gr-dropdown-list value="0"> </gr-dropdown-list>
            </div>
          </div>
          <div class="headerBottomRow">
            <div class="left">
              <div class="filterDiv">
                <input
                  id="filterInput"
                  placeholder="Filter results by tag or regular expression"
                  type="text"
                />
              </div>
            </div>
            <div class="right">
              <gr-tooltip-content
                has-tooltip=""
                position-below=""
                title="Fake Bug Report 1"
              >
                <a
                  href="https://www.google.com"
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  <gr-icon
                    aria-label="Fake Bug Report 1"
                    class="link"
                    filled=""
                    icon="bug_report"
                  >
                  </gr-icon>
                </a>
              </gr-tooltip-content>
              <gr-tooltip-content
                has-tooltip=""
                position-below=""
                title="Fake Link 1"
              >
                <a
                  href="https://www.google.com"
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  <gr-icon
                    aria-label="Fake Link 1"
                    class="link"
                    icon="open_in_new"
                  >
                  </gr-icon>
                </a>
              </gr-tooltip-content>
              <gr-tooltip-content
                has-tooltip=""
                position-below=""
                title="Fake Code Link"
              >
                <a
                  href="https://www.google.com"
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  <gr-icon aria-label="Fake Code Link" class="link" icon="code">
                  </gr-icon>
                </a>
              </gr-tooltip-content>
              <gr-tooltip-content
                has-tooltip=""
                position-below=""
                title="Fake Image Link"
              >
                <a
                  href="https://www.google.com"
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  <gr-icon
                    aria-label="Fake Image Link"
                    class="link"
                    filled=""
                    icon="image"
                  >
                  </gr-icon>
                </a>
              </gr-tooltip-content>
              <div class="space"></div>
              <gr-checks-action context="results"> </gr-checks-action>
              <gr-dropdown
                horizontal-align="right"
                id="moreActions"
                link=""
                vertical-offset="32"
              >
                <gr-icon aria-labelledby="moreMessage" icon="more_vert">
                </gr-icon>
                <span id="moreMessage"> More </span>
              </gr-dropdown>
            </div>
          </div>
        </div>
        <div class="body">
          <div class="error expanded">
            <div class="categoryHeader error">
              <h3 class="heading-3 left">
                <gr-icon class="expandIcon" icon="expand_less"> </gr-icon>
                <gr-tooltip-content
                  has-tooltip=""
                  position-below=""
                  title="Must be fixed and is blocking submit"
                >
                  <div class="statusIconWrapper">
                    <gr-icon class="error statusIcon" filled="" icon="error">
                    </gr-icon>
                    <span class="title"> error </span>
                    <span class="count"> (3) </span>
                  </div>
                </gr-tooltip-content>
              </h3>
              <div class="right">
                <gr-button link=""> Expand All </gr-button>
              </div>
            </div>
            <gr-result-row
              class="FAKEErrorFinderFinderFinderFinderFinderFinderFinder"
            >
            </gr-result-row>
            <gr-result-row
              class="FAKEErrorFinderFinderFinderFinderFinderFinderFinder"
              isexpandable=""
            >
            </gr-result-row>
            <gr-result-row class="FAKESuperCheck" isexpandable="">
            </gr-result-row>
            <table class="resultsTable">
              <thead>
                <tr class="headerRow">
                  <th class="longNames nameCol">Run</th>
                  <th class="summaryCol">Summary</th>
                  <th class="expanderCol"></th>
                </tr>
              </thead>
              <tbody></tbody>
            </table>
          </div>
          <div class="expanded warning">
            <div class="categoryHeader warning">
              <h3 class="heading-3 left">
                <gr-icon class="expandIcon" icon="expand_less"> </gr-icon>
                <gr-tooltip-content
                  has-tooltip=""
                  position-below=""
                  title="Should be checked but is not blocking submit"
                >
                  <div class="statusIconWrapper">
                    <gr-icon
                      class="statusIcon warning"
                      filled=""
                      icon="warning"
                    >
                    </gr-icon>
                    <span class="title"> warning </span>
                    <span class="count"> (1) </span>
                  </div>
                </gr-tooltip-content>
              </h3>
              <div class="right">
                <gr-button link=""> Expand All </gr-button>
              </div>
            </div>
            <gr-result-row class="FAKESuperCheck" isexpandable="">
            </gr-result-row>
            <table class="resultsTable">
              <thead>
                <tr class="headerRow">
                  <th class="nameCol">Run</th>
                  <th class="summaryCol">Summary</th>
                  <th class="expanderCol"></th>
                </tr>
              </thead>
              <tbody></tbody>
            </table>
          </div>
          <div class="collapsed info">
            <div class="categoryHeader info">
              <h3 class="heading-3 left">
                <gr-icon class="expandIcon" icon="expand_more"> </gr-icon>
                <gr-tooltip-content
                  has-tooltip=""
                  position-below=""
                  title="Does not have to be checked, for your information only"
                >
                  <div class="statusIconWrapper">
                    <gr-icon class="info statusIcon" icon="info"> </gr-icon>
                    <span class="title"> info </span>
                    <span class="count"> (3) </span>
                  </div>
                </gr-tooltip-content>
              </h3>
              <div class="right">
                <gr-button hidden="" link=""> Expand All </gr-button>
              </div>
            </div>
          </div>
          <div class="collapsed success">
            <div class="categoryHeader empty success">
              <h3 class="heading-3 left">
                <gr-icon class="expandIcon" icon="expand_more"> </gr-icon>
                <gr-tooltip-content
                  has-tooltip=""
                  position-below=""
                  title="Successful runs without results and individual successful results"
                >
                  <div class="statusIconWrapper">
                    <gr-icon class="statusIcon success" icon="check_circle">
                    </gr-icon>
                    <span class="title"> success </span>
                    <span class="count"> (0) </span>
                  </div>
                </gr-tooltip-content>
              </h3>
              <div class="right">
                <gr-button hidden="" link=""> Expand All </gr-button>
              </div>
            </div>
          </div>
        </div>
      `,
      {
        ignoreAttributes: ['tabindex', 'aria-disabled', 'role'],
      }
    );
  });
});
