/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-change-view';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrChangeView} from './gr-change-view';
import {
  createAccountDetailWithId,
  createChangeViewChange,
  createRevisions,
  createServerInfo,
  createUserConfig,
  TEST_NUMERIC_CHANGE_ID,
} from '../../../test/test-data-generators';
import {testResolver} from '../../../test/common-test-setup';
import {
  stubRestApi,
  visualDiffDarkTheme,
  waitUntil,
} from '../../../test/test-utils';
import {GerritView} from '../../../services/router/router-model';
import {ChangeChildView} from '../../../models/views/change';
import {RepoName, RevisionPatchSetNum} from '../../../types/common';
import {AiCodeReviewProvider} from '../../../api/ai-code-review';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {changeModelToken} from '../../../models/change/change-model';
import {LoadingStatus} from '../../../types/types';
import {filesModelToken, normalize} from '../../../models/change/files-model';
import {FileInfo} from '../../../api/rest-api';
import {userModelToken} from '../../../models/user/user-model';
import {createDefaultPreferences} from '../../../constants/constants';
import {chatModelToken} from '../../../models/chat/chat-model';

suite('gr-change-view screenshot tests', () => {
  let element: GrChangeView;

  setup(async () => {
    stubRestApi('getConfig').returns(
      Promise.resolve({
        ...createServerInfo(),
        user: {
          ...createUserConfig(),
          anonymous_coward_name: 'test coward name',
        },
      })
    );
    stubRestApi('getAccount').returns(
      Promise.resolve(createAccountDetailWithId(5))
    );
    stubRestApi('getIfFlowsIsEnabled').returns(
      Promise.resolve({enabled: false})
    );
    stubRestApi('getDiffComments').returns(Promise.resolve({}));
    stubRestApi('getDiffDrafts').returns(Promise.resolve({}));

    // Mock the plugin model to avoid chat model crash
    testResolver(pluginLoaderToken).pluginsModel.updateState({
      aiCodeReviewPlugins: [
        {
          pluginName: 'test-plugin',
          provider: {
            chat: () => {},
            getModels: () => Promise.resolve({models: []}),
            getActions: () => Promise.resolve({actions: []}),
            getContextItemTypes: () => Promise.resolve([]),
            listChatConversations: () => Promise.resolve([]),
            getChatConversation: () => Promise.resolve([]),
          } as unknown as AiCodeReviewProvider,
        },
      ],
    });

    // Set loading status to LOADED
    testResolver(changeModelToken).updateState({
      loadingStatus: LoadingStatus.LOADED,
    });

    element = await fixture<GrChangeView>(
      html`<gr-change-view></gr-change-view>`
    );

    element.viewState = {
      view: GerritView.CHANGE,
      childView: ChangeChildView.OVERVIEW,
      changeNum: TEST_NUMERIC_CHANGE_ID,
      repo: 'gerrit' as RepoName,
    };
    element.changeNum = TEST_NUMERIC_CHANGE_ID;

    const change = {
      ...createChangeViewChange(),
      revisions: createRevisions(1),
    };
    element.change = change;
    element.patchNum = 1 as RevisionPatchSetNum;

    // Open chat panel
    (element as any).showSidebarChat = true;

    // Wait for file list
    const fileList = element.shadowRoot!.querySelector('gr-file-list');
    await fileList!.updateComplete;
  });

  // ... imports

  test('screenshot with chat panel open', async () => {
    // Populate file list with long paths
    const filesObject = {
      '/file/path/to/a/very/long/file/name/that/should/hopefully/cause/some/wrapping/issues/on/small/screens/0.txt':
        {
          lines_inserted: 10,
          lines_deleted: 5,
          size_delta: 15,
          size: 100,
        },
      '/file/path/to/a/very/long/file/name2/that/should/hopefully/cause/some/wrapping/issues/on/small/screens/1.txt':
        {
          lines_inserted: 2,
          lines_deleted: 0,
          size_delta: 2,
          size: 20,
        },
      '/short.txt': {
        lines_inserted: 1,
        lines_deleted: 1,
        size_delta: 0,
        size: 10,
      },
    } as unknown as {[path: string]: FileInfo};

    stubRestApi('getChangeOrEditFiles').returns(Promise.resolve(filesObject));

    const files = Object.entries(filesObject).map(([path, info]) =>
      normalize(info, path)
    );

    // Provide files to the model
    testResolver(filesModelToken).updateState({
      files,
    });

    // Populate ChatModel with sample data
    testResolver(chatModelToken).updateState({
      turns: [
        {
          userMessage: {
            content: 'Explain this change',
            userType: 0, // UserType.USER
            contextItems: [],
          },
          geminiMessage: {
            responseParts: [
              {
                id: 1,
                type: 0, // ResponsePartType.TEXT
                content: 'This change updates the file list responsiveness.',
              } as any,
            ],
            regenerationIndex: 0,
            references: [],
            citations: [],
            userType: 1, // UserType.GEMINI
          },
        },
      ],
    });

    (element as any).loading = false;

    testResolver(userModelToken).updateState({
      account: createAccountDetailWithId(5),
      preferences: {
        ...createDefaultPreferences(),
        size_bar_in_change_table: true,
      },
    });

    // Wait for file list
    const fileList = element.shadowRoot!.querySelector('gr-file-list');
    await fileList!.updateComplete;
    await waitUntil(() => fileList!.loggedIn === true, 'loggedIn was not set');

    // Wait for files to be loaded in file list
    await new Promise(resolve => setTimeout(resolve, 1000)); // Hacky wait for internal asyncs

    // Set a fixed width to simulate larger screen for chat panel
    document.body.style.width = '1200px';
    element.style.position = 'relative';
    await element.updateComplete;

    // Force a layout reflow/measure
    await new Promise(requestAnimationFrame);

    await visualDiff(element, 'gr-change-view-chat-open');
    await visualDiffDarkTheme(element, 'gr-change-view-chat-open');
  });
});
