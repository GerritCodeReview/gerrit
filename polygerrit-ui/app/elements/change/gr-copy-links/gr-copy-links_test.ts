/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {fixture} from '@open-wc/testing';
import {html} from 'lit';
import './gr-copy-links';
import {GrCopyLinks} from './gr-copy-links';

suite('gr-copy-links tests', () => {
  let element: GrCopyLinks;
  setup(async () => {
    const links = [
      {
        label: 'Change ID',
        shortcut: 'd',
        value: '123456',
      },
    ];
    element = await fixture<GrCopyLinks>(
      html`<gr-copy-links .copyLinks=${links}></gr-copy-links>`
    );
    element.isDropdownOpen = true;
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `<iron-dropdown>
      <div slot="dropdown-content">
          <div class="copy-link-row">
            <label for="Change_ID-field">Change ID</label>
            <input
              class="input"
              id="Change_ID-field"
              readonly=""
              type="text"
            >
            <span class="shortcut">l - d</span>
            <gr-copy-clipboard hideinput="" text="123456">
            </gr-copy-clipboard>
          </div>
      </iron-dropdown>`
    );
  });
});
