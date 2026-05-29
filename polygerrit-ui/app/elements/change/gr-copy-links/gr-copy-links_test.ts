/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './gr-copy-links';
import {GrCopyLinks} from './gr-copy-links';
import {pressKey, queryAndAssert, waitUntil} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrCopyClipboard} from '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {MdMenu} from '@material/web/menu/menu';

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
    await element.updateComplete;
    // md-menu requires anchor to be set
    // so we create a dummy element to allow
    // us to open it.
    const button = document.createElement('button');
    const mdMenu = element.shadowRoot?.querySelector('md-menu');
    mdMenu!.anchorElement = button;
    element.openDropdown();
    await waitUntil(() => element.isDropdownOpen);
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `<md-menu default-focus="none" open="" quick="" tabindex="-1">
        <div class="dropdown-content">
          <div class="copy-link-row">
            <gr-copy-clipboard
              id="Change_ID-field-copy-clipboard"
              label="Change ID"
              nowrap=""
              shortcut="l - d"
              text="123456"
            >
            </gr-copy-clipboard>
          </div>
        </div>
      </md-menu>`
    );
  });

  test('click writes to clipboard', () => {
    const clipboardStub = sinon.stub(navigator.clipboard, 'writeText');
    const copyClipboard = queryAndAssert<GrCopyClipboard>(
      element,
      'gr-copy-clipboard'
    );
    const copyBtn = queryAndAssert<GrButton>(copyClipboard, '.copyToClipboard');
    copyBtn.click();
    assert.isTrue(clipboardStub.called);
    assert.isTrue(clipboardStub.calledWith('123456'));
  });

  test('shorcuts writes to clipboard', () => {
    const clipboardStub = sinon.stub(window.navigator.clipboard, 'writeText');
    const mdMenu = queryAndAssert<MdMenu>(element, 'md-menu');
    pressKey(mdMenu, 'd');
    assert.isTrue(clipboardStub.called);
    assert.isTrue(clipboardStub.calledWith('123456'));
  });
});
