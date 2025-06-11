/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import './gr-diff-view';
import {
  stubRestApi,
  waitUntil,
} from '../../../test/test-utils';
import {ChangeComments} from '../gr-comment-api/gr-comment-api';
import {
  createComment as createCommentGeneric,
  createConfig,
  createDiff,
  createDiffViewState,
  createFileInfo,
  createParsedChange,
  createRevision,
  createServerInfo,
  TEST_NUMERIC_CHANGE_ID,
} from '../../../test/test-data-generators';
import {
  CommentInfo,
  NumericChangeId,
  PARENT,
  PatchSetNum,
  RevisionPatchSetNum,
  UrlEncodedCommentId,
} from '../../../types/common';
import {Files, GrDiffView} from './gr-diff-view';
import {SinonFakeTimers, SinonStubbedMember} from 'sinon';
import {
  ChangeModel,
  changeModelToken,
} from '../../../models/change/change-model';
import {testResolver} from '../../../test/common-test-setup';
import {UserModel, userModelToken} from '../../../models/user/user-model';
import {
  CommentsModel,
  commentsModelToken,
} from '../../../models/comments/comments-model';
import {
  BrowserModel,
  browserModelToken,
} from '../../../models/browser/browser-model';
import {
  ChangeViewModel,
  changeViewModelToken,
} from '../../../models/views/change';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import { FileNameToNormalizedFileInfoMap } from '../../../models/change/files-model';


function createComment(
  id: string,
  line: number,
  ps: number | PatchSetNum,
  path: string
): CommentInfo {
  return {
    ...createCommentGeneric(),
    id: id as UrlEncodedCommentId,
    line,
    patch_set: ps as RevisionPatchSetNum,
    path,
  };
}

function getFilesFromFileList(fileList: string[]): Files {
  const changeFilesByPath = fileList.reduce((files, path) => {
    files[path] = createFileInfo(path);
    return files;
  }, {} as FileNameToNormalizedFileInfoMap);
  return {
    sortedPaths: fileList,
    changeFilesByPath,
  };
}

suite('gr-diff-view screenshot tests', () => {
  let element: GrDiffView;
    let diffCommentsStub;
    let getDiffRestApiStub: SinonStubbedMember<RestApiService['getDiff']>;
    let navToChangeStub: SinonStubbedMember<ChangeModel['navigateToChange']>;
    let navToDiffStub: SinonStubbedMember<ChangeModel['navigateToDiff']>;
    let navToEditStub: SinonStubbedMember<ChangeModel['navigateToEdit']>;
    let changeModel: ChangeModel;
    let viewModel: ChangeViewModel;
    let commentsModel: CommentsModel;
    let browserModel: BrowserModel;
    let userModel: UserModel;

  // similar to gr-diff-view_test.ts
  setup(async () => {
    stubRestApi('getConfig').returns(Promise.resolve(createServerInfo()));
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      stubRestApi('getProjectConfig').returns(Promise.resolve(createConfig()));
      stubRestApi('getChangeFiles').returns(
        Promise.resolve({
          'chell.go': createFileInfo(),
          'glados.txt': createFileInfo(),
          'wheatley.md': createFileInfo(),
        })
      );
      stubRestApi('saveFileReviewed').returns(Promise.resolve(new Response()));
      diffCommentsStub = stubRestApi('getDiffComments');
      diffCommentsStub.returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));
      stubRestApi('getPortedComments').returns(Promise.resolve({}));

      viewModel = testResolver(changeViewModelToken);
      viewModel.setState(createDiffViewState());
      element = await fixture(html`<gr-diff-view></gr-diff-view>`);
      await waitUntil(() => element.changeNum === TEST_NUMERIC_CHANGE_ID);
      element.path = 'some/path.txt';
      element.change = createParsedChange();
      element.diff = {...createDiff(), content: []};
      getDiffRestApiStub = stubRestApi('getDiff');
      // Delayed in case a test updates element.diff.
      getDiffRestApiStub.callsFake(() => Promise.resolve(element.diff));
      element.patchNum = 1 as RevisionPatchSetNum;
      element.basePatchNum = PARENT;
      element.changeComments = new ChangeComments({
        '/COMMIT_MSG': [
          createComment('c1', 10, 2, '/COMMIT_MSG'),
          createComment('c3', 10, PARENT, '/COMMIT_MSG'),
        ],
      });
      await element.updateComplete;
      commentsModel = testResolver(commentsModelToken);
      changeModel = testResolver(changeModelToken);
      browserModel = testResolver(browserModelToken);
      userModel = testResolver(userModelToken);
      navToChangeStub = sinon.stub(changeModel, 'navigateToChange');
      navToDiffStub = sinon.stub(changeModel, 'navigateToDiff');
      navToEditStub = sinon.stub(changeModel, 'navigateToEdit');

      commentsModel.setState({
        comments: {},
        drafts: {},
        portedComments: {},
        portedDrafts: {},
        discardedDrafts: [],
      });
  });

  test('screenshot', async () => {
    element.diff = {
      content: [
        {a: ['line 1'], b: ['line 1']},
        {a: ['line 2'], b: ['line 2 modified']},
        {a: ['line 3'], b: ['line 3']},
      ],
      change_type: 'MODIFIED',
      meta_a: {name: '/file1', content_type: 'text/plain', lines: 3},
      meta_b: {name: '/file1', content_type: 'text/plain', lines: 3},
      intraline_status: 'OK',
    };
    browserModel.setScreenWidth(0);
      const patchNum = 10 as RevisionPatchSetNum;
      element.patchNum = patchNum;
      element.basePatchNum = PARENT;
      const change = {
        ...createParsedChange(),
        _number: 42 as NumericChangeId,
        revisions: {
          a: createRevision(patchNum),
        },
      };
      changeModel.updateStateChange(change);
      viewModel.updateState({patchNum});
      element.files = getFilesFromFileList([
        'chell.go',
        'glados.txt',
        'wheatley.md',
      ]);
      element.path = 'glados.txt';
      element.loggedIn = true;

    await element.updateComplete;
    await element.diffHost?.updateComplete;
    await visualDiff(element, 'gr-diff-view');
  });
}); 