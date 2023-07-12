/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import './gr-hovercard-run';
import {fixture, html, assert} from '@open-wc/testing';
import {GrHovercardRun} from './gr-hovercard-run';
import {fakeRun4Att, fakeRun4_4} from '../../models/checks/checks-fakes';
import {createAttemptMap, worstCategory} from '../../models/checks/checks-util';
import {CheckRun} from '../../models/checks/checks-model';

suite('gr-hovercard-run tests', () => {
  let element: GrHovercardRun;

  setup(async () => {
    const fakeNow = new Date('Sep 26 2022 12:00:00');
    sinon.useFakeTimers(fakeNow);
    element = await fixture<GrHovercardRun>(html`
      <gr-hovercard-run class="hovered"></gr-hovercard-run>
    `);
  });

  teardown(() => {
    element.mouseHide(new MouseEvent('click'));
  });

  test('render fakeRun4', async () => {
    const attemptMap = createAttemptMap(fakeRun4Att);
    const attemptDetails = attemptMap.get(fakeRun4_4.checkName)!.attempts;
    const run: CheckRun = {...fakeRun4_4, attemptDetails};
    element.run = run;
    element.worstCategory = worstCategory(run);
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div id="container" role="tooltip" tabindex="-1">
          <div class="section">
            <div class="chipRow">
              <div class="chip">
                <gr-icon icon="check"></gr-icon>
                <span> COMPLETED </span>
              </div>
            </div>
          </div>
          <div class="section">
            <div class="sectionIcon">
              <gr-icon class="info" icon="info"></gr-icon>
            </div>
            <div class="sectionContent">
              <h3 class="heading-3 name">
                <span> FAKE Elimination Long Long Long Long Long </span>
              </h3>
            </div>
          </div>
          <div class="section">
            <div class="sectionIcon">
              <gr-icon class="small" icon="info"> </gr-icon>
            </div>
            <div class="sectionContent">
              <div class="row">
                <div class="title">Status</div>
                <div>
                  <a
                    href="https://www.google.com"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <gr-icon
                      aria-label="external link to check status"
                      class="link small"
                      icon="open_in_new"
                    >
                    </gr-icon>
                    www.google.com
                  </a>
                </div>
              </div>
              <div class="row">
                <div class="title">Message</div>
                <div>Everything was eliminated already.</div>
              </div>
            </div>
          </div>
          <div class="section">
            <div class="sectionIcon">
              <gr-icon class="small" icon="arrow_forward"> </gr-icon>
            </div>
            <div class="sectionContent">
              <div class="attempts row">
                <div class="title">Attempt</div>
                <div>
                  <div class="attemptIcon">
                    <gr-icon class="more_horiz" icon="more_horiz"> </gr-icon>
                  </div>
                  <div class="attemptNumber"></div>
                </div>
                <div>
                  <div class="attemptIcon">
                    <gr-icon class="error" filled="" icon="error"> </gr-icon>
                  </div>
                  <div class="attemptNumber">34th</div>
                </div>
                <div>
                  <div class="attemptIcon">
                    <gr-icon class="check_circle" icon="check_circle">
                    </gr-icon>
                  </div>
                  <div class="attemptNumber">35th</div>
                </div>
                <div>
                  <div class="attemptIcon">
                    <gr-icon class="error" filled="" icon="error"> </gr-icon>
                  </div>
                  <div class="attemptNumber">36th</div>
                </div>
                <div>
                  <div class="attemptIcon">
                    <gr-icon class="check_circle" icon="check_circle">
                    </gr-icon>
                  </div>
                  <div class="attemptNumber">37th</div>
                </div>
                <div>
                  <div class="attemptIcon">
                    <gr-icon class="error" filled="" icon="error"> </gr-icon>
                  </div>
                  <div class="attemptNumber">38th</div>
                </div>
                <div>
                  <div class="attemptIcon">
                    <gr-icon class="check_circle" icon="check_circle">
                    </gr-icon>
                  </div>
                  <div class="attemptNumber">39th</div>
                </div>
                <div>
                  <div class="attemptIcon">
                    <gr-icon class="info" icon="info"> </gr-icon>
                  </div>
                  <div class="attemptNumber">40th</div>
                </div>
              </div>
            </div>
          </div>
          <div class="section">
            <div class="sectionIcon">
              <gr-icon class="small" icon="schedule"> </gr-icon>
            </div>
            <div class="sectionContent">
              <div class="row">
                <div class="title">Started</div>
                <div>1 year 6 m ago</div>
              </div>
              <div class="row">
                <div class="title">Ended</div>
                <div>1 year 6 m ago</div>
              </div>
              <div class="row">
                <div class="title">Completion</div>
                <div>1 minute</div>
              </div>
            </div>
          </div>
          <div class="section">
            <div class="sectionIcon">
              <gr-icon class="small" icon="link"> </gr-icon>
            </div>
            <div class="sectionContent">
              <div class="row">
                <div class="title">Description</div>
                <div>Shows you the possible eliminations.</div>
              </div>
              <div class="row">
                <div class="title">Documentation</div>
                <div>
                  <a
                    href="https://www.google.com"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <gr-icon
                      aria-label="external link to check documentation"
                      class="link small"
                      icon="open_in_new"
                    >
                    </gr-icon>
                    www.google.com
                  </a>
                </div>
              </div>
            </div>
          </div>
          <div class="action">
            <gr-checks-action context="hovercard"> </gr-checks-action>
          </div>
        </div>
      `
    );
  });
});
