/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {
  createAccountWithEmail,
  createChangeViewState,
  createDraft,
} from '../../test/test-data-generators';
import {
  AccountInfo,
  EmailAddress,
  NumericChangeId,
  Timestamp,
  UrlEncodedCommentId,
} from '../../types/common';
import {CommentsModel, deleteDraft} from './comments-model';
import {Subscription} from 'rxjs';
import {
  createComment,
  createParsedChange,
} from '../../test/test-data-generators';
import {stubRestApi, waitUntil, waitUntilCalled} from '../../test/test-utils';
import {getAppContext} from '../../services/app-context';
import {PathToCommentsInfoMap} from '../../types/common';
import {changeModelToken} from '../change/change-model';
import {assert} from '@open-wc/testing';
import {testResolver} from '../../test/common-test-setup';
import {accountsModelToken} from '../accounts-model/accounts-model';
import {ChangeComments} from '../../elements/diff/gr-comment-api/gr-comment-api';
import {changeViewModelToken} from '../views/change';

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
      testResolver(changeViewModelToken),
      testResolver(changeModelToken),
      testResolver(accountsModelToken),
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

    testResolver(changeViewModelToken).setState(createChangeViewState());
    testResolver(changeModelToken).updateStateChange(createParsedChange());

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
      testResolver(changeViewModelToken),
      testResolver(changeModelToken),
      testResolver(accountsModelToken),
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
      testResolver(changeViewModelToken),
      testResolver(changeModelToken),
      testResolver(accountsModelToken),
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

  test('delete comment change is emitted', async () => {
    const comment = createComment();
    stubRestApi('deleteComment').returns(
      Promise.resolve({
        ...comment,
        message: 'Comment is deleted',
      })
    );
    const model = new CommentsModel(
      testResolver(changeViewModelToken),
      testResolver(changeModelToken),
      testResolver(accountsModelToken),
      getAppContext().restApiService,
      getAppContext().reportingService
    );

    let changeComments: ChangeComments | undefined = undefined;
    model.changeComments$.subscribe(x => (changeComments = x));
    model.setState({
      comments: {[comment.path!]: [comment]},
      discardedDrafts: [],
    });

    model.deleteComment(123 as NumericChangeId, comment, 'Comment is deleted');

    await waitUntil(
      () =>
        changeComments?.getAllCommentsForPath(comment.path!)[0].message ===
        'Comment is deleted'
    );
  });
});
