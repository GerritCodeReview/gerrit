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
import {setViewport} from '@web/test-runner-commands';
import {GrChangeView} from './gr-change-view';
import {
  chatProvider,
  createAccountDetailWithId,
  createChangeViewChange,
  createRevisions,
  createServerInfo,
  createUserConfig,
  TEST_NUMERIC_CHANGE_ID,
} from '../../../test/test-data-generators';
import {
  stubRestApi,
  visualDiffDarkTheme,
  waitUntil,
} from '../../../test/test-utils';
import {testResolver} from '../../../test/common-test-setup';
import {changeModelToken} from '../../../models/change/change-model';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {
  ChangeChildView,
  changeViewModelToken,
} from '../../../models/views/change';
import {GerritView} from '../../../services/router/router-model';
import {
  AccountId,
  ActionInfo,
  EmailAddress,
  RepoName,
  RevisionPatchSetNum,
  Timestamp,
} from '../../../types/common';
import {HttpMethod} from '../../../api/rest-api';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {ParsedChangeInfo} from '../../../types/types';
import {ChangeStatus} from '../../../constants/constants';
import {NormalizedFileInfo} from '../../../models/change/files-model';
import * as sinon from 'sinon';

suite('gr-change-view screenshot tests', () => {
  let element: GrChangeView;

  function createMockFiles(): NormalizedFileInfo[] {
    return [
      {
        __path: '/COMMIT_MSG',
        lines_inserted: 10,
        lines_deleted: 0,
        size_delta: 350,
        size: 350,
      },
      {
        __path: 'src/main/java/com/google/gerrit/server/ChangeUtil.java',
        lines_inserted: 45,
        lines_deleted: 12,
        size_delta: 1250,
        size: 8500,
      },
      {
        __path:
          'src/main/java/com/google/gerrit/server/project/ProjectState.java',
        lines_inserted: 8,
        lines_deleted: 3,
        size_delta: 200,
        size: 4200,
      },
      {
        __path: 'src/test/java/com/google/gerrit/server/ChangeUtilTest.java',
        lines_inserted: 120,
        lines_deleted: 0,
        size_delta: 3500,
        size: 3500,
      },
      {
        __path:
          'polygerrit-ui/app/elements/change/gr-change-view/gr-change-view.ts',
        lines_inserted: 25,
        lines_deleted: 10,
        size_delta: 450,
        size: 15000,
      },
      {
        __path: 'polygerrit-ui/app/elements/shared/gr-button/gr-button.ts',
        lines_inserted: 5,
        lines_deleted: 2,
        size_delta: 100,
        size: 2500,
      },
      {
        __path: 'Documentation/rest-api-changes.txt',
        lines_inserted: 30,
        lines_deleted: 5,
        size_delta: 800,
        size: 45000,
      },
    ];
  }

  function createActions(): {[key: string]: ActionInfo} {
    return {
      abandon: {
        method: HttpMethod.POST,
        label: 'Abandon',
        title: 'Abandon the change',
        enabled: true,
      },
      rebase: {
        method: HttpMethod.POST,
        label: 'Rebase',
        title: 'Rebase the change',
        enabled: true,
      },
      submit: {
        method: HttpMethod.POST,
        label: 'Submit',
        title: 'Submit the change',
        enabled: false,
      },
    };
  }

  setup(async () => {
    sinon.stub(testResolver(navigationToken), 'setUrl');
    sinon
      .stub(testResolver(changeViewModelToken), 'editUrl')
      .returns('fakeEditUrl');
    sinon
      .stub(testResolver(changeViewModelToken), 'diffUrl')
      .returns('fakeDiffUrl');

    const pluginLoader = testResolver(pluginLoaderToken);
    pluginLoader.pluginsModel.aiCodeReviewRegister({
      pluginName: 'test-plugin',
      provider: chatProvider,
    });

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

    const change: ParsedChangeInfo = {
      ...createChangeViewChange(),
      _number: TEST_NUMERIC_CHANGE_ID,
      subject: 'Implement new feature for code review improvements',
      status: ChangeStatus.NEW,
      mergeable: true,
      owner: {
        _account_id: 1000 as AccountId,
        name: 'John Developer',
        email: 'john@example.com' as EmailAddress,
      },
      revisions: createRevisions(3),
      current_revision: '3' as any,
      actions: createActions(),
      labels: {
        'Code-Review': {
          all: [
            {
              value: 2,
              _account_id: 1001 as AccountId,
              name: 'Reviewer One',
            },
          ],
          values: {
            '-2': 'This shall not be merged',
            '-1': 'I would prefer this is not merged as is',
            ' 0': 'No score',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
          default_value: 0,
        },
        Verified: {
          all: [
            {
              value: 1,
              _account_id: 1002 as AccountId,
              name: 'CI Bot',
            },
          ],
          values: {
            '-1': 'Fails',
            ' 0': 'No score',
            '+1': 'Verified',
          },
          default_value: 0,
        },
      },
      reviewers: {
        REVIEWER: [
          {
            _account_id: 1001 as AccountId,
            name: 'Reviewer One',
            email: 'reviewer1@example.com' as EmailAddress,
          },
          {
            _account_id: 1003 as AccountId,
            name: 'Reviewer Two',
            email: 'reviewer2@example.com' as EmailAddress,
          },
        ],
        CC: [
          {
            _account_id: 1004 as AccountId,
            name: 'Watcher',
            email: 'watcher@example.com' as EmailAddress,
          },
        ],
      },
      insertions: 243,
      deletions: 32,
      updated: '2025-01-09 10:30:00.000000000' as Timestamp,
      created: '2025-01-08 14:20:00.000000000' as Timestamp,
    };

    const changeModel = testResolver(changeModelToken);
    changeModel.updateStateChange(change);

    // Set comments model to a settled state (not loading)
    const commentsModel = testResolver(commentsModelToken);
    commentsModel.setState({
      comments: {},
      drafts: {},
      portedComments: {},
      portedDrafts: {},
      discardedDrafts: [],
    });

    element = await fixture<GrChangeView>(
      html`<gr-change-view></gr-change-view>`
    );
    element.viewState = {
      view: GerritView.CHANGE,
      childView: ChangeChildView.OVERVIEW,
      changeNum: TEST_NUMERIC_CHANGE_ID,
      repo: 'gerrit' as RepoName,
      edit: true, // Enable edit mode to show Edit button
    };
    element.patchNum = 3 as RevisionPatchSetNum;

    await element.updateComplete;

    // Wait for the change to load
    await waitUntil(() => element.change !== undefined);
    await element.updateComplete;

    // Set files on the file list
    if (element.fileList) {
      element.fileList.files = createMockFiles();
      await element.fileList.updateComplete;
    }

    await element.updateComplete;
  });

  test('full page at 801px width', async () => {
    // Set viewport to ensure media queries respond correctly
    await setViewport({width: 801, height: 900});

    const container = document.createElement('div');
    container.style.width = '801px';
    container.style.height = '900px';
    container.style.overflow = 'hidden';
    container.style.display = 'block';
    container.style.backgroundColor = 'var(--view-background-color, #fff)';
    container.appendChild(element);
    document.body.appendChild(container);

    try {
      // Wait for all nested components to render
      await new Promise(resolve => setTimeout(resolve, 500));
      await element.updateComplete;
      if (element.fileList) {
        await element.fileList.updateComplete;
      }
      // Additional wait for any remaining async rendering
      await new Promise(resolve => setTimeout(resolve, 200));

      await visualDiff(container, 'gr-change-view-801px');
      await visualDiffDarkTheme(container, 'gr-change-view-801px');
    } finally {
      document.body.removeChild(container);
    }
  });

  test('full page 1280px with chat panel', async () => {
    // Set viewport to ensure media queries respond correctly
    await setViewport({width: 1280, height: 800});
    // Force open the chat panel
    (element as any).showSidebarChat = true;

    const container = document.createElement('div');
    container.style.width = '1280px';
    container.style.height = '800px';
    container.style.overflow = 'hidden';
    container.style.display = 'block';
    container.style.backgroundColor = 'var(--view-background-color, #fff)';
    container.appendChild(element);
    document.body.appendChild(container);

    try {
      // Wait for all nested components to render
      await new Promise(resolve => setTimeout(resolve, 500));
      await element.updateComplete;
      if (element.fileList) {
        await element.fileList.updateComplete;
      }
      // Additional wait for any remaining async rendering
      await new Promise(resolve => setTimeout(resolve, 200));

      await visualDiff(container, 'gr-change-view-1280px-chat-open');
      await visualDiffDarkTheme(container, 'gr-change-view-1280px-chat-open');
    } finally {
      document.body.removeChild(container);
    }
  });
});
