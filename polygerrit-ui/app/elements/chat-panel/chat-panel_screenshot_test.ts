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
import {queryAndAssert, visualDiffDarkTheme} from '../../test/test-utils';
import {ReferencesDropdown} from './references-dropdown';

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

  test('splash page private change', async () => {
    const changeModel = testResolver(changeModelToken);
    changeModel.updateState({
      change: {
        ...createChange(),
        is_private: true,
      } as ParsedChangeInfo,
    });
    await element.updateComplete;
    await visualDiff(element, 'chat-panel-splash-page-private');
    await visualDiffDarkTheme(element, 'chat-panel-splash-page-private');
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

  test('chat mode with comment', async () => {
    chatModel.updateState({
      ...chatModel.getState(),
      turns: [
        {
          userMessage: {
            content: 'Fix this issue',
            userType: UserType.USER,
            contextItems: [],
          },
          geminiMessage: {
            responseParts: [
              {
                id: 0,
                type: ResponsePartType.TEXT,
                content: 'I have created a comment for you:',
              },
              {
                id: 1,
                type: ResponsePartType.CREATE_COMMENT,
                content: '',
                commentCreationId: '123',
                comment: {
                  path: 'polygerrit-ui/app/elements/chat-panel/chat-panel.ts',
                  line: 10,
                  message: 'Please fix this typo.',
                },
              },
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
    await visualDiff(element, 'chat-panel-chat-mode-with-comment');
    await visualDiffDarkTheme(element, 'chat-panel-chat-mode-with-comment');
  });

  test('chat mode with references', async () => {
    chatModel.updateState({
      ...chatModel.getState(),
      turns: [
        {
          userMessage: {
            content: 'What are the conventions?',
            userType: UserType.USER,
            contextItems: [],
          },
          geminiMessage: {
            responseParts: [
              {
                id: 0,
                type: ResponsePartType.TEXT,
                content: 'Here are some references I found:',
              },
            ],
            regenerationIndex: 0,
            references: [
              {
                type: 'g3doc',
                displayText: 'fe-conventions.md',
                externalUrl:
                  'https://source.corp.google.com///depot/company/teams/gstore/teams/gCMS/frontend/fe-conventions.md',
              },
              {
                type: 'yaqs',
                displayText: 'YAQS 5734203896627200',
                externalUrl: 'https://yaqs.corp.google.com/5734203896627200',
              },
              {
                type: 'g3doc',
                displayText: 'style_guidelines.md',
                externalUrl:
                  'https://source.corp.google.com///depot/google3/video/youtube/src/web/polymer/music/g3doc/style_guidelines.md',
              },
            ],
            citations: [],
            userType: UserType.GEMINI,
            responseComplete: true,
          },
        },
      ] as Turn[],
    });
    await element.updateComplete;
    await element.updateComplete;
    const geminiMessage = queryAndAssert(element, 'gemini-message');
    const referencesDropdown = queryAndAssert<ReferencesDropdown>(
      geminiMessage,
      'references-dropdown'
    );
    const expandButton = queryAndAssert<HTMLButtonElement>(
      referencesDropdown,
      '.references-dropdown-button'
    );
    expandButton.click();
    await element.updateComplete;
    await visualDiff(element, 'chat-panel-chat-mode-with-references');
    await visualDiffDarkTheme(element, 'chat-panel-chat-mode-with-references');
  });

  test('chat mode with citations', async () => {
    chatModel.updateState({
      ...chatModel.getState(),
      models: {
        ...chatModel.getState().models!,
        citation_url: 'https://www.google.com',
      },
      turns: [
        {
          userMessage: {
            content: 'What are the conventions?',
            userType: UserType.USER,
            contextItems: [],
          },
          geminiMessage: {
            responseParts: [
              {
                id: 0,
                type: ResponsePartType.TEXT,
                content: 'Here are some references I found:',
              },
            ],
            regenerationIndex: 0,
            references: [],
            citations: [
              'http://example.com/citation1',
              'http://example.com/citation2',
            ],
            userType: UserType.GEMINI,
            responseComplete: true,
          },
        },
      ] as Turn[],
    });
    await element.updateComplete;
    await visualDiff(element, 'chat-panel-chat-mode-with-citations');
    await visualDiffDarkTheme(element, 'chat-panel-chat-mode-with-citations');
  });
});
