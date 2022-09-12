/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {fixture, assert} from '@open-wc/testing';
import {html} from 'lit';
import './gr-account-chip';
import {GrAccountChip} from './gr-account-chip';
import {
  createAccountWithIdNameAndEmail,
  createChange,
} from '../../../test/test-data-generators';

suite('gr-account-chip tests', () => {
  let element: GrAccountChip;
  setup(async () => {
    const reviewer = createAccountWithIdNameAndEmail();
    const change = createChange();
    element = await fixture<GrAccountChip>(html`<gr-account-chip
      .account=${reviewer}
      .change=${change}
    ></gr-account-chip>`);
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="container">
          <div>
            <gr-account-label clickable="" deselected=""></gr-account-label>
          </div>
          <slot name="vote-chip"></slot>
          <gr-button
            aria-disabled="false"
            aria-label="Remove"
            class="remove"
            hidden=""
            id="remove"
            link=""
            role="button"
            tabindex="0"
          >
            <gr-icon icon="close"></gr-icon>
          </gr-button>
        </div>
      `
    );
  });
});
