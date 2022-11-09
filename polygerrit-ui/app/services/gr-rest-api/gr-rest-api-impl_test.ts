/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {
  addListenerForTest,
  assertFails,
  MockPromise,
  mockPromise,
  waitEventLoop,
} from '../../test/test-utils';
import {GrReviewerUpdatesParser} from '../../elements/shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {
  ListChangesOption,
  listChangesOptionsToHex,
} from '../../utils/change-util';
import {
  createAccountDetailWithId,
  createChange,
  createComment,
  createParsedChange,
  createServerInfo,
} from '../../test/test-data-generators';
import {CURRENT} from '../../utils/patch-set-util';
import {
  parsePrefixedJSON,
  readResponsePayload,
  JSON_PREFIX,
} from '../../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {GrRestApiServiceImpl} from './gr-rest-api-impl';
import {
  CommentSide,
  createDefaultEditPrefs,
  HttpMethod,
} from '../../constants/constants';
import {
  BasePatchSetNum,
  ChangeMessageId,
  CommentInfo,
  DashboardId,
  DiffPreferenceInput,
  EDIT,
  EditPreferencesInfo,
  Hashtag,
  HashtagsInput,
  NumericChangeId,
  PARENT,
  ParsedJSON,
  PatchSetNum,
  PreferencesInfo,
  RepoName,
  RevisionId,
  RevisionPatchSetNum,
  RobotCommentInfo,
  Timestamp,
  UrlEncodedCommentId,
} from '../../types/common';
import {assert} from '@open-wc/testing';
import {AuthService} from '../gr-auth/gr-auth';
import {GrAuthMock} from '../gr-auth/gr-auth_mock';

const EXPECTED_QUERY_OPTIONS = listChangesOptionsToHex(
  ListChangesOption.CHANGE_ACTIONS,
  ListChangesOption.CURRENT_ACTIONS,
  ListChangesOption.CURRENT_REVISION,
  ListChangesOption.DETAILED_LABELS,
  ListChangesOption.SUBMIT_REQUIREMENTS
);

