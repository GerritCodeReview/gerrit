/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {
  ChatModel,
  chatModelToken,
  GeminiMessage,
  Turn,
  UserType,
} from '../../models/chat/chat-model';
import './citations-box';
import {CitationsBox} from './citations-box';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {changeModelToken} from '../../models/change/change-model';
import {chatProvider, createChange} from '../../test/test-data-generators';
import {ParsedChangeInfo} from '../../types/types';

suite('citations-box tests', () => {
  let element: CitationsBox;
  let chatModel: ChatModel;

  function createTurn(citations: string[]): Turn {
    return {
      userMessage: {
        userType: UserType.USER,
        content: 'test',
        contextItems: [],
      },
      geminiMessage: {
        userType: UserType.GEMINI,
        responseParts: [],
        regenerationIndex: 0,
        references: [],
        citations,
      } as GeminiMessage,
    };
  }

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

    element = await fixture(html`<citations-box></citations-box>`);
    chatModel = testResolver(chatModelToken);
    await element.updateComplete;
  });

  test('renders nothing when no citations', async () => {
    chatModel.updateState({
      ...chatModel.getState(),
      turns: [createTurn([])],
    });
    await element.updateComplete;

    assert.shadowDom.equal(element, '');
  });

  test('renders nothing when no citation_url', async () => {
    chatModel.updateState({
      ...chatModel.getState(),
      models: undefined,
      turns: [createTurn(['http://example.com/1'])],
    });
    await element.updateComplete;

    assert.shadowDom.equal(element, '');
  });

  test('renders with one citation', async () => {
    const citations = ['http://example.com/1'];
    chatModel.updateState({
      ...chatModel.getState(),
      turns: [createTurn(citations)],
    });
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
        <div class="citations-display-box">
          <p class="citations-summary-message">
            Use
            <a
              href="http://citation.url"
              target="_blank"
              rel="noopener noreferrer"
            >
              with caution</a
            >
            . The model answer includes 1 citation
          from other sources:
          </p>
          <ul class="citation-entry-list">
            <li class="citation-item">
              <a
                href="http://example.com/1"
                target="_blank"
                rel="noopener noreferrer"
                >http://example.com/1</a
              >
            </li>
          </ul>
        </div>
      `
    );
  });

  test('renders with multiple citations', async () => {
    const citations = ['http://example.com/1', 'http://example.com/2'];
    chatModel.updateState({
      ...chatModel.getState(),
      turns: [createTurn(citations)],
    });
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* prettier-ignore */ /* HTML */ `
        <div class="citations-display-box">
          <p class="citations-summary-message">
            Use
            <a
              href="http://citation.url"
              target="_blank"
              rel="noopener noreferrer"
            >
              with caution</a
            >
            . The model answer includes 2 citations
          from other sources:
          </p>
          <ul class="citation-entry-list">
            <li class="citation-item">
              <a
                href="http://example.com/1"
                target="_blank"
                rel="noopener noreferrer"
                >http://example.com/1</a
              >
            </li>
            <li class="citation-item">
              <a
                href="http://example.com/2"
                target="_blank"
                rel="noopener noreferrer"
                >http://example.com/2</a
              >
            </li>
          </ul>
        </div>
      `
    );
  });

  test('renders citations for the correct turnIndex', async () => {
    const turn0 = createTurn(['http://example.com/0']);
    const turn1 = createTurn(['http://example.com/1', 'http://example.com/2']);
    chatModel.updateState({
      ...chatModel.getState(),
      turns: [turn0, turn1],
    });
    element.turnIndex = 1;
    await element.updateComplete;

    const summary = element.shadowRoot?.querySelector(
      '.citations-summary-message'
    );
    assert.isOk(summary);
    assert.include(summary.textContent!, '2 citations');

    const items = element.shadowRoot?.querySelectorAll('.citation-item');
    assert.isOk(items);
    assert.equal(items.length, 2);
    assert.equal(items[0].querySelector('a')?.href, 'http://example.com/1');
    assert.equal(items[1].querySelector('a')?.href, 'http://example.com/2');
  });
});
