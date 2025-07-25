/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import './gr-checks-results';
import {
  GrChecksResults,
  GrResultExpanded,
  GrResultRow,
} from './gr-checks-results';
import {html} from 'lit';
import {assert, fixture} from '@open-wc/testing';
import {checksModelToken, RunResult} from '../../models/checks/checks-model';
import {
  fakeRun0,
  fakeRun1,
  setAllFakeRuns,
} from '../../models/checks/checks-fakes';
import {resolve} from '../../models/dependency';
import {createLabelInfo} from '../../test/test-data-generators';
import {assertIsDefined, query, queryAndAssert} from '../../utils/common-util';
import {PatchSetNumber} from '../../api/rest-api';
import {GrDropdownList} from '../shared/gr-dropdown-list/gr-dropdown-list';

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
    assert.dom.equal(
      labelDiv,
      /* HTML */ `
        <div class="approved label">
          <span> test-label +1 </span>
        </div>
      `
    );
  });

  test('renders', async () => {
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="flex">
          <gr-hovercard-run> </gr-hovercard-run>
          <div class="name" role="button" tabindex="0">
            FAKE Error Finder Finder Finder Finder Finder Finder Finder
          </div>
          <div class="space"></div>
        </div>
        <div class="summary-cell">
          <gr-tooltip-content
            has-tooltip=""
            position-below=""
            title="Link to details"
          >
            <a
              class="link"
              href="https://www.google.com"
              rel="noopener noreferrer"
              target="_blank"
            >
              <gr-icon
                aria-label="external link to details"
                class="link"
                icon="open_in_new"
              >
              </gr-icon>
            </a>
          </gr-tooltip-content>
          <div
            class="summary"
            title="I would like to point out this error: 1 is not equal to 2!"
          >
            I would like to point out this error: 1 is not equal to 2!
          </div>
          <div class="message"></div>
          <div class="tags">
            <gr-tooltip-content
              has-tooltip=""
              position-below=""
              title="A category tag for this check result. Click to filter."
            >
              <button class="tag">
                <span> OBSOLETE </span>
              </button>
            </gr-tooltip-content>
            <gr-tooltip-content
              has-tooltip=""
              position-below=""
              title="A category tag for this check result. Click to filter."
            >
              <button class="tag">
                <span> E2E </span>
              </button>
            </gr-tooltip-content>
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
          <gr-icon icon="expand_more"> </gr-icon>
        </div>
      `
    );
  });

  test('click summary, toggle expand', async () => {
    element.isExpandable = true;
    await element.updateComplete;
    assert.isFalse(element.isExpanded);

    const summaryDiv: HTMLElement =
      element.shadowRoot!.querySelector('.summary')!;
    summaryDiv.click();
    await element.updateComplete;
    assert.isTrue(element.isExpanded);

    summaryDiv.click();
    await element.updateComplete;
    assert.isFalse(element.isExpanded);
  });
});

suite('gr-result-expanded test', () => {
  let element: GrResultExpanded;

  setup(async () => {
    element = await fixture<GrResultExpanded>(
      html`<gr-result-expanded></gr-result-expanded>`
    );
    await element.updateComplete;
  });

  test('renders fake result 1 of run 0', async () => {
    element.result = {...fakeRun0, ...fakeRun0.results![1]} as RunResult;
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="links">
          <a
            href="https://google.com"
            rel="noopener noreferrer"
            target="_blank"
          >
            <gr-icon class="link" icon="download"> </gr-icon>
            <span> Download </span>
          </a>
          <a
            href="https://google.com"
            rel="noopener noreferrer"
            target="_blank"
          >
            <gr-icon class="link" icon="system_update"> </gr-icon>
            <span> Download </span>
          </a>
          <a
            href="https://google.com"
            rel="noopener noreferrer"
            target="_blank"
          >
            <gr-icon class="link" filled="" icon="image"> </gr-icon>
            <span> Link to image </span>
          </a>
          <a
            href="https://google.com"
            rel="noopener noreferrer"
            target="_blank"
          >
            <gr-icon class="link" filled="" icon="image"> </gr-icon>
            <span> Link to image </span>
          </a>
          <a
            href="https://google.com"
            rel="noopener noreferrer"
            target="_blank"
          >
            <gr-icon class="link" filled="" icon="bug_report"> </gr-icon>
            <span> Link for reporting a problem </span>
          </a>
          <a
            href="https://google.com"
            rel="noopener noreferrer"
            target="_blank"
          >
            <gr-icon class="link" icon="help"> </gr-icon>
            <span> Link to help page </span>
          </a>
          <a
            href="https://google.com"
            rel="noopener noreferrer"
            target="_blank"
          >
            <gr-icon class="link" icon="history"> </gr-icon>
            <span> Link to result history </span>
          </a>
        </div>
        <div class="links">
          <a
            href="https://google.com"
            rel="noopener noreferrer"
            target="_blank"
          >
            <gr-icon class="link" icon="open_in_new"> </gr-icon>
            <span> Link to details </span>
          </a>
          <a
            href="https://google.com"
            rel="noopener noreferrer"
            target="_blank"
          >
            <gr-icon class="link" filled="" icon="image"> </gr-icon>
            <span> Link to image </span>
          </a>
        </div>
        <gr-endpoint-decorator name="check-result-expanded">
          <gr-endpoint-param name="run"> </gr-endpoint-param>
          <gr-endpoint-param name="result"> </gr-endpoint-param>
          <gr-formatted-text class="message"> </gr-formatted-text>
        </gr-endpoint-decorator>
        <div class="useful">
          <div class="title">Was this helpful?</div>
          <gr-checks-action icon="thumb_up"> </gr-checks-action>
          <gr-checks-action icon="thumb_down"> </gr-checks-action>
        </div>
      `
    );
  });

  test('renders fake result 2 of run 1', async () => {
    element.result = {...fakeRun1, ...fakeRun1.results![2]} as RunResult;
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="links"></div>
        <gr-endpoint-decorator name="check-result-expanded">
          <gr-endpoint-param name="run"> </gr-endpoint-param>
          <gr-endpoint-param name="result"> </gr-endpoint-param>
          <gr-formatted-text class="message"> </gr-formatted-text>
        </gr-endpoint-decorator>
        <gr-checks-fix-preview> </gr-checks-fix-preview>
        <div class="useful">
          <div class="title">Was this helpful?</div>
          <gr-checks-action icon="thumb_up"> </gr-checks-action>
          <gr-checks-action icon="thumb_down"> </gr-checks-action>
        </div>
      `
    );
  });
});

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
