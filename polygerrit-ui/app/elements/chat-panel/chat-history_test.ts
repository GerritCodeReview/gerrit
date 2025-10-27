/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {ChatHistory} from './chat-history';
import './chat-history';
import {Conversation} from '../../api/ai-code-review';
import sinon from 'sinon';
import {customElement} from 'lit/decorators.js';

const fakeChatModel = {
  conversations$: {
    subscribe: (callback: (conversations: Conversation[]) => void) => {
      callback([]);
      return {unsubscribe: () => {}};
    },
  },
  loadConversation: sinon.stub(),
};

@customElement('test-chat-history')
class TestChatHistory extends ChatHistory {
  constructor() {
    super();
    (this as any).getChatModel = () => fakeChatModel;
  }
}

suite('chat-history tests', () => {
  let element: TestChatHistory;

  setup(async () => {
    fakeChatModel.loadConversation.resetHistory();
    element = await fixture(html`<test-chat-history></test-chat-history>`);
  });

  test('renders empty state', async () => {
    (element as any).conversations = [];
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `<div>No conversations found.</div>`
    );
  });

  test('renders conversations', async () => {
    const date = new Date('2024-01-01T12:00:00Z');
    const conversations: Conversation[] = [
      {
        id: '1',
        title: 'Test Conversation 1',
        timestamp_millis: date.getTime(),
      },
      {
        id: '2',
        title: 'Test Conversation 2',
        timestamp_millis: date.getTime(),
      },
    ];
    (element as any).conversations = conversations;
    await element.updateComplete;

    const cards = element.shadowRoot?.querySelectorAll('.conversation-card');
    assert.equal(cards?.length, 2);

    const firstCard = cards![0];
    const title = firstCard.querySelector('.conversation-content p')?.textContent;
    assert.equal(title, 'Test Conversation 1');
    
    const timestamp = firstCard.querySelector('.conversation-content p.ts')?.textContent;
    assert.include(timestamp?.trim(), '2024-01-01');
  });

  test('clicking conversation calls loadConversation', async () => {
    const conversations: Conversation[] = [
      {
        id: '1',
        title: 'Test Conversation 1',
        timestamp_millis: Date.now(),
      },
    ];
    (element as any).conversations = conversations;
    await element.updateComplete;

    const card = element.shadowRoot?.querySelector('.conversation-card') as HTMLElement;
    assert.isOk(card);
    card.click();

    assert.isTrue(fakeChatModel.loadConversation.calledOnce);
    assert.isTrue(fakeChatModel.loadConversation.calledWith('1'));
  });
});

declare global {
  interface HTMLElementTagNameMap {
    'test-chat-history': TestChatHistory;
  }
}
