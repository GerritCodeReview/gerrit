/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {
  createAccountWithEmail,
  createDraft,
} from '../../test/test-data-generators';
import {
  AccountInfo,
  EmailAddress,
  Timestamp,
  UrlEncodedCommentId,
} from '../../types/common';
import {CommentsModel, deleteDraft} from './comments-model';
import {Subscription} from 'rxjs';
import {
  createComment,
  createParsedChange,
  TEST_NUMERIC_CHANGE_ID,
} from '../../test/test-data-generators';
import {stubRestApi, waitUntil, waitUntilCalled} from '../../test/test-utils';
import {getAppContext} from '../../services/app-context';
import {GerritView} from '../../services/router/router-model';
import {PathToCommentsInfoMap} from '../../types/common';
import {changeModelToken} from '../change/change-model';
import {assert} from '@open-wc/testing';
import {testResolver} from '../../test/common-test-setup';

suite('comments model tests', () => {
  test('updateStateDeleteDraft', () => {
    const draft = createDraft();
    draft.id = '1' as UrlEncodedCommentId;
    const state = {
      comments: {},
      robotComments: {},
      drafts: {
        [draft.path!]: [draft],
      },
      portedComments: {},
      portedDrafts: {},
      discardedDrafts: [],
    };
    const output = deleteDraft(state, draft);
    assert.deepEqual(output, {
      comments: {},
      robotComments: {},
      drafts: {
        'abc.txt': [],
      },
      portedComments: {},
      portedDrafts: {},
      discardedDrafts: [{...draft}],
    });
  });
});

suite('change service tests', () => {
  let subscriptions: Subscription[] = [];

  teardown(() => {
    for (const s of subscriptions) {
      s.unsubscribe();
    }
    subscriptions = [];
  });

  test('loads comments', async () => {
    const model = new CommentsModel(
      getAppContext().routerModel,
      testResolver(changeModelToken),
      getAppContext().accountsModel,
      getAppContext().restApiService,
      getAppContext().reportingService
    );
    const diffCommentsSpy = stubRestApi('getDiffComments').returns(
      Promise.resolve({'foo.c': [createComment()]})
    );
    const diffRobotCommentsSpy = stubRestApi('getDiffRobotComments').returns(
      Promise.resolve({})
    );
    const diffDraftsSpy = stubRestApi('getDiffDrafts').returns(
      Promise.resolve({})
    );
    const portedCommentsSpy = stubRestApi('getPortedComments').returns(
      Promise.resolve({'foo.c': [createComment()]})
    );
    const portedDraftsSpy = stubRestApi('getPortedDrafts').returns(
      Promise.resolve({})
    );
    let comments: PathToCommentsInfoMap = {};
    subscriptions.push(model.comments$.subscribe(c => (comments = c ?? {})));
    let portedComments: PathToCommentsInfoMap = {};
    subscriptions.push(
      model.portedComments$.subscribe(c => (portedComments = c ?? {}))
    );

    model.routerModel.setState({
      view: GerritView.CHANGE,
      changeNum: TEST_NUMERIC_CHANGE_ID,
    });
    model.changeModel.updateStateChange(createParsedChange());

    await waitUntilCalled(diffCommentsSpy, 'diffCommentsSpy');
    await waitUntilCalled(diffRobotCommentsSpy, 'diffRobotCommentsSpy');
    await waitUntilCalled(diffDraftsSpy, 'diffDraftsSpy');
    await waitUntilCalled(portedCommentsSpy, 'portedCommentsSpy');
    await waitUntilCalled(portedDraftsSpy, 'portedDraftsSpy');
    await waitUntil(
      () => Object.keys(comments).length > 0,
      'comment in model not set'
    );
    await waitUntil(
      () => Object.keys(portedComments).length > 0,
      'ported comment in model not set'
    );

    assert.equal(comments['foo.c'].length, 1);
    assert.equal(comments['foo.c'][0].id, '12345');
    assert.equal(portedComments['foo.c'].length, 1);
    assert.equal(portedComments['foo.c'][0].id, '12345');
  });

  test('duplicate mentions are filtered out', async () => {
    const account = {
      ...createAccountWithEmail('abcd@def.com' as EmailAddress),
      registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
    };
    stubRestApi('getAccountDetails').returns(Promise.resolve(account));
    const model = new CommentsModel(
      getAppContext().routerModel,
      testResolver(changeModelToken),
      getAppContext().accountsModel,
      getAppContext().restApiService,
      getAppContext().reportingService
    );
    let mentionedUsers: AccountInfo[] = [];
    const draft = {...createDraft(), message: 'hey @abc@def.com'};
    model.mentionedUsersInDrafts$.subscribe(x => (mentionedUsers = x));
    model.setState({
      drafts: {
        'abc.txt': [draft, draft],
      },
      discardedDrafts: [],
    });

    await waitUntil(() => mentionedUsers.length > 0);

    assert.deepEqual(mentionedUsers, [account]);
  });

  test('empty mentions are emitted', async () => {
    const account = {
      ...createAccountWithEmail('abcd@def.com' as EmailAddress),
      registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
    };
    stubRestApi('getAccountDetails').returns(Promise.resolve(account));
    const model = new CommentsModel(
      getAppContext().routerModel,
      testResolver(changeModelToken),
      getAppContext().accountsModel,
      getAppContext().restApiService,
      getAppContext().reportingService
    );
    let mentionedUsers: AccountInfo[] = [];
    const draft = {...createDraft(), message: 'hey @abc@def.com'};
    model.mentionedUsersInDrafts$.subscribe(x => (mentionedUsers = x));
    model.setState({
      drafts: {
        'abc.txt': [draft],
      },
      discardedDrafts: [],
    });

    await waitUntil(() => mentionedUsers.length > 0);

    assert.deepEqual(mentionedUsers, [account]);

    model.setState({
      drafts: {
        'abc.txt': [],
      },
      discardedDrafts: [],
    });
    await waitUntil(() => mentionedUsers.length === 0);
  });
});
