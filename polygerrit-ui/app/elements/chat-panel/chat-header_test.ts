/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './chat-header';
import {ChatHeader} from './chat-header';
import {
  ChatModel,
  chatModelToken,
  ChatPanelMode,
} from '../../models/chat/chat-model';
import sinon from 'sinon';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {changeModelToken} from '../../models/change/change-model';
import {chatProvider, createChange} from '../../test/test-data-generators';
import {ParsedChangeInfo} from '../../types/types';

suite('chat-header tests', () => {
  let element: ChatHeader;
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

    element = await fixture(html`<chat-header></chat-header>`);
    chatModel = testResolver(chatModelToken);
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-icon class="gemini-icon" icon="robot_2"></gr-icon>
        <md-text-button
          id="selectModelTrigger"
          class="select-model-trigger"
          value=""
        >
          <div class="title-group">
            <span class="title">Review Agent</span>
            <div class="subtitle">
              <span class="subtitle-text">Gemini Pro</span>
              <md-icon aria-hidden="true" class="arrow-drop-down"
                >arrow_drop_down</md-icon
              >
            </div>
          </div>
        </md-text-button>
        <md-icon-button
          class="history-button first-right-button"
          data-aria-label="Show history"
          title="Show history"
          value=""
        >
          <md-icon aria-hidden="true">history</md-icon>
        </md-icon-button>
        <md-icon-button
          id="moreActionsTrigger"
          class="more-actions-trigger"
          data-aria-label="More actions"
          title="More"
          value=""
        >
          <md-icon aria-hidden="true">more_vert</md-icon>
        </md-icon-button>
        <md-icon-button
          class="clear-history-button"
          title="Start a new conversation"
          data-aria-label="Start a new conversation"
          value=""
        >
          <md-icon aria-hidden="true">add</md-icon>
        </md-icon-button>
        <md-icon-button
          class="close-button"
          title="Close Review Agent panel"
          data-aria-label="Close Review Agent panel"
          value=""
        >
          <md-icon aria-hidden="true">clear</md-icon>
        </md-icon-button>
        <md-menu
          id="selectModelMenu"
          anchor="selectModelTrigger"
          class="select-model-menu"
          aria-hidden="true"
        >
          <md-menu-item md-menu-item="" tabindex="0">
            <md-icon slot="start" style="visibility:visible;" aria-hidden="true"
              >done</md-icon
            >
            Gemini Pro
          </md-menu-item>
          <md-menu-item md-menu-item="" tabindex="-1">
            <md-icon slot="start" style="visibility:hidden;" aria-hidden="true"
              >done</md-icon
            >
            Gemini Ultra
          </md-menu-item>
        </md-menu>
        <md-menu
          id="moreActionsMenu"
          anchor="moreActionsTrigger"
          class="more-actions-menu"
          menu-corner="start-end"
          anchor-corner="end-end"
          aria-hidden="true"
        >
          <a
            href="http://doc.url"
            target="_blank"
            rel="noopener noreferrer"
            style="text-decoration: none;"
          >
            <md-menu-item md-menu-item="">
              <md-icon slot="start" aria-hidden="true">help_outline</md-icon>
              Documentation
            </md-menu-item>
          </a>
        </md-menu>
      `
    );
  });

  test('renders history mode', async () => {
    chatModel.setMode(ChatPanelMode.HISTORY);
    await element.updateComplete;

    const backButton = element.shadowRoot?.querySelector('.back-arrow');
    assert.isOk(backButton);
    const title = element.shadowRoot?.querySelector('.title');
    assert.equal(title?.textContent?.trim(), 'History');
  });

  test('handles switching model', async () => {
    const menuItems = element.shadowRoot?.querySelectorAll(
      '#selectModelMenu md-menu-item'
    );
    assert.equal(menuItems?.length, 2);
    assert.equal(element.selectedModel?.model_id, 'gemini-pro');
    (menuItems![1] as HTMLElement).click();
    await element.updateComplete;
    assert.equal(element.selectedModel?.model_id, 'gemini-ultra');
  });

  test('handles show history', async () => {
    assert.equal(element.mode, ChatPanelMode.CONVERSATION);
    const historyButton = element.shadowRoot?.querySelector(
      '.history-button'
    ) as HTMLElement;
    historyButton.click();
    await element.updateComplete;
    assert.equal(element.mode, ChatPanelMode.HISTORY);
  });

  test('handles back to chat', async () => {
    chatModel.setMode(ChatPanelMode.HISTORY);
    await element.updateComplete;
    assert.equal(element.mode, ChatPanelMode.HISTORY);
    const backButton = element.shadowRoot?.querySelector(
      '.back-arrow'
    ) as HTMLElement;
    backButton.click();
    await element.updateComplete;
    assert.equal(element.mode, ChatPanelMode.CONVERSATION);
  });

  test('handles start new conversation', async () => {
    const addButton = element.shadowRoot?.querySelector(
      '.clear-history-button'
    ) as HTMLElement;
    addButton.click();
    await element.updateComplete;

    assert.equal(element.mode, ChatPanelMode.CONVERSATION);
    assert.equal(chatModel.getState().turns.length, 0);
  });

  test('handles close panel', async () => {
    const spy = sinon.spy();
    element.addEventListener('close-chat-panel', spy);
    const closeButton = element.shadowRoot?.querySelector(
      '.close-button'
    ) as HTMLElement;
    closeButton.click();
    assert.isTrue(spy.called);
  });
});
