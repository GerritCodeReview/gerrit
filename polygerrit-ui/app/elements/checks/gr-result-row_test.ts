/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import './gr-result-row';
import {GrResultRow, GrResultExpanded} from './gr-result-row';
import {html} from 'lit';
import {assert, fixture} from '@open-wc/testing';
import {RunResult} from '../../models/checks/checks-model';
import {
  fakeRun0,
  fakeRun1,
} from '../../models/checks/checks-fakes';
import {createLabelInfo} from '../../test/test-data-generators';
import {query, queryAndAssert} from '../../utils/common-util';
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
        <tr class="container collapsed">
        <td class="nameCol">
          <div class="flex">
            <gr-hovercard-run>
            </gr-hovercard-run>
            <div
              class="name"
              role="button"
              tabindex="0"
            >
              FAKE Error Finder Finder Finder Finder Finder Finder Finder
            </div>
            <div class="space">
            </div>
          </div>
        </td>
        <td class="summaryCol">
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
              I would like to point out this error: 1 is not equal to 2!&nbsp;
            </div>
            <div class="message">
            </div>
            <div class="links">
            </div>
            <div class="actions">
              <gr-checks-action
                context="result-row"
                exportparts="button"
              >
              </gr-checks-action>
              <gr-dropdown
                horizontal-align="right"
                id="moreActions"
                link=""
                vertical-offset="32"
              >
                <gr-icon
                  aria-labelledby="moreMessage"
                  icon="more_vert"
                >
                </gr-icon>
                <span id="moreMessage">
                  More
                </span>
              </gr-dropdown>
            </div>
            <div class="tags">
              <gr-tooltip-content
                has-tooltip=""
                position-below=""
                title="A category tag for this check result. Click to filter."
              >
                <button class="tag gray">
                  <span>
                    OBSOLETE
                  </span>
                </button>
              </gr-tooltip-content>
              <gr-tooltip-content
                has-tooltip=""
                position-below=""
                title="A category tag for this check result. Click to filter."
              >
                <button class="tag gray">
                  <span>
                    E2E
                  </span>
                </button>
              </gr-tooltip-content>
            </div>
          </div>
        </td>
        <td class="expanderCol">
          <div
            aria-checked="false"
            aria-label="Expand result row"
            class="show-hide"
            hidden=""
            role="switch"
            tabindex="0"
          >
            <gr-icon icon="expand_more">
            </gr-icon>
          </div>
        </td>
      </tr>
      <tr class="detailsRow collapsed">
        <td
          class="expandedCol"
          colspan="3"
        >
        </td>
      </tr>
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
        <div class="links"></div>
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
        <gr-endpoint-decorator name="check-result-expanded">
          <gr-endpoint-param name="run"> </gr-endpoint-param>
          <gr-endpoint-param name="result"> </gr-endpoint-param>
          <gr-formatted-text class="message" markdown=""> </gr-formatted-text>
        </gr-endpoint-decorator>
        <div class="useful">
          <div class="title">Was this helpful?</div>
          <gr-checks-action
            exportparts="button"
            icon="thumb_up"
          >
          </gr-checks-action>
          <gr-checks-action
            exportparts="button"
            icon="thumb_down"
          >
          </gr-checks-action>
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
          <gr-formatted-text class="message" markdown=""> </gr-formatted-text>
        </gr-endpoint-decorator>
        <gr-checks-fix-preview> </gr-checks-fix-preview>
        <div class="useful">
          <div class.bind="title">Was this helpful?</div>
          <gr-checks-action
            exportparts="button"
            icon="thumb_up"
          >
          </gr-checks-action>
          <gr-checks-action
            exportparts="button"
            icon="thumb_down"
          >
          </gr-checks-action>
        </div>
      `
    );
  });
});
