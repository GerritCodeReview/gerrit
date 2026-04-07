/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {fixture, html, assert} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import './splash-page-action';
import {SplashPageAction} from './splash-page-action';
import {Action} from '../../api/ai-code-review';
import {visualDiffDarkTheme} from '../../test/test-utils';

suite('splash-page-action screenshot tests', () => {
  let element: SplashPageAction;

  setup(async () => {
    element = await fixture(html`<splash-page-action></splash-page-action>`);
    await element.updateComplete;
  });

  test('modal rendering', async () => {
    const action: Action = {
      id: 'test-action',
      display_text: 'Test Action',
      initial_user_prompt: 'Test prompt',
    };
    element.action = action;
    await element.updateComplete;

    // Trigger the modal to open
    const infoButton = element.shadowRoot?.querySelector('.info-button') as HTMLElement;
    assert.isOk(infoButton);
    infoButton.click();
    await element.updateComplete;

    // Wait for the modal to be visible (it uses showModal())
    // We might need to wait a bit if there is any animation, but usually it's instant in tests.
    
    await visualDiff(element, 'splash-page-action-modal');
    await visualDiffDarkTheme(element, 'splash-page-action-modal');
  });
});
