/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import './gr-checks-results';
import {html} from 'lit';
import {fixture, assert} from '@open-wc/testing';
import {createCheckFix} from '../../test/test-data-generators';
import {GrChecksFixPreview} from './gr-checks-fix-preview';
import {rectifyFix} from '../../models/checks/checks-util';

suite('gr-checks-fix-preview test', () => {
  let element: GrChecksFixPreview;

  setup(async () => {
    const fix = rectifyFix(createCheckFix(), 'test-checker');
    element = await fixture<GrChecksFixPreview>(
      html`<gr-checks-fix-preview
        .fixSuggestionInfo=${fix}
      ></gr-checks-fix-preview>`
    );
    await element.updateComplete;
  });

  test('renders', async () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="header">
          <div class="title">
            <span> Attached Fix </span>
          </div>
          <div>
            <gr-button
              aria-disabled="true"
              disabled=""
              flatten=""
              role="button"
              secondary=""
              tabindex="-1"
            >
              Show fix side-by-side
            </gr-button>
            <gr-button
              aria-disabled="true"
              disabled=""
              flatten=""
              primary=""
              role="button"
              tabindex="-1"
              title=""
            >
              Apply fix
            </gr-button>
          </div>
        </div>
        <div class="loading">Loading fix preview ...</div>
      `
    );
  });
});
