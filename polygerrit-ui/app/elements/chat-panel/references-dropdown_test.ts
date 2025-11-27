/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './references-dropdown';
import {ReferencesDropdown} from './references-dropdown';
import {
  ChatModel,
  chatModelToken,
  Turn,
  UserType,
} from '../../models/chat/chat-model';
import {Reference} from '../../api/ai-code-review';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {chatProvider, createChange} from '../../test/test-data-generators';
import {changeModelToken} from '../../models/change/change-model';
import {ParsedChangeInfo} from '../../types/types';

suite('references-dropdown tests', () => {
  let element: ReferencesDropdown;
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

    element = await fixture<ReferencesDropdown>(
      html`<references-dropdown .turnIndex=${0}></references-dropdown>`
    );
  });

  function createTurn(references: Reference[]): Turn {
    return {
      userMessage: {
        userType: UserType.USER,
        content: 'test',
        contextItems: [],
      },
      geminiMessage: {
        userType: UserType.GEMINI,
        responseParts: [],
        references,
        regenerationIndex: 0,
        citations: [],
      },
    };
  }

  test('is hidden when there are no references', async () => {
    chatModel.updateState({...chatModel.getState(), turns: [createTurn([])]});
    await element.updateComplete;
    assert.shadowDom.equal(element, '');
  });

  test('renders references', async () => {
    const references: Reference[] = [
      {
        type: 'FILE',
        displayText: 'file1.txt',
        externalUrl: 'http://example.com/file1',
      },
      {
        type: 'FILE',
        displayText: 'file2.txt',
        externalUrl: 'http://example.com/file2',
      },
    ];
    chatModel.updateState({
      ...chatModel.getState(),
      turns: [createTurn(references)],
    });
    await element.updateComplete;

    const button = element.shadowRoot?.querySelector(
      '.references-dropdown-button'
    );
    assert.isOk(button);
    assert.dom.equal(
      button,
      `
      <md-text-button
        class="references-dropdown-button"
        value=""
      >
        <md-icon slot="icon" aria-hidden="true">expand_more</md-icon>
        Context used (2)
      </md-text-button>
    `
    );
    (button as HTMLElement).click();
    await element.updateComplete;

    const referenceLinks =
      element.shadowRoot?.querySelectorAll('.reference-button');
    assert.isOk(referenceLinks);
    assert.equal(referenceLinks.length, 2);
    assert.equal(
      (referenceLinks[0] as HTMLAnchorElement).href,
      'http://example.com/file1'
    );
    assert.equal(
      (referenceLinks[1] as HTMLAnchorElement).href,
      'http://example.com/file2'
    );
  });

  test('renders warnings', async () => {
    const references: Reference[] = [
      {
        type: 'FILE',
        displayText: 'file1.txt',
        externalUrl: 'http://example.com/file1',
        errorMsg: 'File not found',
      },
    ];
    chatModel.updateState({
      ...chatModel.getState(),
      turns: [createTurn(references)],
    });
    await element.updateComplete;

    assert.shadowDom.equal(element, '');

    const referencesWithErrors: Reference[] = [
      {
        type: 'FILE',
        displayText: 'file1.txt',
        externalUrl: 'http://example.com/file1',
      },
      {
        type: 'FILE',
        displayText: 'file2.txt',
        externalUrl: 'http://example.com/file2',
        errorMsg: 'File not found',
      },
    ];
    chatModel.updateState({
      ...chatModel.getState(),
      turns: [createTurn(referencesWithErrors)],
    });
    await element.updateComplete;

    const button = element.shadowRoot?.querySelector(
      '.references-dropdown-button'
    );
    assert.isOk(button);
    (button as HTMLElement).click();
    await element.updateComplete;

    const warningButton = element.shadowRoot?.querySelector(
      '.list-warnings-button'
    );
    assert.isOk(warningButton);
    (warningButton as HTMLElement).click();
    await element.updateComplete;

    const warningsList = element.shadowRoot?.querySelector('.warnings-list');
    assert.isOk(warningsList);
    const warningItems = warningsList?.querySelectorAll('li');
    assert.isOk(warningItems);
    assert.equal(warningItems.length, 1);
    assert.equal(
      warningItems[0].textContent?.trim(),
      'Failed to load file2.txt: File not found'
    );
  });
});
