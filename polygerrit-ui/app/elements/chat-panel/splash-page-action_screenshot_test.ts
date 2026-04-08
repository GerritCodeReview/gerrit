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
      custom_action_source: {
        custom_action_id: '//depot/google3/some/file.ts',
      },
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
        'This is a long instruction text. We want to test that it collapses properly and can be expanded. ' +
        'By forcing the state in the test, we guarantee that the button appears regardless of font rendering differences.',
      custom_action_source: {
        custom_action_id: '//depot/google3/some/file.ts',
      },
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

    // Force the showExpandButton state to true to make the test robust
    (element as unknown as {showExpandButton: boolean}).showExpandButton = true;
    await element.updateComplete;

    await visualDiff(modal, 'splash-page-action-details-modal-long');
    await visualDiffDarkTheme(modal, 'splash-page-action-details-modal-long');

    // Force the isInstructionExpanded state to true to test expanded rendering
    (
      element as unknown as {isInstructionExpanded: boolean}
    ).isInstructionExpanded = true;
    await element.updateComplete;

    await visualDiff(modal, 'splash-page-action-details-modal-expanded');
    await visualDiffDarkTheme(
      modal,
      'splash-page-action-details-modal-expanded'
    );
  });
});
