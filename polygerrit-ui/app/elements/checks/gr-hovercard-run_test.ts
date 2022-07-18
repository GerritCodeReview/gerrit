/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import './gr-hovercard-run';
import {fixture, html} from '@open-wc/testing-helpers';
import {GrHovercardRun} from './gr-hovercard-run';
import {fakeRun0} from '../../models/checks/checks-fakes';

suite('gr-hovercard-run tests', () => {
  let element: GrHovercardRun;

  setup(async () => {
    element = await fixture<GrHovercardRun>(html`
      <gr-hovercard-run class="hovered" .run=${fakeRun0}></gr-hovercard-run>
    `);
  });

  teardown(() => {
    element.mouseHide(new MouseEvent('click'));
  });

  test('render', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div id="container" role="tooltip" tabindex="-1">
        <div class="section">
          <div class="chipRow">
            <div class="chip">
              <span class="material-icon">check</span>
              <span> COMPLETED </span>
            </div>
          </div>
        </div>
        <div class="section">
          <div class="sectionIcon">
            <span class="material-icon filled error">error</span>
          </div>
          <div class="sectionContent">
            <h3 class="heading-3 name">
              <span>
                FAKE Error Finder Finder Finder Finder Finder Finder Finder
              </span>
            </h3>
          </div>
        </div>
      </div>
    `);
  });

  test('hovercard is shown with error icon', () => {
    assert.equal(element.computeIcon().name, 'error');
  });
});
