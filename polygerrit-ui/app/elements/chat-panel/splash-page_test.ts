/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import './splash-page';
import {SplashPage} from './splash-page';
import {
  ChatModel,
  chatModelToken,
  Turn,
  UserType,
} from '../../models/chat/chat-model';
import {Action} from '../../api/ai-code-review';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../shared/gr-js-api-interface/gr-plugin-loader';
import {chatProvider, createChange} from '../../test/test-data-generators';
import {changeModelToken} from '../../models/change/change-model';
import {ParsedChangeInfo} from '../../types/types';
import {userModelToken} from '../../models/user/user-model';
import {AccountDetailInfo} from '../../types/common';
import {UserModel} from '../../models/user/user-model';

suite('splash-page tests', () => {
  let element: SplashPage;
  let chatModel: ChatModel;
  let userModel: UserModel;

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
    userModel = testResolver(userModelToken);

    element = await fixture<SplashPage>(html`<splash-page></splash-page>`);
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="splash-container">
          <h1 class="splash-greeting">Hello,</h1>
          <p class="splash-question">How can I help you today?</p>
          <div class="action-container-title suggested-actions-title">
            Capabilities
          </div>
          <md-chip-set class="action-container">
            <splash-page-action></splash-page-action>
          </md-chip-set>
        </div>
      `
    );
  });

  test('displays user name', async () => {
    userModel.updateState({
      account: {display_name: 'Test User'} as AccountDetailInfo,
    });
    await element.updateComplete;
    const greeting = element.shadowRoot!.querySelector('.splash-greeting');
    assert.dom.equal(
      greeting,
      '<h1 class="splash-greeting">Hello, Test User</h1>'
    );
  });

  test('renders actions', async () => {
    const actions: Action[] = [
      {id: 'action1', display_text: 'Action 1', enable_splash_page_card: true},
      {id: 'action2', display_text: 'Action 2', enable_splash_page_card: true},
    ];
    chatModel.updateState({
      ...chatModel.getState(),
      actions: {actions, default_action_id: 'action1'},
    });
    await element.updateComplete;
    const actionElements =
      element.shadowRoot!.querySelectorAll('splash-page-action');
    assert.lengthOf(actionElements, 2);
  });

  test('renders background request', async () => {
    const turns: Turn[] = [
      {
        userMessage: {
          userType: UserType.USER,
          content: 'Test background request',
          isBackgroundRequest: true,
          contextItems: [],
        },
        geminiMessage: {
          userType: UserType.GEMINI,
          responseParts: [],
          regenerationIndex: 0,
          responseComplete: false,
          references: [],
          citations: [],
        },
      },
    ];
    chatModel.updateState({...chatModel.getState(), turns});
    await element.updateComplete;
    const backgroundRequestContainer = element.shadowRoot!.querySelector(
      '.background-request-container'
    );
    assert.isOk(backgroundRequestContainer);
  });

  test('toggles background request expansion', async () => {
    const turns: Turn[] = [
      {
        userMessage: {
          userType: UserType.USER,
          content: 'Test background request',
          isBackgroundRequest: true,
          contextItems: [],
        },
        geminiMessage: {
          userType: UserType.GEMINI,
          responseParts: [],
          regenerationIndex: 0,
          responseComplete: false,
          references: [],
          citations: [],
        },
      },
    ];
    chatModel.updateState({...chatModel.getState(), turns});
    await element.updateComplete;

    const expansionButton = element.shadowRoot!.querySelector(
      '.info-panel-expansion-button'
    ) as HTMLElement;
    assert.isOk(expansionButton);

    const innerContainer = element.shadowRoot!.querySelector(
      '.background-request-container-inner'
    );
    assert.isFalse(innerContainer!.classList.contains('expanded'));

    expansionButton.click();
    await element.updateComplete;
    assert.isTrue(innerContainer!.classList.contains('expanded'));

    expansionButton.click();
    await element.updateComplete;
    assert.isFalse(innerContainer!.classList.contains('expanded'));
  });

  test('clicking action starts new chat', async () => {
    const actionElement =
      element.shadowRoot!.querySelector('splash-page-action')!;
    await actionElement.updateComplete;
    const chip = actionElement.shadowRoot!.querySelector('md-assist-chip')!;
    assert.equal(chip.textContent?.trim(), 'Summarize');
    chip.click();

    await element.updateComplete;
    const turns = chatModel.getState().turns;
    assert.lengthOf(turns, 1);
    assert.equal(turns[0].userMessage.content, 'Summarize the change');
    assert.equal(turns[0].userMessage.userType, UserType.USER);
  });
});
