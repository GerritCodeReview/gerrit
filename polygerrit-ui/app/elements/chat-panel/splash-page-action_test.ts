/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './splash-page-action';
import {SplashPageAction} from './splash-page-action';
import {ChatModel, chatModelToken} from '../../models/chat/chat-model';
import {Action} from '../../api/ai-code-review';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {
  chatActions,
  chatProvider,
  createChange,
} from '../../test/test-data-generators';
import {changeModelToken} from '../../models/change/change-model';
import {ParsedChangeInfo} from '../../types/types';

suite('splash-page-action tests', () => {
  let element: SplashPageAction;
  let chatModel: ChatModel;

  setup(async () => {
    const pluginLoader = testResolver(pluginLoaderToken);
    pluginLoader.pluginsModel.aiCodeReviewRegister({
      pluginName: 'test-plugin',
      provider: chatProvider,
    });

    const changeModel = testResolver(changeModelToken);
    changeModel.updateState({
      change: createChange() as ParsedChangeInfo,
    });

    chatModel = testResolver(chatModelToken);

    element = await fixture<SplashPageAction>(
      html`<splash-page-action></splash-page-action>`
    );
  });

  test('renders with action', async () => {
    const action: Action = {
      id: 'test-action',
      display_text: 'Test Action',
      hover_text: 'Test hover',
      subtext: 'Test subtext',
      icon: 'test-icon',
    };
    element.action = action;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <md-assist-chip class="action-chip" title="Test hover">
          <div class="chip-content">
            <gr-icon class="action-icon" icon="test-icon"></gr-icon>
            <div class="action-text-container">
              <div class="main-action-text-container has-subtext">
                <span class="action-text">Test Action</span>
                <gr-tooltip-content has-tooltip="" title="Capability details">
                  <gr-button
                    aria-disabled="false"
                    class="info-button"
                    flatten=""
                    role="button"
                    tabindex="0"
                  >
                    <gr-icon icon="info"></gr-icon>
                  </gr-button>
                </gr-tooltip-content>
              </div>
              <span class="action-subtext">Test subtext</span>
            </div>
          </div>
        </md-assist-chip>
      `
    );
  });

  test('handles click with predefined prompt', async () => {
    // Summarize action
    element.action = chatActions.actions[1];
    await element.updateComplete;

    const chip = element.shadowRoot?.querySelector('md-assist-chip');
    assert.isOk(chip);
    chip.click();

    const turns = chatModel.getState().turns;
    assert.lengthOf(turns, 1);
    assert.equal(turns[0].userMessage.content, 'Summarize the change');
  });

  test('handles click with user input', async () => {
    // Freeform action
    element.action = chatActions.actions[0];
    await element.updateComplete;

    const chip = element.shadowRoot?.querySelector('md-assist-chip');
    assert.isOk(chip);
    chip.click();

    const turns = chatModel.getState().turns;
    assert.lengthOf(turns, 0);
  });
});
