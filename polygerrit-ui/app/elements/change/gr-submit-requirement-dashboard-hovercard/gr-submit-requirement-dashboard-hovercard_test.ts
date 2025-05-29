/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture} from '@open-wc/testing';
import {html} from 'lit';
import './gr-submit-requirement-dashboard-hovercard';
import {GrSubmitRequirementDashboardHovercard} from './gr-submit-requirement-dashboard-hovercard';
import {createParsedChange} from '../../../test/test-data-generators';

suite('gr-submit-requirement-dashboard-hovercard tests', () => {
  let element: GrSubmitRequirementDashboardHovercard;
  setup(async () => {
    element = await fixture<GrSubmitRequirementDashboardHovercard>(
      html`<gr-submit-requirement-dashboard-hovercard
        .change=${createParsedChange()}
      ></gr-submit-requirement-dashboard-hovercard>`
    );
    await element.updateComplete;
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div id="container" role="tooltip" tabindex="-1">
          <gr-submit-requirements
            disable-endpoints=""
            disable-hovercards=""
            suppress-title=""
          >
          </gr-submit-requirements>
        </div>
      `
    );
  });
});
