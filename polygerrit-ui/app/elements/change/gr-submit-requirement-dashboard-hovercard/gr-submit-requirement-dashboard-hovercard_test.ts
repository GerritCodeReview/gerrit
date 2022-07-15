/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {fixture} from '@open-wc/testing-helpers';
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
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div id="container" role="tooltip" tabindex="-1">
        <gr-submit-requirements
          disable-endpoints=""
          disable-hovercards=""
          suppress-title=""
        >
        </gr-submit-requirements>
      </div>
    `);
  });
});
