/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GrDateFormatter} from '../../elements/shared/gr-date-formatter/gr-date-formatter';
import '../../test/common-test-setup';
import './chat-history';
import {ChatHistory} from './chat-history';
import sinon from 'sinon';
import {assert, fixture, html} from '@open-wc/testing';
import {Conversation} from '../../api/ai-code-review';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {chatProvider} from '../../test/test-data-generators';

suite('chat-history tests', () => {
  let element: ChatHistory;

  setup(async () => {
    const pluginLoader = testResolver(pluginLoaderToken);
    pluginLoader.pluginsModel.aiCodeReviewRegister({
      pluginName: 'test-plugin',
      provider: chatProvider,
    });

    element = await fixture(html`<chat-history></chat-history>`);
    element.conversations = [];
    await element.updateComplete;
  });

  test('renders empty state', async () => {
    element.conversations = [];
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ '<div>No conversations found.</div>'
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
    element.conversations = conversations;
    await element.updateComplete;

    const cards = element.shadowRoot?.querySelectorAll('.conversation-card');
    assert.equal(cards?.length, 2);

    const firstCard = cards![0];
    const title = firstCard.querySelector(
      '.conversation-content p'
    )?.textContent;
    assert.equal(title, 'Test Conversation 1');

    const timestamp = firstCard.querySelector(
      '.conversation-content p.ts gr-date-formatter'
    ) as GrDateFormatter;
    assert.isOk(timestamp);
    assert.include(timestamp.dateStr, '2024-01-01');
  });

  test('clicking conversation calls loadConversation', async () => {
    const loadConversationStub = sinon.stub(element, 'loadConversation');
    const conversations: Conversation[] = [
      {
        id: '1',
        title: 'Test Conversation 1',
        timestamp_millis: Date.now(),
      },
    ];
    element.conversations = conversations;
    await element.updateComplete;

    const card = element.shadowRoot?.querySelector(
      '.conversation-card'
    ) as HTMLElement;
    assert.isOk(card);
    card.click();
    await element.updateComplete;

    assert.isTrue(loadConversationStub.calledOnce);
    assert.isTrue(loadConversationStub.calledWith(conversations[0]));
  });
});
