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
  CommentInfo,
  DraftInfo,
  EmailAddress,
  NumericChangeId,
  Timestamp,
  UrlEncodedCommentId,
} from '../../types/common';
import {
  CommentsModel,
  deleteDraft,
  filterOutPublishedDrafts,
} from './comments-model';
import {Subscription} from 'rxjs';
import {
  createComment,
  createParsedChange,
} from '../../test/test-data-generators';
import {stubRestApi, waitUntil, waitUntilCalled} from '../../test/test-utils';
import {getAppContext} from '../../services/app-context';
import {changeModelToken} from '../change/change-model';
import {assert} from '@open-wc/testing';
import {testResolver} from '../../test/common-test-setup';
import {accountsModelToken} from '../accounts/accounts-model';
import {ChangeComments} from '../../elements/diff/gr-comment-api/gr-comment-api';
import {changeViewModelToken} from '../views/change';
import {navigationToken} from '../../elements/core/gr-navigation/gr-navigation';

suite('comments model tests', () => {
  test('updateStateDeleteDraft', () => {
    const draft = createDraft();
    draft.id = '1' as UrlEncodedCommentId;
    const state = {
      comments: {},
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
      getAppContext().reportingService,
      testResolver(navigationToken)
    );
    const diffCommentsSpy = stubRestApi('getDiffComments').returns(
      Promise.resolve({'foo.c': [createComment()]})
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
    let comments: {[path: string]: CommentInfo[]} = {};
    subscriptions.push(model.comments$.subscribe(c => (comments = c ?? {})));
    let portedComments: {[path: string]: CommentInfo[]} = {};
    subscriptions.push(
      model.portedComments$.subscribe(c => (portedComments = c ?? {}))
    );

    testResolver(changeViewModelToken).setState(createChangeViewState());
    testResolver(changeModelToken).updateStateChange(createParsedChange());

    await waitUntilCalled(diffCommentsSpy, 'diffCommentsSpy');
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

  test('published drafts and ported drafts are filtered out', async () => {
    const draft1 = {
      ...createDraft(),
      id: 'draft1' as UrlEncodedCommentId,
      path: 'path1',
    };
    const draft2 = {
      ...createDraft(),
      id: 'draft2' as UrlEncodedCommentId,
      path: 'path2',
    };
    const draft3 = {
      ...createDraft(),
      id: 'draft3' as UrlEncodedCommentId,
      path: 'path1',
    };
    const comment1 = {
      ...createComment(),
      id: 'draft1' as UrlEncodedCommentId,
      path: 'path1',
    };
    const comment2 = {
      ...createComment(),
      id: 'draft3' as UrlEncodedCommentId,
      path: 'path1',
    };
    const comment3 = {
      ...createComment(),
      id: 'non-draft' as UrlEncodedCommentId,
      path: 'path',
    };

    const model = new CommentsModel(
      testResolver(changeViewModelToken),
      testResolver(changeModelToken),
      testResolver(accountsModelToken),
      getAppContext().restApiService,
      getAppContext().reportingService,
      testResolver(navigationToken)
    );
    const diffCommentsSpy = stubRestApi('getDiffComments').returns(
      Promise.resolve({path1: [comment1, comment2], path2: [comment3]})
    );
    const diffDraftsSpy = stubRestApi('getDiffDrafts').returns(
      Promise.resolve({path1: [draft1, draft3], path2: [draft2]})
    );
    const portedCommentsSpy = stubRestApi('getPortedComments').returns(
      Promise.resolve({path1: [comment2]})
    );
    const portedDraftsSpy = stubRestApi('getPortedDrafts').returns(
      Promise.resolve({path1: [draft3], path2: [draft2]})
    );
    let drafts: {[path: string]: DraftInfo[]} = {};
    let portedDrafts: {[path: string]: DraftInfo[]} = {};
    subscriptions.push(
      model.state$.subscribe(s => {
        drafts = s.drafts ?? {};
        portedDrafts = s.portedDrafts ?? {};
      })
    );

    testResolver(changeViewModelToken).setState(createChangeViewState());
    testResolver(changeModelToken).updateStateChange(createParsedChange());

    await waitUntilCalled(diffCommentsSpy, 'diffCommentsSpy');
    await waitUntilCalled(diffDraftsSpy, 'diffDraftsSpy');
    await waitUntilCalled(portedCommentsSpy, 'portedCommentsSpy');
    await waitUntilCalled(portedDraftsSpy, 'portedDraftsSpy');
    await waitUntil(
      () => Object.keys(drafts).length > 0,
      'comment in model not set'
    );
    await waitUntil(
      () => Object.keys(portedDrafts).length > 0,
      'ported comment in model not set'
    );

    assert.deepEqual(drafts, {path2: [draft2]});
    assert.deepEqual(portedDrafts, {path2: [draft2]});
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
      getAppContext().reportingService,
      testResolver(navigationToken)
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
      getAppContext().reportingService,
      testResolver(navigationToken)
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
      getAppContext().reportingService,
      testResolver(navigationToken)
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

  test('filterOutPublishedDrafts removes drafts that are comments', () => {
    const draft1 = {...createDraft(), id: 'draft1' as UrlEncodedCommentId};
    const draft2 = {...createDraft(), id: 'draft2' as UrlEncodedCommentId};
    const draft3 = {...createDraft(), id: 'draft3' as UrlEncodedCommentId};
    const comment1 = {...createComment(), id: 'draft1' as UrlEncodedCommentId};
    const comment2 = {...createComment(), id: 'draft3' as UrlEncodedCommentId};
    const comment3 = {
      ...createComment(),
      id: 'non-draft' as UrlEncodedCommentId,
    };

    const drafts = {path1: [draft1, draft2], path2: [draft3]};
    const commentIds = new Set<UrlEncodedCommentId>([
      comment1.id,
      comment2.id,
      comment3.id,
    ]);
    const {drafts: filteredDrafts, removedCnt} = filterOutPublishedDrafts(
      drafts,
      commentIds
    );
    assert.deepEqual(filteredDrafts, {path1: [draft2]});
    assert.equal(removedCnt, 2);
  });
});
