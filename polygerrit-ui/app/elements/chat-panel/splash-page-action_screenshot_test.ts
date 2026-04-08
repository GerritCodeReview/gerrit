/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
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

  test('card rendering', async () => {
    const action: Action = {
      id: 'test-action',
      display_text: 'Test Action',
      initial_user_prompt: 'Test prompt',
    };
    element.action = action;
    await element.updateComplete;

    await visualDiff(element, 'splash-page-action-card');
    await visualDiffDarkTheme(element, 'splash-page-action-card');
  });

  test('details modal rendering', async () => {
    const action: Action = {
      id: 'test-action',
      display_text: 'Test Action',
      initial_user_prompt: 'Test prompt',
    };
    element.action = action;
    await element.updateComplete;

    // Trigger the modal to open
    const infoButton = element.shadowRoot?.querySelector(
      '.info-button'
    ) as HTMLElement;
    assert.isOk(infoButton);
    infoButton.click();
    await element.updateComplete;

    const modal = element.shadowRoot?.querySelector(
      '#detailsModal'
    ) as HTMLElement;
    assert.isOk(modal);

    await visualDiff(modal, 'splash-page-action-details-modal');
    await visualDiffDarkTheme(modal, 'splash-page-action-details-modal');
  });

  test('details modal rendering with long instructions', async () => {
    const action: Action = {
      id: 'test-action',
      display_text: 'Test Action',
      initial_user_prompt:
        'This is a very long text that should be long enough to trigger the expansion logic in the details card. It needs to be more than 100px tall in the rendered UI. So I am adding more text here to make sure it overflows. Let us see if this is enough. If not I will add even more text. Is this enough now? I hope so! Let us add one more sentence just to be absolutely sure that it is long enough. ' +
        'Adding even more text to guarantee overflow. This should definitely push it over the 100px limit. We want to be absolutely sure that the expand button appears in the test environment. Let us keep adding text until we are certain. Is this enough? It should be! Let us add another paragraph just in case.',
    };
    element.action = action;
    await element.updateComplete;

    // Trigger the modal to open
    const infoButton = element.shadowRoot?.querySelector(
      '.info-button'
    ) as HTMLElement;
    assert.isOk(infoButton);
    infoButton.click();

    // Wait for the modal to open and layout to complete
    await new Promise(resolve => setTimeout(resolve, 200));
    await element.updateComplete;

    const modal = element.shadowRoot?.querySelector(
      '#detailsModal'
    ) as HTMLElement;
    assert.isOk(modal);

    await visualDiff(modal, 'splash-page-action-details-modal-long');
    await visualDiffDarkTheme(modal, 'splash-page-action-details-modal-long');

    // Click "Show more"
    const expandButton = modal.querySelector('.expand-button') as HTMLElement;
    assert.isOk(expandButton);
    assert.equal(expandButton.innerText.trim(), 'Show more');
    expandButton.click();
    await element.updateComplete;

    await visualDiff(modal, 'splash-page-action-details-modal-expanded');
    await visualDiffDarkTheme(
      modal,
      'splash-page-action-details-modal-expanded'
    );
  });
});
