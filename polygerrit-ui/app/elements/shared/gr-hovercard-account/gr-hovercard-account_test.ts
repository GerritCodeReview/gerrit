/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture} from '@open-wc/testing';
import {html} from 'lit';
import './gr-hovercard-account';
import {GrHovercardAccount} from './gr-hovercard-account';
import {queryAndAssert} from '../../../test/test-utils';
import {
  createAccountDetailWithId,
  createChange,
} from '../../../test/test-data-generators';
import {GrButton} from '../gr-button/gr-button';
import {GrHovercardAccountContents} from './gr-hovercard-account-contents';
import {userModelToken} from '../../../models/user/user-model';
import {testResolver} from '../../../test/common-test-setup';

suite('gr-hovercard-account tests', () => {
  let element: GrHovercardAccount;
  let contents: GrHovercardAccountContents;

  setup(async () => {
    const account = createAccountDetailWithId(31);
    element = await fixture<GrHovercardAccount>(
      html`<gr-hovercard-account
        class="hovered"
        .account=${account}
        .change=${createChange()}
        .highlightAttention=${true}
      >
      </gr-hovercard-account>`
    );
    await element.show({});
    testResolver(userModelToken).setAccount({...account});
    await element.updateComplete;
    contents = queryAndAssert(element, 'gr-hovercard-account-contents');
  });

  teardown(async () => {
    element.mouseHide(new MouseEvent('click'));
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div id="container" role="tooltip" tabindex="-1">
          <gr-hovercard-account-contents></gr-hovercard-account-contents>
        </div>
      `
    );
  });

  test('hides when links are clicked', () => {
    const changesLink = queryAndAssert<HTMLAnchorElement>(contents, 'a');
    // Actually redirecting will break the test, replace URL with no-op
    changesLink.href = 'javascript:';

    assert.isTrue(element._isShowing);

    changesLink.click();

    assert.isFalse(element._isShowing);
  });

  test('hides when actions are performed', () => {
    assert.isTrue(element._isShowing);

    queryAndAssert<GrButton>(contents, 'gr-button.addToAttentionSet').click();

    assert.isFalse(element._isShowing);
  });
});