suite('gr-rest-api-service-impl tests', () => {
  let element: GrRestApiServiceImpl;
  let authService: AuthService;

  let ctr = 0;
  let originalCanonicalPath: string | undefined;

  setup(() => {
    // Modify CANONICAL_PATH to effectively reset cache.
    ctr += 1;
    originalCanonicalPath = window.CANONICAL_PATH;
    window.CANONICAL_PATH = `test${ctr}`;

    const testJSON = ')]}\'\n{"hello": "bonjour"}';
    sinon.stub(window, 'fetch').resolves(new Response(testJSON));
    // fake auth
    authService = new GrAuthMock();
    sinon.stub(authService, 'authCheck').resolves(true);
    element = new GrRestApiServiceImpl(authService);

    element._projectLookup = {};
  });

  teardown(() => {
    window.CANONICAL_PATH = originalCanonicalPath;
  });

  test('parent diff comments are properly grouped', async () => {
    sinon.stub(element._restApiHelper, 'fetchJSON').resolves({
      '/COMMIT_MSG': [],
      'sieve.go': [
        {
          updated: '2017-02-03 22:32:28.000000000',
          message: 'this isn’t quite right',
        },
        {
          side: CommentSide.PARENT,
          message: 'how did this work in the first place?',
          updated: '2017-02-03 22:33:28.000000000',
        },
      ],
    } as unknown as ParsedJSON);
    const obj = await element._getDiffComments(
      42 as NumericChangeId,
      '/comments',
      undefined,
      PARENT,
      1 as PatchSetNum,
      'sieve.go'
    );
    assert.equal(obj.baseComments.length, 1);
    assert.deepEqual(obj.baseComments[0], {
      side: CommentSide.PARENT,
      message: 'how did this work in the first place?',
      path: 'sieve.go',
      updated: '2017-02-03 22:33:28.000000000' as Timestamp,
    } as RobotCommentInfo);
    assert.equal(obj.comments.length, 1);
    assert.deepEqual(obj.comments[0], {
      message: 'this isn’t quite right',
      path: 'sieve.go',
      updated: '2017-02-03 22:32:28.000000000' as Timestamp,
    } as RobotCommentInfo);
  });

  test('_setRange', () => {
    const comments: CommentInfo[] = [
      {
        id: '1' as UrlEncodedCommentId,
        side: CommentSide.PARENT,
        message: 'how did this work in the first place?',
        updated: '2017-02-03 22:32:28.000000000' as Timestamp,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
      {
        id: '2' as UrlEncodedCommentId,
        in_reply_to: '1' as UrlEncodedCommentId,
        message: 'this isn’t quite right',
        updated: '2017-02-03 22:33:28.000000000' as Timestamp,
      },
    ];
    const expectedResult: CommentInfo = {
      id: '2' as UrlEncodedCommentId,
      in_reply_to: '1' as UrlEncodedCommentId,
      message: 'this isn’t quite right',
      updated: '2017-02-03 22:33:28.000000000' as Timestamp,
      range: {
        start_line: 1,
        start_character: 1,
        end_line: 2,
        end_character: 1,
      },
    };
    const comment = comments[1];
    assert.deepEqual(element._setRange(comments, comment), expectedResult);
  });

  test('_setRanges', () => {
    const comments: CommentInfo[] = [
      {
        id: '3' as UrlEncodedCommentId,
        in_reply_to: '2' as UrlEncodedCommentId,
        message: 'this isn’t quite right either',
        updated: '2017-02-03 22:34:28.000000000' as Timestamp,
      },
      {
        id: '2' as UrlEncodedCommentId,
        in_reply_to: '1' as UrlEncodedCommentId,
        message: 'this isn’t quite right',
        updated: '2017-02-03 22:33:28.000000000' as Timestamp,
      },
      {
        id: '1' as UrlEncodedCommentId,
        side: CommentSide.PARENT,
        message: 'how did this work in the first place?',
        updated: '2017-02-03 22:32:28.000000000' as Timestamp,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
    ];
    const expectedResult: CommentInfo[] = [
      {
        id: '1' as UrlEncodedCommentId,
        side: CommentSide.PARENT,
        message: 'how did this work in the first place?',
        updated: '2017-02-03 22:32:28.000000000' as Timestamp,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
      {
        id: '2' as UrlEncodedCommentId,
        in_reply_to: '1' as UrlEncodedCommentId,
        message: 'this isn’t quite right',
        updated: '2017-02-03 22:33:28.000000000' as Timestamp,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
      {
        id: '3' as UrlEncodedCommentId,
        in_reply_to: '2' as UrlEncodedCommentId,
        message: 'this isn’t quite right either',
        updated: '2017-02-03 22:34:28.000000000' as Timestamp,
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
    ];
    assert.deepEqual(element._setRanges(comments), expectedResult);
  });

  test('differing patch diff comments are properly grouped', async () => {
    sinon.stub(element, 'getFromProjectLookup').resolves('test' as RepoName);
    sinon.stub(element._restApiHelper, 'fetchJSON').callsFake(async request => {
      const url = request.url;
      if (url === '/changes/test~42/revisions/1/comments') {
        return {
          '/COMMIT_MSG': [],
          'sieve.go': [
            {
              message: 'this isn’t quite right',
              updated: '2017-02-03 22:32:28.000000000',
            },
            {
              side: CommentSide.PARENT,
              message: 'how did this work in the first place?',
              updated: '2017-02-03 22:33:28.000000000',
            },
          ],
        } as unknown as ParsedJSON;
      } else if (url === '/changes/test~42/revisions/2/comments') {
        return {
          '/COMMIT_MSG': [],
          'sieve.go': [
            {
              message: 'What on earth are you thinking, here?',
              updated: '2017-02-03 22:32:28.000000000',
            },
            {
              side: CommentSide.PARENT,
              message: 'Yeah not sure how this worked either?',
              updated: '2017-02-03 22:33:28.000000000',
            },
            {
              message: '¯\\_(ツ)_/¯',
              updated: '2017-02-04 22:33:28.000000000',
            },
          ],
        } as unknown as ParsedJSON;
      }
      return undefined;
    });
    const obj = await element._getDiffComments(
      42 as NumericChangeId,
      '/comments',
      undefined,
      1 as BasePatchSetNum,
      2 as PatchSetNum,
      'sieve.go'
    );
    assert.equal(obj.baseComments.length, 1);
    assert.deepEqual(obj.baseComments[0], {
      message: 'this isn’t quite right',
      path: 'sieve.go',
      updated: '2017-02-03 22:32:28.000000000' as Timestamp,
    } as RobotCommentInfo);
    assert.equal(obj.comments.length, 2);
    assert.deepEqual(obj.comments[0], {
      message: 'What on earth are you thinking, here?',
      path: 'sieve.go',
      updated: '2017-02-03 22:32:28.000000000' as Timestamp,
    } as RobotCommentInfo);
    assert.deepEqual(obj.comments[1], {
      message: '¯\\_(ツ)_/¯',
      path: 'sieve.go',
      updated: '2017-02-04 22:33:28.000000000' as Timestamp,
    } as RobotCommentInfo);
  });

  test('server error', async () => {
    const getResponseObjectStub = sinon.stub(element, 'getResponseObject');
    sinon
      .stub(authService, 'fetch')
      .resolves(new Response(undefined, {status: 502}));
    const serverErrorEventPromise = new Promise(resolve => {
      addListenerForTest(document, 'server-error', resolve);
    });
    const response = await element._restApiHelper.fetchJSON({url: ''});
    assert.isUndefined(response);
    assert.isTrue(getResponseObjectStub.notCalled);
    await serverErrorEventPromise;
  });

  test('legacy n,z key in change url is replaced', async () => {
    const stub = sinon
      .stub(element._restApiHelper, 'fetchJSON')
      .resolves([] as unknown as ParsedJSON);
    await element.getChanges(1, undefined, 'n,z');
    assert.equal(stub.lastCall.args[0].params!.S, 0);
  });

  test('saveDiffPreferences invalidates cache line', () => {
    const cacheKey = '/accounts/self/preferences.diff';
    const sendStub = sinon.stub(element._restApiHelper, 'send');
    element._cache.set(cacheKey, {tab_size: 4} as unknown as ParsedJSON);
    element.saveDiffPreferences({
      tab_size: 8,
      ignore_whitespace: 'IGNORE_NONE',
    });
    assert.isTrue(sendStub.called);
    assert.isFalse(element._cache.has(cacheKey));
  });

  suite('getAccountSuggestions', () => {
    let fetchStub: sinon.SinonStub;
    setup(() => {
      fetchStub = sinon
        .stub(element._restApiHelper, 'fetch')
        .resolves(new Response());
    });

    test('url with just email', () => {
      element.getSuggestedAccounts('bro');
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.firstCall.args[0].url,
        'test52/accounts/?o=DETAILS&q=%22bro%22'
      );
    });

    test('url with email and canSee changeId', () => {
      element.getSuggestedAccounts('bro', undefined, 341682 as NumericChangeId);
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.firstCall.args[0].url,
        'test53/accounts/?o=DETAILS&q=%22bro%22%20and%20cansee%3A341682'
      );
    });

    test('url with email and canSee changeId and isActive', () => {
      element.getSuggestedAccounts(
        'bro',
        undefined,
        341682 as NumericChangeId,
        true
      );
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.firstCall.args[0].url,
        'test54/accounts/?o=DETAILS&q=%22bro%22%20and%20' +
          'cansee%3A341682%20and%20is%3Aactive'
      );
    });
  });

  test('getAccount when resp is undefined clears cache', async () => {
    const cacheKey = '/accounts/self/detail';
    const account = createAccountDetailWithId();
    element._cache.set(cacheKey, account);
    const stub = sinon
      .stub(element._restApiHelper, 'fetchCacheURL')
      .callsFake(async req => {
        req.errFn!(undefined);
        return undefined;
      });
    assert.isTrue(element._cache.has(cacheKey));

    await element.getAccount();
    assert.isTrue(stub.called);
    assert.isFalse(element._cache.has(cacheKey));
  });

  test('getAccount when status is 403 clears cache', async () => {
    const cacheKey = '/accounts/self/detail';
    const account = createAccountDetailWithId();
    element._cache.set(cacheKey, account);
    const stub = sinon
      .stub(element._restApiHelper, 'fetchCacheURL')
      .callsFake(async req => {
        req.errFn!(new Response(undefined, {status: 403}));
        return undefined;
      });
    assert.isTrue(element._cache.has(cacheKey));

    await element.getAccount();
    assert.isTrue(stub.called);
    assert.isFalse(element._cache.has(cacheKey));
  });

  test('getAccount when resp is successful updates cache', async () => {
    const cacheKey = '/accounts/self/detail';
    const account = createAccountDetailWithId();
    const stub = sinon
      .stub(element._restApiHelper, 'fetchCacheURL')
      .callsFake(async () => {
        element._cache.set(cacheKey, account);
        return undefined;
      });
    assert.isFalse(element._cache.has(cacheKey));

    await element.getAccount();
    assert.isTrue(stub.called);
    assert.equal(element._cache.get(cacheKey), account);
  });

  const preferenceSetup = function (testJSON: unknown, loggedIn: boolean) {
    sinon
      .stub(element, 'getLoggedIn')
      .callsFake(() => Promise.resolve(loggedIn));
    sinon
      .stub(element._restApiHelper, 'fetchCacheURL')
      .callsFake(() => Promise.resolve(testJSON as ParsedJSON));
  };

  test('getPreferences returns correctly logged in', async () => {
    const testJSON = {diff_view: 'SIDE_BY_SIDE'};
    const loggedIn = true;

    preferenceSetup(testJSON, loggedIn);

    const obj = await element.getPreferences();
    assert.equal(obj!.diff_view, 'SIDE_BY_SIDE');
  });

  test('getPreferences returns correctly on larger screens logged in', async () => {
    const testJSON = {diff_view: 'UNIFIED_DIFF'};
    const loggedIn = true;

    preferenceSetup(testJSON, loggedIn);

    const obj = await element.getPreferences();
    assert.equal(obj!.diff_view, 'UNIFIED_DIFF');
  });

  test('getPreferences returns correctly on larger screens no login', async () => {
    const testJSON = {diff_view: 'UNIFIED_DIFF'};
    const loggedIn = false;

    preferenceSetup(testJSON, loggedIn);

    const obj = await element.getPreferences();
    assert.equal(obj!.diff_view, 'SIDE_BY_SIDE');
  });

  test('savPreferences normalizes download scheme', () => {
    const sendStub = sinon
      .stub(element._restApiHelper, 'send')
      .resolves(new Response());
    element.savePreferences({download_scheme: 'HTTP'});
    assert.isTrue(sendStub.called);
    assert.equal(
      (sendStub.lastCall.args[0].body as Partial<PreferencesInfo>)
        .download_scheme,
      'http'
    );
  });

  test('getDiffPreferences returns correct defaults', async () => {
    sinon.stub(element, 'getLoggedIn').callsFake(() => Promise.resolve(false));

    const obj = (await element.getDiffPreferences())!;
    assert.equal(obj.context, 10);
    assert.equal(obj.cursor_blink_rate, 0);
    assert.equal(obj.font_size, 12);
    assert.equal(obj.ignore_whitespace, 'IGNORE_NONE');
    assert.equal(obj.line_length, 100);
    assert.equal(obj.line_wrapping, false);
    assert.equal(obj.show_line_endings, true);
    assert.equal(obj.show_tabs, true);
    assert.equal(obj.show_whitespace_errors, true);
    assert.equal(obj.syntax_highlighting, true);
    assert.equal(obj.tab_size, 8);
  });

  test('saveDiffPreferences set show_tabs to false', () => {
    const sendStub = sinon.stub(element._restApiHelper, 'send');
    element.saveDiffPreferences({
      show_tabs: false,
      ignore_whitespace: 'IGNORE_NONE',
    });
    assert.isTrue(sendStub.called);
    assert.equal(
      (sendStub.lastCall.args[0].body as Partial<DiffPreferenceInput>)
        .show_tabs,
      false
    );
  });

  test('getEditPreferences returns correct defaults', async () => {
    sinon.stub(element, 'getLoggedIn').callsFake(() => Promise.resolve(false));

    const obj = (await element.getEditPreferences())!;
    assert.equal(obj.auto_close_brackets, false);
    assert.equal(obj.cursor_blink_rate, 0);
    assert.equal(obj.hide_line_numbers, false);
    assert.equal(obj.hide_top_menu, false);
    assert.equal(obj.indent_unit, 2);
    assert.equal(obj.indent_with_tabs, false);
    assert.equal(obj.key_map_type, 'DEFAULT');
    assert.equal(obj.line_length, 100);
    assert.equal(obj.line_wrapping, false);
    assert.equal(obj.match_brackets, true);
    assert.equal(obj.show_base, false);
    assert.equal(obj.show_tabs, true);
    assert.equal(obj.show_whitespace_errors, true);
    assert.equal(obj.syntax_highlighting, true);
    assert.equal(obj.tab_size, 8);
    assert.equal(obj.theme, 'DEFAULT');
  });

  test('saveEditPreferences set show_tabs to false', () => {
    const sendStub = sinon.stub(element._restApiHelper, 'send');
    element.saveEditPreferences({
      ...createDefaultEditPrefs(),
      show_tabs: false,
    });
    assert.isTrue(sendStub.called);
    assert.equal(
      (sendStub.lastCall.args[0].body as EditPreferencesInfo).show_tabs,
      false
    );
  });

  test('confirmEmail', () => {
    const sendStub = sinon.spy(element._restApiHelper, 'send');
    element.confirmEmail('foo');
    assert.isTrue(sendStub.calledOnce);
    assert.equal(sendStub.lastCall.args[0].method, HttpMethod.PUT);
    assert.equal(sendStub.lastCall.args[0].url, '/config/server/email.confirm');
    assert.deepEqual(sendStub.lastCall.args[0].body, {token: 'foo'});
  });

  test('setAccountStatus', async () => {
    const sendStub = sinon
      .stub(element._restApiHelper, 'send')
      .resolves('OOO' as unknown as ParsedJSON);
    element._cache.set('/accounts/self/detail', createAccountDetailWithId());
    await element.setAccountStatus('OOO');
    assert.isTrue(sendStub.calledOnce);
    assert.equal(sendStub.lastCall.args[0].method, HttpMethod.PUT);
    assert.equal(sendStub.lastCall.args[0].url, '/accounts/self/status');
    assert.deepEqual(sendStub.lastCall.args[0].body, {status: 'OOO'});
    assert.deepEqual(
      element._cache.get('/accounts/self/detail')!.status,
      'OOO'
    );
  });

  suite('draft comments', () => {
    test('_sendDiffDraftRequest pending requests tracked', async () => {
      const obj = element._pendingRequests;
      sinon
        .stub(element, '_getChangeURLAndSend')
        .callsFake(() => mockPromise());
      assert.notOk(element.hasPendingDiffDrafts());

      element._sendDiffDraftRequest(
        HttpMethod.PUT,
        123 as NumericChangeId,
        1 as PatchSetNum,
        {}
      );
      assert.equal(obj.sendDiffDraft.length, 1);
      assert.isTrue(!!element.hasPendingDiffDrafts());

      element._sendDiffDraftRequest(
        HttpMethod.PUT,
        123 as NumericChangeId,
        1 as PatchSetNum,
        {}
      );
      assert.equal(obj.sendDiffDraft.length, 2);
      assert.isTrue(!!element.hasPendingDiffDrafts());

      for (const promise of obj.sendDiffDraft) {
        (promise as MockPromise<void>).resolve();
      }

      await element.awaitPendingDiffDrafts();
      assert.equal(obj.sendDiffDraft.length, 0);
      assert.isFalse(!!element.hasPendingDiffDrafts());
    });

    suite('_failForCreate200', () => {
      test('_sendDiffDraftRequest checks for 200 on create', async () => {
        const sendPromise = Promise.resolve({} as unknown as ParsedJSON);
        sinon.stub(element, '_getChangeURLAndSend').returns(sendPromise);
        const failStub = sinon.stub(element, '_failForCreate200').resolves();
        await element._sendDiffDraftRequest(
          HttpMethod.PUT,
          123 as NumericChangeId,
          4 as PatchSetNum,
          {}
        );
        assert.isTrue(failStub.calledOnce);
        assert.isTrue(failStub.calledWithExactly(sendPromise));
      });

      test('_sendDiffDraftRequest no checks for 200 on non create', async () => {
        sinon.stub(element, '_getChangeURLAndSend').resolves();
        const failStub = sinon.stub(element, '_failForCreate200').resolves();
        await element._sendDiffDraftRequest(
          HttpMethod.PUT,
          123 as NumericChangeId,
          4 as PatchSetNum,
          {
            id: '123' as UrlEncodedCommentId,
          }
        );
        assert.isFalse(failStub.called);
      });

      test('_failForCreate200 fails on 200', async () => {
        const result = new Response(undefined, {
          status: 200,
          headers: {
            'Set-CoOkiE': 'secret',
            Innocuous: 'hello',
          },
        });
        const error = (await assertFails(
          element._failForCreate200(Promise.resolve(result))
        )) as Error;
        assert.isOk(error);
        assert.include(error.message, 'Saving draft resulted in HTTP 200');
        assert.include(error.message, 'hello');
        assert.notInclude(error.message, 'secret');
      });

      test('_failForCreate200 does not fail on 201', () => {
        const result = new Response(undefined, {status: 201});
        return element._failForCreate200(Promise.resolve(result));
      });
    });
  });

  test('saveChangeEdit', async () => {
    element._projectLookup = {1: Promise.resolve('test' as RepoName)};
    const change_num = 1 as NumericChangeId;
    const file_name = 'index.php';
    const file_contents = '<?php';
    const sendStub = sinon
      .stub(element._restApiHelper, 'send')
      .resolves([
        change_num,
        file_name,
        file_contents,
      ] as unknown as ParsedJSON);
    sinon
      .stub(element, 'getResponseObject')
      .resolves([
        change_num,
        file_name,
        file_contents,
      ] as unknown as ParsedJSON);
    element._cache.set(
      `/changes/${change_num}/edit/${file_name}`,
      {} as unknown as ParsedJSON
    );
    await element.saveChangeEdit(change_num, file_name, file_contents);
    assert.isTrue(sendStub.calledOnce);
    assert.equal(sendStub.lastCall.args[0].method, HttpMethod.PUT);
    assert.equal(
      sendStub.lastCall.args[0].url,
      '/changes/test~1/edit/' + file_name
    );
    assert.equal(sendStub.lastCall.args[0].body, file_contents);
  });

  test('putChangeCommitMessage', async () => {
    element._projectLookup = {1: Promise.resolve('test' as RepoName)};
    const change_num = 1 as NumericChangeId;
    const message = 'this is a commit message';
    const sendStub = sinon
      .stub(element._restApiHelper, 'send')
      .resolves([change_num, message] as unknown as ParsedJSON);
    sinon
      .stub(element, 'getResponseObject')
      .resolves([change_num, message] as unknown as ParsedJSON);
    element._cache.set(
      `/changes/${change_num}/message`,
      {} as unknown as ParsedJSON
    );
    await element.putChangeCommitMessage(change_num, message);
    assert.isTrue(sendStub.calledOnce);
    assert.equal(sendStub.lastCall.args[0].method, HttpMethod.PUT);
    assert.equal(sendStub.lastCall.args[0].url, '/changes/test~1/message');
    assert.deepEqual(sendStub.lastCall.args[0].body, {
      message,
    });
  });

  test('deleteChangeCommitMessage', async () => {
    element._projectLookup = {1: Promise.resolve('test' as RepoName)};
    const change_num = 1 as NumericChangeId;
    const messageId = 'abc' as ChangeMessageId;
    const sendStub = sinon
      .stub(element._restApiHelper, 'send')
      .resolves([change_num, messageId] as unknown as ParsedJSON);
    sinon
      .stub(element, 'getResponseObject')
      .resolves([change_num, messageId] as unknown as ParsedJSON);
    await element.deleteChangeCommitMessage(change_num, messageId);
    assert.isTrue(sendStub.calledOnce);
    assert.equal(sendStub.lastCall.args[0].method, HttpMethod.DELETE);
    assert.equal(sendStub.lastCall.args[0].url, '/changes/test~1/messages/abc');
  });

  test('startWorkInProgress', () => {
    const sendStub = sinon
      .stub(element, '_getChangeURLAndSend')
      .resolves('ok' as unknown as ParsedJSON);
    element.startWorkInProgress(42 as NumericChangeId);
    assert.isTrue(sendStub.calledOnce);
    assert.equal(sendStub.lastCall.args[0].changeNum, 42 as NumericChangeId);
    assert.equal(sendStub.lastCall.args[0].method, HttpMethod.POST);
    assert.isNotOk(sendStub.lastCall.args[0].patchNum);
    assert.equal(sendStub.lastCall.args[0].endpoint, '/wip');
    assert.deepEqual(sendStub.lastCall.args[0].body, {});

    element.startWorkInProgress(42 as NumericChangeId, 'revising...');
    assert.isTrue(sendStub.calledTwice);
    assert.equal(sendStub.lastCall.args[0].changeNum, 42 as NumericChangeId);
    assert.equal(sendStub.lastCall.args[0].method, HttpMethod.POST);
    assert.isNotOk(sendStub.lastCall.args[0].patchNum);
    assert.equal(sendStub.lastCall.args[0].endpoint, '/wip');
    assert.deepEqual(sendStub.lastCall.args[0].body, {
      message: 'revising...',
    });
  });

  test('deleteComment', async () => {
    const comment = createComment();
    const sendStub = sinon
      .stub(element, '_getChangeURLAndSend')
      .resolves(comment as unknown as ParsedJSON);
    const response = await element.deleteComment(
      123 as NumericChangeId,
      1 as PatchSetNum,
      '01234' as UrlEncodedCommentId,
      'removal reason'
    );
    assert.equal(response, comment);
    assert.isTrue(sendStub.calledOnce);
    assert.equal(sendStub.lastCall.args[0].changeNum, 123 as NumericChangeId);
    assert.equal(sendStub.lastCall.args[0].method, HttpMethod.POST);
    assert.equal(sendStub.lastCall.args[0].patchNum, 1 as PatchSetNum);
    assert.equal(sendStub.lastCall.args[0].endpoint, '/comments/01234/delete');
    assert.deepEqual(sendStub.lastCall.args[0].body, {
      reason: 'removal reason',
    });
  });

  test('createRepo encodes name', async () => {
    const sendStub = sinon.stub(element._restApiHelper, 'send').resolves();
    await element.createRepo({name: 'x/y' as RepoName});
    assert.isTrue(sendStub.calledOnce);
    assert.equal(sendStub.lastCall.args[0].url, '/projects/x%2Fy');
  });

  test('queryChangeFiles', async () => {
    const fetchStub = sinon.stub(element, '_getChangeURLAndFetch').resolves();
    await element.queryChangeFiles(42 as NumericChangeId, EDIT, 'test/path.js');
    assert.equal(fetchStub.lastCall.args[0].changeNum, 42 as NumericChangeId);
    assert.equal(
      fetchStub.lastCall.args[0].endpoint,
      '/files?q=test%2Fpath.js'
    );
    assert.equal(fetchStub.lastCall.args[0].revision, EDIT);
  });

  test('normal use', () => {
    const defaultQuery = '';

    assert.equal(
      element._getReposUrl('test', 25).toString(),
      [false, '/projects/?n=26&S=0&d=&m=test'].toString()
    );

    assert.equal(
      element._getReposUrl(undefined, 25).toString(),
      [false, `/projects/?n=26&S=0&d=&m=${defaultQuery}`].toString()
    );

    assert.equal(
      element._getReposUrl('test', 25, 25).toString(),
      [false, '/projects/?n=26&S=25&d=&m=test'].toString()
    );

    assert.equal(
      element._getReposUrl('inname:test', 25, 25).toString(),
      [true, '/projects/?n=26&S=25&query=inname%3Atest'].toString()
    );
  });

  test('invalidateReposCache', () => {
    const url = '/projects/?n=26&S=0&query=test';

    element._cache.set(url, {} as unknown as ParsedJSON);

    element.invalidateReposCache();

    assert.isUndefined(element._sharedFetchPromises.get(url));

    assert.isFalse(element._cache.has(url));
  });

  test('invalidateAccountsCache', () => {
    const url = '/accounts/self/detail';

    element._cache.set(url, {} as unknown as ParsedJSON);

    element.invalidateAccountsCache();

    assert.isUndefined(element._sharedFetchPromises.get(url));

    assert.isFalse(element._cache.has(url));
  });

  suite('getRepos', () => {
    const defaultQuery = '';
    let fetchCacheURLStub: sinon.SinonStub;
    setup(() => {
      fetchCacheURLStub = sinon
        .stub(element._restApiHelper, 'fetchCacheURL')
        .resolves([] as unknown as ParsedJSON);
    });

    test('normal use', () => {
      element.getRepos('test', 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=test'
      );

      element.getRepos(undefined, 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        `/projects/?n=26&S=0&d=&m=${defaultQuery}`
      );

      element.getRepos('test', 25, 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/projects/?n=26&S=25&d=&m=test'
      );
    });

    test('with blank', () => {
      element.getRepos('test/test', 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=test%2Ftest'
      );
    });

    test('with hyphen', () => {
      element.getRepos('foo-bar', 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=foo-bar'
      );
    });

    test('with leading hyphen', () => {
      element.getRepos('-bar', 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=-bar'
      );
    });

    test('with trailing hyphen', () => {
      element.getRepos('foo-bar-', 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=foo-bar-'
      );
    });

    test('with underscore', () => {
      element.getRepos('foo_bar', 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=foo_bar'
      );
    });

    test('with underscore', () => {
      element.getRepos('foo_bar', 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=foo_bar'
      );
    });

    test('hyphen only', () => {
      element.getRepos('-', 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&d=&m=-'
      );
    });

    test('using query', () => {
      element.getRepos('description:project', 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/projects/?n=26&S=0&query=description%3Aproject'
      );
    });
  });

  test('_getGroupsUrl normal use', () => {
    assert.equal(element._getGroupsUrl('test', 25), '/groups/?n=26&S=0&m=test');

    assert.equal(element._getGroupsUrl('', 25), '/groups/?n=26&S=0');

    assert.equal(
      element._getGroupsUrl('test', 25, 25),
      '/groups/?n=26&S=25&m=test'
    );
  });

  test('invalidateGroupsCache', () => {
    const url = '/groups/?n=26&S=0&m=test';

    element._cache.set(url, {} as unknown as ParsedJSON);

    element.invalidateGroupsCache();

    assert.isUndefined(element._sharedFetchPromises.get(url));

    assert.isFalse(element._cache.has(url));
  });

  suite('getGroups', () => {
    let fetchCacheURLStub: sinon.SinonStub;
    setup(() => {
      fetchCacheURLStub = sinon.stub(element._restApiHelper, 'fetchCacheURL');
    });

    test('normal use', () => {
      element.getGroups('test', 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/groups/?n=26&S=0&m=test'
      );

      element.getGroups('', 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url, '/groups/?n=26&S=0');

      element.getGroups('test', 25, 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/groups/?n=26&S=25&m=test'
      );
    });

    test('regex', () => {
      element.getGroups('^test.*', 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/groups/?n=26&S=0&r=%5Etest.*'
      );

      element.getGroups('^test.*', 25, 25);
      assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/groups/?n=26&S=25&r=%5Etest.*'
      );
    });
  });

  test('gerrit auth is used', () => {
    const fetchStub = sinon.stub(authService, 'fetch').resolves();
    element._restApiHelper.fetchJSON({url: 'foo'});
    assert(fetchStub.called);
  });

  test('getSuggestedAccounts does not return fetchJSON', async () => {
    const fetchJSONSpy = sinon.spy(element._restApiHelper, 'fetchJSON');
    const accts = await element.getSuggestedAccounts('');
    assert.isFalse(fetchJSONSpy.called);
    assert.equal(accts!.length, 0);
  });

  test('fetchJSON gets called by getSuggestedAccounts', async () => {
    const fetchJSONStub = sinon
      .stub(element._restApiHelper, 'fetchJSON')
      .resolves();
    await element.getSuggestedAccounts('own');
    assert.deepEqual(fetchJSONStub.lastCall.args[0].params, {
      q: '"own"',
      o: 'DETAILS',
    });
  });

  suite('getChangeDetail', () => {
    suite('change detail options', () => {
      let changeDetailStub: sinon.SinonStub;
      setup(() => {
        changeDetailStub = sinon
          .stub(element, '_getChangeDetail')
          .resolves({...createChange(), _number: 123 as NumericChangeId});
      });

      test('signed pushes disabled', async () => {
        sinon.stub(element, 'getConfig').resolves({
          ...createServerInfo(),
          receive: {enable_signed_push: undefined},
        });
        const change = await element.getChangeDetail(123 as NumericChangeId);
        assert.strictEqual(123, change!._number);
        const options = changeDetailStub.firstCall.args[1];
        assert.isNotOk(
          parseInt(options, 16) & (1 << ListChangesOption.PUSH_CERTIFICATES)
        );
      });

      test('signed pushes enabled', async () => {
        sinon.stub(element, 'getConfig').resolves({
          ...createServerInfo(),
          receive: {enable_signed_push: 'true'},
        });
        const change = await element.getChangeDetail(123 as NumericChangeId);
        assert.strictEqual(123, change!._number);
        const options = changeDetailStub.firstCall.args[1];
        assert.ok(
          parseInt(options, 16) & (1 << ListChangesOption.PUSH_CERTIFICATES)
        );
      });
    });

    test('GrReviewerUpdatesParser.parse is used', async () => {
      const changeInfo = createParsedChange();
      const parseStub = sinon
        .stub(GrReviewerUpdatesParser, 'parse')
        .resolves(changeInfo);
      const result = await element.getChangeDetail(42 as NumericChangeId);
      assert.isTrue(parseStub.calledOnce);
      assert.equal(result, changeInfo);
    });

    test('_getChangeDetail passes params to ETags decorator', async () => {
      const changeNum = 4321 as NumericChangeId;
      element._projectLookup[changeNum] = Promise.resolve('test' as RepoName);
      const expectedUrl = `${window.CANONICAL_PATH}/changes/test~4321/detail?O=516714`;
      const optionsStub = sinon.stub(element._etags, 'getOptions');
      const collectStub = sinon.stub(element._etags, 'collect');
      await element._getChangeDetail(changeNum, '516714');
      assert.isTrue(optionsStub.calledWithExactly(expectedUrl));
      assert.equal(collectStub.lastCall.args[0], expectedUrl);
    });

    test('_getChangeDetail calls errFn on 500', async () => {
      const errFn = sinon.stub();
      sinon.stub(element, 'getChangeActionURL').resolves('');
      sinon
        .stub(element._restApiHelper, 'fetchRawJSON')
        .resolves(new Response(undefined, {status: 500}));
      await element._getChangeDetail(123 as NumericChangeId, '516714', errFn);
      assert.isTrue(errFn.called);
    });

    test('_getChangeDetail populates _projectLookup', async () => {
      sinon.stub(element, 'getChangeActionURL').resolves('');
      sinon.stub(element._restApiHelper, 'fetchRawJSON').resolves(
        new Response(')]}\'{"_number":1,"project":"test"}', {
          status: 200,
        })
      );
      await element._getChangeDetail(1 as NumericChangeId, '516714');
      assert.equal(Object.keys(element._projectLookup).length, 1);
      const project = await element._projectLookup[1];
      assert.equal(project, 'test' as RepoName);
    });

    suite('_getChangeDetail ETag cache', () => {
      let requestUrl: string;
      let mockResponseSerial: string;
      let collectSpy: sinon.SinonSpy;

      setup(() => {
        requestUrl = '/foo/bar';
        const mockResponse = {foo: 'bar', baz: 42};
        mockResponseSerial = JSON_PREFIX + JSON.stringify(mockResponse);
        sinon.stub(element._restApiHelper, 'urlWithParams').returns(requestUrl);
        sinon.stub(element, 'getChangeActionURL').resolves(requestUrl);
        collectSpy = sinon.spy(element._etags, 'collect');
      });

      test('contributes to cache', async () => {
        const getPayloadSpy = sinon.spy(element._etags, 'getCachedPayload');
        sinon.stub(element._restApiHelper, 'fetchRawJSON').resolves(
          new Response(mockResponseSerial, {
            status: 200,
          })
        );

        await element._getChangeDetail(123 as NumericChangeId, '516714');
        assert.isFalse(getPayloadSpy.called);
        assert.isTrue(collectSpy.calledOnce);
        const cachedResponse = element._etags.getCachedPayload(requestUrl);
        assert.equal(cachedResponse, mockResponseSerial);
      });

      test('uses cache on HTTP 304', async () => {
        const getPayloadStub = sinon.stub(element._etags, 'getCachedPayload');
        getPayloadStub.returns(mockResponseSerial);
        sinon.stub(element._restApiHelper, 'fetchRawJSON').resolves(
          new Response(undefined, {
            status: 304,
          })
        );

        await element._getChangeDetail(123 as NumericChangeId, '');
        assert.isFalse(collectSpy.called);
        assert.isTrue(getPayloadStub.calledOnce);
      });
    });
  });

  test('setInProjectLookup', async () => {
    await element.setInProjectLookup(
      555 as NumericChangeId,
      'project' as RepoName
    );
    const project = await element.getFromProjectLookup(555 as NumericChangeId);
    assert.deepEqual(project, 'project' as RepoName);
  });

  suite('getFromProjectLookup', () => {
    test('getChange succeeds, no project', async () => {
      sinon.stub(element, 'getChange').resolves(null);
      const val = await element.getFromProjectLookup(555 as NumericChangeId);
      assert.strictEqual(val, undefined);
    });

    test('getChange succeeds with project', async () => {
      sinon
        .stub(element, 'getChange')
        .resolves({...createChange(), project: 'project' as RepoName});
      const projectLookup = element.getFromProjectLookup(
        555 as NumericChangeId
      );
      const val = await projectLookup;
      assert.equal(val, 'project' as RepoName);
      assert.deepEqual(element._projectLookup, {'555': projectLookup});
    });
  });

  suite('getChanges populates _projectLookup', () => {
    test('multiple queries', async () => {
      sinon.stub(element._restApiHelper, 'fetchJSON').resolves([
        [
          {_number: 1, project: 'test'},
          {_number: 2, project: 'test'},
        ],
        [{_number: 3, project: 'test/test'}],
      ] as unknown as ParsedJSON);
      // When opt_query instanceof Array, fetchJSON returns
      // Array<Array<Object>>.
      await element.getChangesForMultipleQueries(undefined, []);
      assert.equal(Object.keys(element._projectLookup).length, 3);
      const project1 = await element.getFromProjectLookup(1 as NumericChangeId);
      assert.equal(project1, 'test' as RepoName);
      const project2 = await element.getFromProjectLookup(2 as NumericChangeId);
      assert.equal(project2, 'test' as RepoName);
      const project3 = await element.getFromProjectLookup(3 as NumericChangeId);
      assert.equal(project3, 'test/test' as RepoName);
    });

    test('no query', async () => {
      sinon.stub(element._restApiHelper, 'fetchJSON').resolves([
        {_number: 1, project: 'test'},
        {_number: 2, project: 'test'},
        {_number: 3, project: 'test/test'},
      ] as unknown as ParsedJSON);

      // When opt_query !instanceof Array, fetchJSON returns Array<Object>.
      await element.getChanges();
      assert.equal(Object.keys(element._projectLookup).length, 3);
      const project1 = await element.getFromProjectLookup(1 as NumericChangeId);
      assert.equal(project1, 'test' as RepoName);
      const project2 = await element.getFromProjectLookup(2 as NumericChangeId);
      assert.equal(project2, 'test' as RepoName);
      const project3 = await element.getFromProjectLookup(3 as NumericChangeId);
      assert.equal(project3, 'test/test' as RepoName);
    });
  });

  test('getDetailedChangesWithActions', async () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;
    const getChangesStub = sinon
      .stub(element, 'getChanges')
      .callsFake((changesPerPage, query, offset, options) => {
        assert.isUndefined(changesPerPage);
        assert.strictEqual(query, 'change:1 OR change:2');
        assert.isUndefined(offset);
        assert.strictEqual(options, EXPECTED_QUERY_OPTIONS);
        return Promise.resolve([]);
      });
    await element.getDetailedChangesWithActions([c1._number, c2._number]);
    assert.isTrue(getChangesStub.calledOnce);
  });

  test('_getChangeURLAndFetch', async () => {
    element._projectLookup = {1: Promise.resolve('test' as RepoName)};
    const fetchStub = sinon
      .stub(element._restApiHelper, 'fetchJSON')
      .resolves();
    const req = {
      changeNum: 1 as NumericChangeId,
      endpoint: '/test',
      revision: 1 as RevisionId,
    };
    await element._getChangeURLAndFetch(req);
    assert.equal(
      fetchStub.lastCall.args[0].url,
      '/changes/test~1/revisions/1/test'
    );
  });

  test('_getChangeURLAndSend', async () => {
    element._projectLookup = {1: Promise.resolve('test' as RepoName)};
    const sendStub = sinon.stub(element._restApiHelper, 'send').resolves();

    const req = {
      changeNum: 1 as NumericChangeId,
      method: HttpMethod.POST,
      patchNum: 1 as PatchSetNum,
      endpoint: '/test',
    };
    await element._getChangeURLAndSend(req);
    assert.isTrue(sendStub.calledOnce);
    assert.equal(sendStub.lastCall.args[0].method, HttpMethod.POST);
    assert.equal(
      sendStub.lastCall.args[0].url,
      '/changes/test~1/revisions/1/test'
    );
  });

  suite('reading responses', () => {
    test('_readResponsePayload', async () => {
      const mockObject = {foo: 'bar', baz: 'foo'} as unknown as ParsedJSON;
      const serial = JSON_PREFIX + JSON.stringify(mockObject);
      const response = new Response(serial);
      const payload = await readResponsePayload(response);
      assert.deepEqual(payload.parsed, mockObject);
      assert.equal(payload.raw, serial);
    });

    test('_parsePrefixedJSON', () => {
      const obj = {x: 3, y: {z: 4}, w: 23} as unknown as ParsedJSON;
      const serial = JSON_PREFIX + JSON.stringify(obj);
      const result = parsePrefixedJSON(serial);
      assert.deepEqual(result, obj);
    });
  });

  test('setChangeTopic', async () => {
    const sendSpy = sinon.spy(element, '_getChangeURLAndSend');
    await element.setChangeTopic(123 as NumericChangeId, 'foo-bar');
    assert.isTrue(sendSpy.calledOnce);
    assert.deepEqual(sendSpy.lastCall.args[0].body, {topic: 'foo-bar'});
  });

  test('setChangeHashtag', async () => {
    const sendSpy = sinon.spy(element, '_getChangeURLAndSend');
    await element.setChangeHashtag(123 as NumericChangeId, {
      add: ['foo-bar' as Hashtag],
    });
    assert.isTrue(sendSpy.calledOnce);
    assert.sameDeepMembers(
      (sendSpy.lastCall.args[0].body! as HashtagsInput).add!,
      ['foo-bar']
    );
  });

  test('generateAccountHttpPassword', async () => {
    const sendSpy = sinon.spy(element._restApiHelper, 'send');
    await element.generateAccountHttpPassword();
    assert.isTrue(sendSpy.calledOnce);
    assert.deepEqual(sendSpy.lastCall.args[0].body, {generate: true});
  });

  suite('getChangeFiles', () => {
    test('patch only', async () => {
      const fetchStub = sinon.stub(element, '_getChangeURLAndFetch').resolves();
      const range = {basePatchNum: PARENT, patchNum: 2 as RevisionPatchSetNum};
      await element.getChangeFiles(123 as NumericChangeId, range);
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(
        fetchStub.lastCall.args[0].revision,
        2 as RevisionPatchSetNum
      );
      assert.isNotOk(fetchStub.lastCall.args[0].params);
    });

    test('simple range', async () => {
      const fetchStub = sinon.stub(element, '_getChangeURLAndFetch').resolves();
      const range = {
        basePatchNum: 4 as BasePatchSetNum,
        patchNum: 5 as RevisionPatchSetNum,
      };
      await element.getChangeFiles(123 as NumericChangeId, range);
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(fetchStub.lastCall.args[0].revision, 5 as RevisionId);
      assert.isOk(fetchStub.lastCall.args[0].params);
      assert.equal(fetchStub.lastCall.args[0].params!.base, 4);
      assert.isNotOk(fetchStub.lastCall.args[0].params!.parent);
    });

    test('parent index', async () => {
      const fetchStub = sinon.stub(element, '_getChangeURLAndFetch').resolves();
      const range = {
        basePatchNum: -3 as BasePatchSetNum,
        patchNum: 5 as RevisionPatchSetNum,
      };
      await element.getChangeFiles(123 as NumericChangeId, range);
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(fetchStub.lastCall.args[0].revision, 5 as RevisionId);
      assert.isOk(fetchStub.lastCall.args[0].params);
      assert.isNotOk(fetchStub.lastCall.args[0].params!.base);
      assert.equal(fetchStub.lastCall.args[0].params!.parent, 3);
    });
  });

  suite('getDiff', () => {
    test('patchOnly', async () => {
      const fetchStub = sinon.stub(element, '_getChangeURLAndFetch').resolves();
      await element.getDiff(
        123 as NumericChangeId,
        PARENT,
        2 as PatchSetNum,
        'foo/bar.baz'
      );
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(fetchStub.lastCall.args[0].revision, 2 as RevisionId);
      assert.isOk(fetchStub.lastCall.args[0].params);
      assert.isNotOk(fetchStub.lastCall.args[0].params!.parent);
      assert.isNotOk(fetchStub.lastCall.args[0].params!.base);
    });

    test('simple range', async () => {
      const fetchStub = sinon.stub(element, '_getChangeURLAndFetch').resolves();
      await element.getDiff(
        123 as NumericChangeId,
        4 as PatchSetNum,
        5 as PatchSetNum,
        'foo/bar.baz'
      );
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(fetchStub.lastCall.args[0].revision, 5 as RevisionId);
      assert.isOk(fetchStub.lastCall.args[0].params);
      assert.isNotOk(fetchStub.lastCall.args[0].params!.parent);
      assert.equal(fetchStub.lastCall.args[0].params!.base, 4);
    });

    test('parent index', async () => {
      const fetchStub = sinon.stub(element, '_getChangeURLAndFetch').resolves();
      await element.getDiff(
        123 as NumericChangeId,
        -3 as PatchSetNum,
        5 as PatchSetNum,
        'foo/bar.baz'
      );
      assert.isTrue(fetchStub.calledOnce);
      assert.equal(fetchStub.lastCall.args[0].revision, 5 as RevisionId);
      assert.isOk(fetchStub.lastCall.args[0].params);
      assert.isNotOk(fetchStub.lastCall.args[0].params!.base);
      assert.equal(fetchStub.lastCall.args[0].params!.parent, 3);
    });
  });

  test('getDashboard', () => {
    const fetchCacheURLStub = sinon.stub(
      element._restApiHelper,
      'fetchCacheURL'
    );
    element.getDashboard(
      'gerrit/project' as RepoName,
      'default:main' as DashboardId
    );
    assert.isTrue(fetchCacheURLStub.calledOnce);
    assert.equal(
      fetchCacheURLStub.lastCall.args[0].url,
      '/projects/gerrit%2Fproject/dashboards/default%3Amain'
    );
  });

  test('getFileContent', async () => {
    sinon.stub(element, '_getChangeURLAndSend').resolves(
      new Response(undefined, {
        status: 200,
        headers: {
          'X-FYI-Content-Type': 'text/java',
        },
      }) as unknown as ParsedJSON
    );

    sinon
      .stub(element, 'getResponseObject')
      .resolves('new content' as unknown as ParsedJSON);

    const edit = await element.getFileContent(
      1 as NumericChangeId,
      'tst/path',
      'EDIT' as PatchSetNum
    );

    assert.deepEqual(edit, {
      content: 'new content',
      type: 'text/java',
      ok: true,
    });

    const normal = await element.getFileContent(
      1 as NumericChangeId,
      'tst/path',
      '3' as PatchSetNum
    );
    assert.deepEqual(normal, {
      content: 'new content',
      type: 'text/java',
      ok: true,
    });
  });

  test('getFileContent suppresses 404s', async () => {
    const res404 = new Response(undefined, {status: 404});
    const res500 = new Response(undefined, {status: 500});
    const spy = sinon.spy();
    addListenerForTest(document, 'server-error', spy);
    const authStub = sinon.stub(authService, 'fetch').resolves(res404);
    sinon.stub(element, '_changeBaseURL').resolves('');
    await element.getFileContent(
      1 as NumericChangeId,
      'tst/path',
      1 as PatchSetNum
    );
    await waitEventLoop();
    assert.isFalse(spy.called);
    authStub.reset();
    authStub.resolves(res500);
    await element.getFileContent(
      1 as NumericChangeId,
      'tst/path',
      1 as PatchSetNum
    );
    assert.isTrue(spy.called);
    assert.notEqual(spy.lastCall.args[0].detail.response.status, 404);
  });

  test('getChangeFilesOrEditFiles is edit-sensitive', async () => {
    const getChangeFilesStub = sinon
      .stub(element, 'getChangeFiles')
      .resolves({});
    const getChangeEditFilesStub = sinon
      .stub(element, 'getChangeEditFiles')
      .resolves({files: {}});

    await element.getChangeOrEditFiles(1 as NumericChangeId, {
      basePatchNum: PARENT,
      patchNum: EDIT,
    });
    assert.isTrue(getChangeEditFilesStub.calledOnce);
    assert.isFalse(getChangeFilesStub.called);
    await element.getChangeOrEditFiles(1 as NumericChangeId, {
      basePatchNum: PARENT,
      patchNum: 1 as RevisionPatchSetNum,
    });
    assert.isTrue(getChangeEditFilesStub.calledOnce);
    assert.isTrue(getChangeFilesStub.calledOnce);
  });

  test('_fetch forwards request and logs', async () => {
    const logStub = sinon.stub(element._restApiHelper, '_logCall');
    const response = new Response(undefined, {status: 404});
    const url = 'my url';
    const fetchOptions = {method: 'DELETE'};
    sinon.stub(authService, 'fetch').resolves(response);
    const startTime = 123;
    sinon.stub(Date, 'now').returns(startTime);
    const req = {url, fetchOptions};
    await element._restApiHelper.fetch(req);
    assert.isTrue(logStub.calledOnce);
    assert.isTrue(logStub.calledWith(req, startTime, response.status));
  });

  test('_logCall only reports requests with anonymized URLss', async () => {
    sinon.stub(Date, 'now').returns(200);
    const handler = sinon.stub();
    addListenerForTest(document, 'gr-rpc-log', handler);

    element._restApiHelper._logCall({url: 'url'}, 100, 200);
    assert.isFalse(handler.called);

    element._restApiHelper._logCall(
      {url: 'url', anonymizedUrl: 'not url'},
      100,
      200
    );
    await waitEventLoop();
    assert.isTrue(handler.calledOnce);
  });

  test('ported comment errors do not trigger error dialog', () => {
    const change = createChange();
    const handler = sinon.stub();
    addListenerForTest(document, 'server-error', handler);
    sinon.stub(element._restApiHelper, 'fetchJSON').resolves({
      ok: false,
    } as unknown as ParsedJSON);

    element.getPortedComments(change._number, CURRENT);

    assert.isFalse(handler.called);
  });

  test('ported drafts are not requested user is not logged in', () => {
    const change = createChange();
    sinon.stub(element, 'getLoggedIn').resolves(false);
    const getChangeURLAndFetchStub = sinon.stub(
      element,
      '_getChangeURLAndFetch'
    );

    element.getPortedDrafts(change._number, CURRENT);

    assert.isFalse(getChangeURLAndFetchStub.called);
  });

  test('saveChangeStarred', async () => {
    sinon.stub(element, 'getFromProjectLookup').resolves('test' as RepoName);
    const sendStub = sinon.stub(element._restApiHelper, 'send').resolves();

    await element.saveChangeStarred(123 as NumericChangeId, true);
    assert.isTrue(sendStub.calledOnce);
    assert.deepEqual(sendStub.lastCall.args[0], {
      method: HttpMethod.PUT,
      url: '/accounts/self/starred.changes/test~123',
      anonymizedUrl: '/accounts/self/starred.changes/*',
    });

    await element.saveChangeStarred(456 as NumericChangeId, false);
    assert.isTrue(sendStub.calledTwice);
    assert.deepEqual(sendStub.lastCall.args[0], {
      method: HttpMethod.DELETE,
      url: '/accounts/self/starred.changes/test~456',
      anonymizedUrl: '/accounts/self/starred.changes/*',
    });
  });
});
