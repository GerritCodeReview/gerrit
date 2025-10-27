/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {ChatHeader} from './chat-header';
import {ChatPanelMode} from '../../models/chat/chat-model';
import {ModelInfo} from '../../api/ai-code-review';
import {of, BehaviorSubject} from 'rxjs';
import sinon from 'sinon';
import {customElement} from 'lit/decorators.js';

const availableModels: ModelInfo[] = [
  {
    model_id: 'gemini-pro',
    short_text: 'Gemini Pro',
    full_display_text: 'Gemini Pro',
  },
  {
    model_id: 'gemini-ultra',
    short_text: 'Gemini Ultra',
    full_display_text: 'Gemini Ultra',
  },
];

const fakeChatModel = {
  availableModelsMap$: of(new Map(availableModels.map(m => [m.model_id, m]))),
  selectedModel$: new BehaviorSubject<ModelInfo | undefined>(
    availableModels[0]
  ),
  models$: of({documentation_url: 'http://doc.url'}),
  mode$: new BehaviorSubject<ChatPanelMode>(ChatPanelMode.CONVERSATION),
  selectModel: sinon.stub(),
  setMode: sinon.stub(),
  startEmptyNewChat: sinon.stub(),
};

@customElement('test-chat-header')
class TestChatHeader extends ChatHeader {
  constructor() {
    super();
    (this as any).getChatModel = () => fakeChatModel;
  }
}

suite('chat-header tests', () => {
  let element: TestChatHeader;

  setup(async () => {
    element = await fixture(html`<test-chat-header></test-chat-header>`);
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-icon class="gemini-icon" icon="star_shine"></gr-icon>
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
          title="Close AI Chat panel"
          data-aria-label="Close AI Chat panel"
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
    fakeChatModel.mode$.next(ChatPanelMode.HISTORY);
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
    (menuItems![1] as HTMLElement).click();
    assert.isTrue(fakeChatModel.selectModel.calledWith('gemini-ultra'));
  });

  test('handles show history', async () => {
    const historyButton = element.shadowRoot?.querySelector(
      '.history-button'
    ) as HTMLElement;
    historyButton.click();
    assert.isTrue(fakeChatModel.setMode.calledWith(ChatPanelMode.HISTORY));
  });

  test('handles back to chat', async () => {
    fakeChatModel.mode$.next(ChatPanelMode.HISTORY);
    await element.updateComplete;
    const backButton = element.shadowRoot?.querySelector(
      '.back-arrow'
    ) as HTMLElement;
    backButton.click();
    assert.isTrue(fakeChatModel.setMode.calledWith(ChatPanelMode.CONVERSATION));
  });

  test('handles start new conversation', async () => {
    const addButton = element.shadowRoot?.querySelector(
      '.clear-history-button'
    ) as HTMLElement;
    addButton.click();
    assert.isTrue(fakeChatModel.setMode.calledWith(ChatPanelMode.CONVERSATION));
    assert.isTrue(fakeChatModel.startEmptyNewChat.calledWith(true));
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

declare global {
  interface HTMLElementTagNameMap {
    'test-chat-header': TestChatHeader;
  }
}
