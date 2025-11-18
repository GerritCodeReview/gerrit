/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import './chat-panel';
import {ChatPanel} from './chat-panel';
import {
  ChatModel,
  chatModelToken,
  ResponsePartType,
  Turn,
  UserType,
} from '../../models/chat/chat-model';
import {testResolver} from '../../test/common-test-setup';
import {pluginLoaderToken} from '../../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {changeModelToken} from '../../models/change/change-model';
import {chatProvider, createChange} from '../../test/test-data-generators';
import {ParsedChangeInfo} from '../../types/types';
import {visualDiffDarkTheme} from '../../test/test-utils';

suite('chat-panel screenshot tests', () => {
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

  test('splash page', async () => {
    await visualDiff(element, 'chat-panel-splash-page');
    await visualDiffDarkTheme(element, 'chat-panel-splash-page');
  });

  test('chat mode', async () => {
    chatModel.updateState({
      ...chatModel.getState(),
      turns: [
        {
          userMessage: {
            content: 'hello',
            userType: UserType.USER,
            contextItems: [],
          },
          geminiMessage: {
            responseParts: [
              {id: 0, type: ResponsePartType.TEXT, content: 'world'},
            ],
            regenerationIndex: 0,
            references: [],
            citations: [],
            userType: UserType.GEMINI,
            responseComplete: true,
          },
        },
      ] as Turn[],
    });
    await element.updateComplete;
    await visualDiff(element, 'chat-panel-chat-mode');
    await visualDiffDarkTheme(element, 'chat-panel-chat-mode');
  });
});
