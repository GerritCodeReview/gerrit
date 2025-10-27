/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './chat-panel';
import {ChatPanel} from './chat-panel';
import {
  ChatModel,
  chatModelToken,
  ChatPanelMode,
  Turn,
} from '../../models/chat/chat-model';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {changeModelToken} from '../../models/change/change-model';
import {chatProvider, createChange} from '../../test/test-data-generators';
import {ParsedChangeInfo} from '../../types/types';

suite('chat-panel tests', () => {
  let element: ChatPanel;
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

    element = await fixture(html`<chat-panel></chat-panel>`);
    await element.updateComplete;
  });

  test('renders', async () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="chat-panel-container">
          <chat-header></chat-header>
          <splash-page></splash-page>
          <div class="prompt-section">
            <prompt-box></prompt-box>
            <div class="ai-policy">
              Review agent may display inaccurate info.
              <a href="http://privacy.url" target="_blank">
                AI privacy policy
              </a>
            </div>
          </div>
        </div>
      `
    );
  });

  test('renders history mode', async () => {
    chatModel.setMode(ChatPanelMode.HISTORY);
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="chat-panel-container">
          <chat-header></chat-header>
          <chat-history></chat-history>
        </div>
      `
    );
  });

  test('renders chat mode', async () => {
    chatModel.updateState({
      ...chatModel.getState(),
      turns: [
        {
          userMessage: {
            content: 'hello',
            userType: 0, // UserType.USER
            contextItems: [],
          },
          geminiMessage: {
            responseParts: [],
            regenerationIndex: 0,
            references: [],
            citations: [],
            userType: 1, // UserType.GEMINI
          },
        },
      ] as Turn[],
    });

    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="chat-panel-container">
          <chat-header></chat-header>
          <div class="messages-container" id="scrollableDiv">
            <user-message></user-message>
            <gemini-message
              class="latest"
              style="min-height: 0px"
            ></gemini-message>
          </div>
          <div class="prompt-section">
            <prompt-box></prompt-box>
            <div class="ai-policy">
              Review agent may display inaccurate info.
              <a href="http://privacy.url" target="_blank">
                AI privacy policy
              </a>
            </div>
          </div>
        </div>
      `
    );
  });

  test('renders privacy policy if url is present', async () => {
    const policy = element.shadowRoot!.querySelector('.ai-policy');
    assert.isOk(policy);
    assert.include(
      policy.textContent!,
      'Review agent may display inaccurate info'
    );
    const link = policy.querySelector('a');
    assert.isOk(link);
    assert.equal(link.getAttribute('href'), 'http://privacy.url');
  });
});
