/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma.js';
import {addListenerForTest, mockPromise, stubAuth} from '../../test/test-utils.js';
import {GrReviewerUpdatesParser} from '../../elements/shared/gr-rest-api-interface/gr-reviewer-updates-parser.js';
import {ListChangesOption, listChangesOptionsToHex} from '../../utils/change-util.js';
import {getAppContext} from '../app-context.js';
import {createChange} from '../../test/test-data-generators.js';
import {CURRENT} from '../../utils/patch-set-util.js';
import {parsePrefixedJSON, readResponsePayload} from '../../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper.js';
import {JSON_PREFIX} from '../../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper.js';
import {GrRestApiServiceImpl} from './gr-rest-api-impl.js';

const EXPECTED_QUERY_OPTIONS = listChangesOptionsToHex(
    ListChangesOption.CHANGE_ACTIONS,
    ListChangesOption.CURRENT_ACTIONS,
    ListChangesOption.CURRENT_REVISION,
    ListChangesOption.DETAILED_LABELS,
    ListChangesOption.SUBMIT_REQUIREMENTS
);

suite('gr-rest-api-service-impl tests', () => {
  let element;

  let ctr = 0;
  let originalCanonicalPath;

  setup(() => {
    // Modify CANONICAL_PATH to effectively reset cache.
    ctr += 1;
    originalCanonicalPath = window.CANONICAL_PATH;
    window.CANONICAL_PATH = `test${ctr}`;

    const testJSON = ')]}\'\n{"hello": "bonjour"}';
    sinon.stub(window, 'fetch').returns(Promise.resolve({
      ok: true,
      text() {
        return Promise.resolve(testJSON);
      },
    }));
    // fake auth
    sinon.stub(getAppContext().authService, 'authCheck')
        .returns(Promise.resolve(true));
    element = new GrRestApiServiceImpl(
        getAppContext().authService,
        getAppContext().flagsService
    );
    element._projectLookup = {};
  });

  teardown(() => {
    window.CANONICAL_PATH = originalCanonicalPath;
  });

  test('parent diff comments are properly grouped', () => {
    sinon.stub(element._restApiHelper, 'fetchJSON')
        .callsFake(() => Promise.resolve({
          '/COMMIT_MSG': [],
          'sieve.go': [
            {
              updated: '2017-02-03 22:32:28.000000000',
              message: 'this isn’t quite right',
            },
            {
              side: 'PARENT',
              message: 'how did this work in the first place?',
              updated: '2017-02-03 22:33:28.000000000',
            },
          ],
        }));
    return element._getDiffComments('42', '', undefined, 'PARENT', 1,
        'sieve.go').then(
        obj => {
          assert.equal(obj.baseComments.length, 1);
          assert.deepEqual(obj.baseComments[0], {
            side: 'PARENT',
            message: 'how did this work in the first place?',
            path: 'sieve.go',
            updated: '2017-02-03 22:33:28.000000000',
          });
          assert.equal(obj.comments.length, 1);
          assert.deepEqual(obj.comments[0], {
            message: 'this isn’t quite right',
            path: 'sieve.go',
            updated: '2017-02-03 22:32:28.000000000',
          });
        });
  });

  test('_setRange', () => {
    const comments = [
      {
        id: 1,
        side: 'PARENT',
        message: 'how did this work in the first place?',
        updated: '2017-02-03 22:32:28.000000000',
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
      {
        id: 2,
        in_reply_to: 1,
        message: 'this isn’t quite right',
        updated: '2017-02-03 22:33:28.000000000',
      },
    ];
    const expectedResult = {
      id: 2,
      in_reply_to: 1,
      message: 'this isn’t quite right',
      updated: '2017-02-03 22:33:28.000000000',
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
    const comments = [
      {
        id: 3,
        in_reply_to: 2,
        message: 'this isn’t quite right either',
        updated: '2017-02-03 22:34:28.000000000',
      },
      {
        id: 2,
        in_reply_to: 1,
        message: 'this isn’t quite right',
        updated: '2017-02-03 22:33:28.000000000',
      },
      {
        id: 1,
        side: 'PARENT',
        message: 'how did this work in the first place?',
        updated: '2017-02-03 22:32:28.000000000',
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
    ];
    const expectedResult = [
      {
        id: 1,
        side: 'PARENT',
        message: 'how did this work in the first place?',
        updated: '2017-02-03 22:32:28.000000000',
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
      {
        id: 2,
        in_reply_to: 1,
        message: 'this isn’t quite right',
        updated: '2017-02-03 22:33:28.000000000',
        range: {
          start_line: 1,
          start_character: 1,
          end_line: 2,
          end_character: 1,
        },
      },
      {
        id: 3,
        in_reply_to: 2,
        message: 'this isn’t quite right either',
        updated: '2017-02-03 22:34:28.000000000',
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

  test('differing patch diff comments are properly grouped', () => {
    sinon.stub(element, 'getFromProjectLookup')
        .returns(Promise.resolve('test'));
    sinon.stub(element._restApiHelper, 'fetchJSON').callsFake(request => {
      const url = request.url;
      if (url === '/changes/test~42/revisions/1') {
        return Promise.resolve({
          '/COMMIT_MSG': [],
          'sieve.go': [
            {
              message: 'this isn’t quite right',
              updated: '2017-02-03 22:32:28.000000000',
            },
            {
              side: 'PARENT',
              message: 'how did this work in the first place?',
              updated: '2017-02-03 22:33:28.000000000',
            },
          ],
        });
      } else if (url === '/changes/test~42/revisions/2') {
        return Promise.resolve({
          '/COMMIT_MSG': [],
          'sieve.go': [
            {
              message: 'What on earth are you thinking, here?',
              updated: '2017-02-03 22:32:28.000000000',
            },
            {
              side: 'PARENT',
              message: 'Yeah not sure how this worked either?',
              updated: '2017-02-03 22:33:28.000000000',
            },
            {
              message: '¯\\_(ツ)_/¯',
              updated: '2017-02-04 22:33:28.000000000',
            },
          ],
        });
      }
    });
    return element._getDiffComments('42', '', undefined, 1, 2, 'sieve.go').then(
        obj => {
          assert.equal(obj.baseComments.length, 1);
          assert.deepEqual(obj.baseComments[0], {
            message: 'this isn’t quite right',
            path: 'sieve.go',
            updated: '2017-02-03 22:32:28.000000000',
          });
          assert.equal(obj.comments.length, 2);
          assert.deepEqual(obj.comments[0], {
            message: 'What on earth are you thinking, here?',
            path: 'sieve.go',
            updated: '2017-02-03 22:32:28.000000000',
          });
          assert.deepEqual(obj.comments[1], {
            message: '¯\\_(ツ)_/¯',
            path: 'sieve.go',
            updated: '2017-02-04 22:33:28.000000000',
          });
        });
  });

  test('server error', () => {
    const getResponseObjectStub = sinon.stub(element, 'getResponseObject');
    stubAuth('fetch').returns(Promise.resolve({ok: false}));
    const serverErrorEventPromise = new Promise(resolve => {
      addListenerForTest(document, 'server-error', resolve);
    });

    return Promise.all([element._restApiHelper.fetchJSON({}).then(response => {
      assert.isUndefined(response);
      assert.isTrue(getResponseObjectStub.notCalled);
    }), serverErrorEventPromise]);
  });

  test('legacy n,z key in change url is replaced', async () => {
    sinon.stub(element, 'getConfig').callsFake(async () => {
      return {};
    });
    const stub = sinon.stub(element._restApiHelper, 'fetchJSON')
        .returns(Promise.resolve([]));
    await element.getChanges(1, null, 'n,z');
    assert.equal(stub.lastCall.args[0].params.S, 0);
  });

  test('saveDiffPreferences invalidates cache line', () => {
    const cacheKey = '/accounts/self/preferences.diff';
    const sendStub = sinon.stub(element._restApiHelper, 'send');
    element._cache.set(cacheKey, {tab_size: 4});
    element.saveDiffPreferences({tab_size: 8});
    assert.isTrue(sendStub.called);
    assert.isFalse(element._restApiHelper._cache.has(cacheKey));
  });

  test('getAccount when resp is null does not add to cache', async () => {
    const cacheKey = '/accounts/self/detail';
    const stub = sinon.stub(element._restApiHelper, 'fetchCacheURL')
        .callsFake(() => Promise.resolve());

    await element.getAccount();
    assert.isTrue(stub.called);
    assert.isFalse(element._restApiHelper._cache.has(cacheKey));

    element._restApiHelper._cache.set(cacheKey, 'fake cache');
    stub.lastCall.args[0].errFn();
  });

  test('getAccount does not add to cache when status is 403', async () => {
    const cacheKey = '/accounts/self/detail';
    const stub = sinon.stub(element._restApiHelper, 'fetchCacheURL')
        .callsFake(() => Promise.resolve());

    await element.getAccount();
    assert.isTrue(stub.called);
    assert.isFalse(element._restApiHelper._cache.has(cacheKey));

    element._cache.set(cacheKey, 'fake cache');
    stub.lastCall.args[0].errFn({status: 403});
  });

  test('getAccount when resp is successful', async () => {
    const cacheKey = '/accounts/self/detail';
    const stub = sinon.stub(element._restApiHelper, 'fetchCacheURL').callsFake(
        () => Promise.resolve());

    await element.getAccount();

    element._restApiHelper._cache.set(cacheKey, 'fake cache');
    assert.isTrue(stub.called);
    assert.equal(element._restApiHelper._cache.get(cacheKey), 'fake cache');
    stub.lastCall.args[0].errFn({});
  });

  const preferenceSetup = function(testJSON, loggedIn) {
    sinon.stub(element, 'getLoggedIn')
        .callsFake(() => Promise.resolve(loggedIn));
    sinon.stub(
        element._restApiHelper,
        'fetchCacheURL')
        .callsFake(() => Promise.resolve(testJSON));
  };

  test('getPreferences returns correctly logged in',
      () => {
        const testJSON = {diff_view: 'SIDE_BY_SIDE'};
        const loggedIn = true;

        preferenceSetup(testJSON, loggedIn);

        return element.getPreferences().then(obj => {
          assert.equal(obj.diff_view, 'SIDE_BY_SIDE');
        });
      });

  test('getPreferences returns correctly on larger screens logged in',
      () => {
        const testJSON = {diff_view: 'UNIFIED_DIFF'};
        const loggedIn = true;

        preferenceSetup(testJSON, loggedIn);

        return element.getPreferences().then(obj => {
          assert.equal(obj.diff_view, 'UNIFIED_DIFF');
        });
      });

  test('getPreferences returns correctly on larger screens not logged in',
      () => {
        const testJSON = {diff_view: 'UNIFIED_DIFF'};
        const loggedIn = false;

        preferenceSetup(testJSON, loggedIn);

        return element.getPreferences().then(obj => {
          assert.equal(obj.diff_view, 'SIDE_BY_SIDE');
        });
      });

  test('savPreferences normalizes download scheme', () => {
    const sendStub = sinon.stub(element._restApiHelper, 'send').returns(
        Promise.resolve(new Response()));
    element.savePreferences({download_scheme: 'HTTP'});
    assert.isTrue(sendStub.called);
    assert.equal(sendStub.lastCall.args[0].body.download_scheme, 'http');
  });

  test('getDiffPreferences returns correct defaults', () => {
    sinon.stub(element, 'getLoggedIn').callsFake(() => Promise.resolve(false));

    return element.getDiffPreferences().then(obj => {
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
  });

  test('saveDiffPreferences set show_tabs to false', () => {
    const sendStub = sinon.stub(element._restApiHelper, 'send');
    element.saveDiffPreferences({show_tabs: false});
    assert.isTrue(sendStub.called);
    assert.equal(sendStub.lastCall.args[0].body.show_tabs, false);
  });

  test('getEditPreferences returns correct defaults', () => {
    sinon.stub(element, 'getLoggedIn').callsFake(() => Promise.resolve(false));

    return element.getEditPreferences().then(obj => {
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
  });

  test('saveEditPreferences set show_tabs to false', () => {
    const sendStub = sinon.stub(element._restApiHelper, 'send');
    element.saveEditPreferences({show_tabs: false});
    assert.isTrue(sendStub.called);
    assert.equal(sendStub.lastCall.args[0].body.show_tabs, false);
  });

  test('confirmEmail', () => {
    const sendStub = sinon.spy(element._restApiHelper, 'send');
    element.confirmEmail('foo');
    assert.isTrue(sendStub.calledOnce);
    assert.equal(sendStub.lastCall.args[0].method, 'PUT');
    assert.equal(sendStub.lastCall.args[0].url,
        '/config/server/email.confirm');
    assert.deepEqual(sendStub.lastCall.args[0].body, {token: 'foo'});
  });

  test('setAccountStatus', () => {
    const sendStub = sinon.stub(element._restApiHelper, 'send')
        .returns(Promise.resolve('OOO'));
    element._cache.set('/accounts/self/detail', {});
    return element.setAccountStatus('OOO').then(() => {
      assert.isTrue(sendStub.calledOnce);
      assert.equal(sendStub.lastCall.args[0].method, 'PUT');
      assert.equal(sendStub.lastCall.args[0].url,
          '/accounts/self/status');
      assert.deepEqual(sendStub.lastCall.args[0].body,
          {status: 'OOO'});
      assert.deepEqual(
          element._restApiHelper._cache.get('/accounts/self/detail'),
          {status: 'OOO'});
    });
  });

  suite('draft comments', () => {
    test('_sendDiffDraftRequest pending requests tracked', () => {
      const obj = element._pendingRequests;
      sinon.stub(element, '_getChangeURLAndSend')
          .callsFake(() => mockPromise());
      assert.notOk(element.hasPendingDiffDrafts());

      element._sendDiffDraftRequest(null, null, null, {});
      assert.equal(obj.sendDiffDraft.length, 1);
      assert.isTrue(!!element.hasPendingDiffDrafts());

      element._sendDiffDraftRequest(null, null, null, {});
      assert.equal(obj.sendDiffDraft.length, 2);
      assert.isTrue(!!element.hasPendingDiffDrafts());

      for (const promise of obj.sendDiffDraft) {
        promise.resolve();
      }

      return element.awaitPendingDiffDrafts().then(() => {
        assert.equal(obj.sendDiffDraft.length, 0);
        assert.isFalse(!!element.hasPendingDiffDrafts());
      });
    });

    suite('_failForCreate200', () => {
      test('_sendDiffDraftRequest checks for 200 on create', () => {
        const sendPromise = Promise.resolve();
        sinon.stub(element, '_getChangeURLAndSend').returns(sendPromise);
        const failStub = sinon.stub(element, '_failForCreate200')
            .returns(Promise.resolve());
        return element._sendDiffDraftRequest('PUT', 123, 4, {}).then(() => {
          assert.isTrue(failStub.calledOnce);
          assert.isTrue(failStub.calledWithExactly(sendPromise));
        });
      });

      test('_sendDiffDraftRequest no checks for 200 on non create', () => {
        sinon.stub(element, '_getChangeURLAndSend')
            .returns(Promise.resolve());
        const failStub = sinon.stub(element, '_failForCreate200')
            .returns(Promise.resolve());
        return element._sendDiffDraftRequest('PUT', 123, 4, {id: '123'})
            .then(() => {
              assert.isFalse(failStub.called);
            });
      });

      test('_failForCreate200 fails on 200', () => {
        const result = {
          ok: true,
          status: 200,
          headers: {
            entries: () => [
              ['Set-CoOkiE', 'secret'],
              ['Innocuous', 'hello'],
            ],
          },
        };
        return element._failForCreate200(Promise.resolve(result))
            .then(() => {
              assert.fail('Error expected.');
            })
            .catch(e => {
              assert.isOk(e);
              assert.include(e.message, 'Saving draft resulted in HTTP 200');
              assert.include(e.message, 'hello');
              assert.notInclude(e.message, 'secret');
            });
      });

      test('_failForCreate200 does not fail on 201', () => {
        const result = {
          ok: true,
          status: 201,
          headers: {entries: () => []},
        };
        return element._failForCreate200(Promise.resolve(result));
      });
    });
  });

  test('saveChangeEdit', () => {
    element._projectLookup = {1: Promise.resolve('test')};
    const change_num = '1';
    const file_name = 'index.php';
    const file_contents = '<?php';
    sinon.stub(element._restApiHelper, 'send').returns(
        Promise.resolve([change_num, file_name, file_contents]));
    sinon.stub(element, 'getResponseObject')
        .returns(Promise.resolve([change_num, file_name, file_contents]));
    element._cache.set('/changes/' + change_num + '/edit/' + file_name, {});
    return element.saveChangeEdit(change_num, file_name, file_contents)
        .then(() => {
          assert.isTrue(element._restApiHelper.send.calledOnce);
          assert.equal(element._restApiHelper.send.lastCall.args[0].method,
              'PUT');
          assert.equal(element._restApiHelper.send.lastCall.args[0].url,
              '/changes/test~1/edit/' + file_name);
          assert.equal(element._restApiHelper.send.lastCall.args[0].body,
              file_contents);
        });
  });

  test('putChangeCommitMessage', () => {
    element._projectLookup = {1: Promise.resolve('test')};
    const change_num = '1';
    const message = 'this is a commit message';
    sinon.stub(element._restApiHelper, 'send').returns(
        Promise.resolve([change_num, message]));
    sinon.stub(element, 'getResponseObject')
        .returns(Promise.resolve([change_num, message]));
    element._cache.set('/changes/' + change_num + '/message', {});
    return element.putChangeCommitMessage(change_num, message).then(() => {
      assert.isTrue(element._restApiHelper.send.calledOnce);
      assert.equal(element._restApiHelper.send.lastCall.args[0].method, 'PUT');
      assert.equal(element._restApiHelper.send.lastCall.args[0].url,
          '/changes/test~1/message');
      assert.deepEqual(element._restApiHelper.send.lastCall.args[0].body,
          {message});
    });
  });

  test('deleteChangeCommitMessage', () => {
    element._projectLookup = {1: Promise.resolve('test')};
    const change_num = '1';
    const messageId = 'abc';
    sinon.stub(element._restApiHelper, 'send').returns(
        Promise.resolve([change_num, messageId]));
    sinon.stub(element, 'getResponseObject')
        .returns(Promise.resolve([change_num, messageId]));
    return element.deleteChangeCommitMessage(change_num, messageId).then(() => {
      assert.isTrue(element._restApiHelper.send.calledOnce);
      assert.equal(
          element._restApiHelper.send.lastCall.args[0].method,
          'DELETE'
      );
      assert.equal(element._restApiHelper.send.lastCall.args[0].url,
          '/changes/test~1/messages/abc');
    });
  });

  test('startWorkInProgress', () => {
    const sendStub = sinon.stub(element, '_getChangeURLAndSend')
        .returns(Promise.resolve('ok'));
    element.startWorkInProgress('42');
    assert.isTrue(sendStub.calledOnce);
    assert.equal(sendStub.lastCall.args[0].changeNum, '42');
    assert.equal(sendStub.lastCall.args[0].method, 'POST');
    assert.isNotOk(sendStub.lastCall.args[0].patchNum);
    assert.equal(sendStub.lastCall.args[0].endpoint, '/wip');
    assert.deepEqual(sendStub.lastCall.args[0].body, {});

    element.startWorkInProgress('42', 'revising...');
    assert.isTrue(sendStub.calledTwice);
    assert.equal(sendStub.lastCall.args[0].changeNum, '42');
    assert.equal(sendStub.lastCall.args[0].method, 'POST');
    assert.isNotOk(sendStub.lastCall.args[0].patchNum);
    assert.equal(sendStub.lastCall.args[0].endpoint, '/wip');
    assert.deepEqual(sendStub.lastCall.args[0].body,
        {message: 'revising...'});
  });

  test('deleteComment', () => {
    const sendStub = sinon.stub(element, '_getChangeURLAndSend')
        .returns(Promise.resolve('some response'));
    return element.deleteComment('foo', 'bar', '01234', 'removal reason')
        .then(response => {
          assert.equal(response, 'some response');
          assert.isTrue(sendStub.calledOnce);
          assert.equal(sendStub.lastCall.args[0].changeNum, 'foo');
          assert.equal(sendStub.lastCall.args[0].method, 'POST');
          assert.equal(sendStub.lastCall.args[0].patchNum, 'bar');
          assert.equal(sendStub.lastCall.args[0].endpoint,
              '/comments/01234/delete');
          assert.deepEqual(sendStub.lastCall.args[0].body,
              {reason: 'removal reason'});
        });
  });

  test('createRepo encodes name', () => {
    const sendStub = sinon.stub(element._restApiHelper, 'send')
        .returns(Promise.resolve());
    return element.createRepo({name: 'x/y'}).then(() => {
      assert.isTrue(sendStub.calledOnce);
      assert.equal(sendStub.lastCall.args[0].url, '/projects/x%2Fy');
    });
  });

  test('queryChangeFiles', () => {
    const fetchStub = sinon.stub(element, '_getChangeURLAndFetch')
        .returns(Promise.resolve());
    return element.queryChangeFiles('42', 'edit', 'test/path.js').then(() => {
      assert.equal(fetchStub.lastCall.args[0].changeNum, '42');
      assert.equal(fetchStub.lastCall.args[0].endpoint,
          '/files?q=test%2Fpath.js');
      assert.equal(fetchStub.lastCall.args[0].revision, 'edit');
    });
  });

  test('normal use', () => {
    const defaultQuery = 'state%3Aactive%20OR%20state%3Aread-only';

    assert.equal(element._getReposUrl('test', 25),
        '/projects/?n=26&S=0&query=test');

    assert.equal(element._getReposUrl(null, 25),
        `/projects/?n=26&S=0&query=${defaultQuery}`);

    assert.equal(element._getReposUrl('test', 25, 25),
        '/projects/?n=26&S=25&query=test');
  });

  test('invalidateReposCache', () => {
    const url = '/projects/?n=26&S=0&query=test';

    element._cache.set(url, {});

    element.invalidateReposCache();

    assert.isUndefined(element._sharedFetchPromises[url]);

    assert.isFalse(element._cache.has(url));
  });

  test('invalidateAccountsCache', () => {
    const url = '/accounts/self/detail';

    element._cache.set(url, {});

    element.invalidateAccountsCache();

    assert.isUndefined(element._sharedFetchPromises[url]);

    assert.isFalse(element._cache.has(url));
  });

  suite('getRepos', () => {
    const defaultQuery = 'state%3Aactive%20OR%20state%3Aread-only';
    let fetchCacheURLStub;
    setup(() => {
      fetchCacheURLStub =
          sinon.stub(element._restApiHelper, 'fetchCacheURL');
    });

    test('normal use', () => {
      element.getRepos('test', 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/projects/?n=26&S=0&query=test');

      element.getRepos(null, 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          `/projects/?n=26&S=0&query=${defaultQuery}`);

      element.getRepos('test', 25, 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/projects/?n=26&S=25&query=test');
    });

    test('with blank', () => {
      element.getRepos('test/test', 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/projects/?n=26&S=0&query=inname%3Atest%20AND%20inname%3Atest');
    });

    test('with hyphen', () => {
      element.getRepos('foo-bar', 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/projects/?n=26&S=0&query=inname%3Afoo%20AND%20inname%3Abar');
    });

    test('with leading hyphen', () => {
      element.getRepos('-bar', 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/projects/?n=26&S=0&query=inname%3Abar');
    });

    test('with trailing hyphen', () => {
      element.getRepos('foo-bar-', 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/projects/?n=26&S=0&query=inname%3Afoo%20AND%20inname%3Abar');
    });

    test('with underscore', () => {
      element.getRepos('foo_bar', 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/projects/?n=26&S=0&query=inname%3Afoo%20AND%20inname%3Abar');
    });

    test('with underscore', () => {
      element.getRepos('foo_bar', 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/projects/?n=26&S=0&query=inname%3Afoo%20AND%20inname%3Abar');
    });

    test('hyphen only', () => {
      element.getRepos('-', 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          `/projects/?n=26&S=0&query=${defaultQuery}`);
    });
  });

  test('_getGroupsUrl normal use', () => {
    assert.equal(element._getGroupsUrl('test', 25),
        '/groups/?n=26&S=0&m=test');

    assert.equal(element._getGroupsUrl(null, 25),
        '/groups/?n=26&S=0');

    assert.equal(element._getGroupsUrl('test', 25, 25),
        '/groups/?n=26&S=25&m=test');
  });

  test('invalidateGroupsCache', () => {
    const url = '/groups/?n=26&S=0&m=test';

    element._cache.set(url, {});

    element.invalidateGroupsCache();

    assert.isUndefined(element._sharedFetchPromises[url]);

    assert.isFalse(element._cache.has(url));
  });

  suite('getGroups', () => {
    let fetchCacheURLStub;
    setup(() => {
      fetchCacheURLStub =
          sinon.stub(element._restApiHelper, 'fetchCacheURL');
    });

    test('normal use', () => {
      element.getGroups('test', 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/groups/?n=26&S=0&m=test');

      element.getGroups(null, 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/groups/?n=26&S=0');

      element.getGroups('test', 25, 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/groups/?n=26&S=25&m=test');
    });

    test('regex', () => {
      element.getGroups('^test.*', 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/groups/?n=26&S=0&r=%5Etest.*');

      element.getGroups('^test.*', 25, 25);
      assert.equal(fetchCacheURLStub.lastCall.args[0].url,
          '/groups/?n=26&S=25&r=%5Etest.*');
    });
  });

  test('gerrit auth is used', () => {
    stubAuth('fetch').returns(Promise.resolve());
    element._restApiHelper.fetchJSON({url: 'foo'});
    assert(getAppContext().authService.fetch.called);
  });

  test('getSuggestedAccounts does not return _fetchJSON', () => {
    const _fetchJSONSpy = sinon.spy(element._restApiHelper, 'fetchJSON');
    return element.getSuggestedAccounts().then(accts => {
      assert.isFalse(_fetchJSONSpy.called);
      assert.equal(accts.length, 0);
    });
  });

  test('_fetchJSON gets called by getSuggestedAccounts', () => {
    const _fetchJSONStub = sinon.stub(element._restApiHelper, 'fetchJSON')
        .callsFake(() => Promise.resolve());
    return element.getSuggestedAccounts('own').then(() => {
      assert.deepEqual(_fetchJSONStub.lastCall.args[0].params, {
        q: 'own',
        suggest: null,
      });
    });
  });

  suite('getChangeDetail', () => {
    suite('change detail options', () => {
      setup(() => {
        sinon.stub(element, '_getChangeDetail').callsFake(
            async (changeNum, options) => {
              return {changeNum, options};
            });
      });

      test('signed pushes disabled', async () => {
        sinon.stub(element, 'getConfig').callsFake(async () => {
          return {};
        });
        const {changeNum, options} = await element.getChangeDetail(123);
        assert.strictEqual(123, changeNum);
        assert.isNotOk(
            parseInt(options, 16) & (1 << ListChangesOption.PUSH_CERTIFICATES));
      });

      test('signed pushes enabled', async () => {
        sinon.stub(element, 'getConfig').callsFake(async () => {
          return {receive: {enable_signed_push: true}};
        });
        const {changeNum, options} = await element.getChangeDetail(123);
        assert.strictEqual(123, changeNum);
        assert.ok(
            parseInt(options, 16) & (1 << ListChangesOption.PUSH_CERTIFICATES));
      });
    });

    test('GrReviewerUpdatesParser.parse is used', () => {
      sinon.stub(GrReviewerUpdatesParser, 'parse').returns(
          Promise.resolve('foo'));
      return element.getChangeDetail(42).then(result => {
        assert.isTrue(GrReviewerUpdatesParser.parse.calledOnce);
        assert.equal(result, 'foo');
      });
    });

    test('_getChangeDetail passes params to ETags decorator', () => {
      const changeNum = 4321;
      element._projectLookup[changeNum] = Promise.resolve('test');
      const expectedUrl =
          window.CANONICAL_PATH + '/changes/test~4321/detail?O=516714';
      sinon.stub(element._etags, 'getOptions');
      sinon.stub(element._etags, 'collect');
      return element._getChangeDetail(changeNum, '516714').then(() => {
        assert.isTrue(element._etags.getOptions.calledWithExactly(
            expectedUrl));
        assert.equal(element._etags.collect.lastCall.args[0], expectedUrl);
      });
    });

    test('_getChangeDetail calls errFn on 500', () => {
      const errFn = sinon.stub();
      sinon.stub(element, 'getChangeActionURL')
          .returns(Promise.resolve(''));
      sinon.stub(element._restApiHelper, 'fetchRawJSON')
          .returns(Promise.resolve({ok: false, status: 500}));
      return element._getChangeDetail(123, '516714', errFn).then(() => {
        assert.isTrue(errFn.called);
      });
    });

    test('_getChangeDetail populates _projectLookup', async () => {
      sinon.stub(element, 'getChangeActionURL')
          .returns(Promise.resolve(''));
      sinon.stub(element._restApiHelper, 'fetchRawJSON')
          .returns(Promise.resolve({
            ok: true,
            status: 200,
            text: () => Promise.resolve(`)]}'{"_number":1,"project":"test"}`),
          }));
      await element._getChangeDetail(1, '516714');
      assert.equal(Object.keys(element._projectLookup).length, 1);
      const project = await element._projectLookup[1];
      assert.equal(project, 'test');
    });

    suite('_getChangeDetail ETag cache', () => {
      let requestUrl;
      let mockResponseSerial;
      let collectSpy;

      setup(() => {
        requestUrl = '/foo/bar';
        const mockResponse = {foo: 'bar', baz: 42};
        mockResponseSerial = JSON_PREFIX + JSON.stringify(mockResponse);
        sinon.stub(element._restApiHelper, 'urlWithParams')
            .returns(requestUrl);
        sinon.stub(element, 'getChangeActionURL')
            .returns(Promise.resolve(requestUrl));
        collectSpy = sinon.spy(element._etags, 'collect');
      });

      test('contributes to cache', () => {
        const getPayloadSpy = sinon.spy(element._etags, 'getCachedPayload');
        sinon.stub(element._restApiHelper, 'fetchRawJSON')
            .returns(Promise.resolve({
              text: () => Promise.resolve(mockResponseSerial),
              status: 200,
              ok: true,
            }));

        return element._getChangeDetail(123, '516714').then(detail => {
          assert.isFalse(getPayloadSpy.called);
          assert.isTrue(collectSpy.calledOnce);
          const cachedResponse = element._etags.getCachedPayload(requestUrl);
          assert.equal(cachedResponse, mockResponseSerial);
        });
      });

      test('uses cache on HTTP 304', () => {
        const getPayloadStub = sinon.stub(element._etags, 'getCachedPayload');
        getPayloadStub.returns(mockResponseSerial);
        sinon.stub(element._restApiHelper, 'fetchRawJSON')
            .returns(Promise.resolve({
              text: () => Promise.resolve(''),
              status: 304,
              ok: true,
            }));

        return element._getChangeDetail(123, '').then(detail => {
          assert.isFalse(collectSpy.called);
          assert.isTrue(getPayloadStub.calledOnce);
        });
      });
    });
  });

  test('setInProjectLookup', async () => {
    await element.setInProjectLookup('test', 'project');
    const project = await element.getFromProjectLookup('test');
    assert.deepEqual(project, 'project');
  });

  suite('getFromProjectLookup', () => {
    test('getChange succeeds, no project', async () => {
      sinon.stub(element, 'getChange').returns(Promise.resolve(null));
      const val = await element.getFromProjectLookup();
      assert.strictEqual(val, undefined);
    });

    test('getChange succeeds with project', () => {
      sinon.stub(element, 'getChange')
          .returns(Promise.resolve({project: 'project'}));
      const projectLookup = element.getFromProjectLookup('test');
      return projectLookup.then(val => {
        assert.equal(val, 'project');
        assert.deepEqual(element._projectLookup, {test: projectLookup});
      });
    });
  });

  suite('getChanges populates _projectLookup', () => {
    test('multiple queries', async () => {
      sinon.stub(element._restApiHelper, 'fetchJSON')
          .returns(Promise.resolve([
            [
              {_number: 1, project: 'test'},
              {_number: 2, project: 'test'},
            ], [
              {_number: 3, project: 'test/test'},
            ],
          ]));
      // When opt_query instanceof Array, _fetchJSON returns
      // Array<Array<Object>>.
      await element.getChangesForMultipleQueries(null, []);
      assert.equal(Object.keys(element._projectLookup).length, 3);
      const project1 = await element.getFromProjectLookup(1);
      assert.equal(project1, 'test');
      const project2 = await element.getFromProjectLookup(2);
      assert.equal(project2, 'test');
      const project3 = await element.getFromProjectLookup(3);
      assert.equal(project3, 'test/test');
    });

    test('no query', async () => {
      sinon.stub(element._restApiHelper, 'fetchJSON')
          .returns(Promise.resolve([
            {_number: 1, project: 'test'},
            {_number: 2, project: 'test'},
            {_number: 3, project: 'test/test'},
          ]));

      // When opt_query !instanceof Array, _fetchJSON returns
      // Array<Object>.
      await element.getChanges();
      assert.equal(Object.keys(element._projectLookup).length, 3);
      const project1 = await element.getFromProjectLookup(1);
      assert.equal(project1, 'test');
      const project2 = await element.getFromProjectLookup(2);
      assert.equal(project2, 'test');
      const project3 = await element.getFromProjectLookup(3);
      assert.equal(project3, 'test/test');
    });
  });

  test('getDetailedChangesWithActions', async () => {
    const c1 = createChange();
    c1._number = 1;
    const c2 = createChange();
    c2._number = 2;
    const getChangesStub = sinon.stub(element, 'getChanges').callsFake(
        (changesPerPage, query, offset, options) => {
          assert.isUndefined(changesPerPage);
          assert.strictEqual(query, 'change:1 OR change:2');
          assert.isUndefined(offset);
          assert.strictEqual(options, EXPECTED_QUERY_OPTIONS);
          return Promise.resolve([]);
        }
    );
    await element.getDetailedChangesWithActions([c1._number, c2._number]);
    assert.isTrue(getChangesStub.calledOnce);
  });

  test('_getChangeURLAndFetch', () => {
    element._projectLookup = {1: Promise.resolve('test')};
    const fetchStub = sinon.stub(element._restApiHelper, 'fetchJSON')
        .returns(Promise.resolve());
    const req = {changeNum: 1, endpoint: '/test', revision: 1};
    return element._getChangeURLAndFetch(req).then(() => {
      assert.equal(fetchStub.lastCall.args[0].url,
          '/changes/test~1/revisions/1/test');
    });
  });

  test('_getChangeURLAndSend', () => {
    element._projectLookup = {1: Promise.resolve('test')};
    const sendStub = sinon.stub(element._restApiHelper, 'send')
        .returns(Promise.resolve());

    const req = {
      changeNum: 1,
      method: 'POST',
      patchNum: 1,
      endpoint: '/test',
    };
    return element._getChangeURLAndSend(req).then(() => {
      assert.isTrue(sendStub.calledOnce);
      assert.equal(sendStub.lastCall.args[0].method, 'POST');
      assert.equal(sendStub.lastCall.args[0].url,
          '/changes/test~1/revisions/1/test');
    });
  });

  suite('reading responses', () => {
    test('_readResponsePayload', async () => {
      const mockObject = {foo: 'bar', baz: 'foo'};
      const serial = JSON_PREFIX + JSON.stringify(mockObject);
      const mockResponse = {text: () => Promise.resolve(serial)};
      const payload = await readResponsePayload(mockResponse);
      assert.deepEqual(payload.parsed, mockObject);
      assert.equal(payload.raw, serial);
    });

    test('_parsePrefixedJSON', () => {
      const obj = {x: 3, y: {z: 4}, w: 23};
      const serial = JSON_PREFIX + JSON.stringify(obj);
      const result = parsePrefixedJSON(serial);
      assert.deepEqual(result, obj);
    });
  });

  test('setChangeTopic', () => {
    const sendSpy = sinon.spy(element, '_getChangeURLAndSend');
    return element.setChangeTopic(123, 'foo-bar').then(() => {
      assert.isTrue(sendSpy.calledOnce);
      assert.deepEqual(sendSpy.lastCall.args[0].body, {topic: 'foo-bar'});
    });
  });

  test('setChangeHashtag', () => {
    const sendSpy = sinon.spy(element, '_getChangeURLAndSend');
    return element.setChangeHashtag(123, 'foo-bar').then(() => {
      assert.isTrue(sendSpy.calledOnce);
      assert.equal(sendSpy.lastCall.args[0].body, 'foo-bar');
    });
  });

  test('generateAccountHttpPassword', () => {
    const sendSpy = sinon.spy(element._restApiHelper, 'send');
    return element.generateAccountHttpPassword().then(() => {
      assert.isTrue(sendSpy.calledOnce);
      assert.deepEqual(sendSpy.lastCall.args[0].body, {generate: true});
    });
  });

  suite('getChangeFiles', () => {
    test('patch only', () => {
      const fetchStub = sinon.stub(element, '_getChangeURLAndFetch')
          .returns(Promise.resolve());
      const range = {basePatchNum: 'PARENT', patchNum: 2};
      return element.getChangeFiles(123, range).then(() => {
        assert.isTrue(fetchStub.calledOnce);
        assert.equal(fetchStub.lastCall.args[0].revision, 2);
        assert.isNotOk(fetchStub.lastCall.args[0].params);
      });
    });

    test('simple range', () => {
      const fetchStub = sinon.stub(element, '_getChangeURLAndFetch')
          .returns(Promise.resolve());
      const range = {basePatchNum: 4, patchNum: 5};
      return element.getChangeFiles(123, range).then(() => {
        assert.isTrue(fetchStub.calledOnce);
        assert.equal(fetchStub.lastCall.args[0].revision, 5);
        assert.isOk(fetchStub.lastCall.args[0].params);
        assert.equal(fetchStub.lastCall.args[0].params.base, 4);
        assert.isNotOk(fetchStub.lastCall.args[0].params.parent);
      });
    });

    test('parent index', () => {
      const fetchStub = sinon.stub(element, '_getChangeURLAndFetch')
          .returns(Promise.resolve());
      const range = {basePatchNum: -3, patchNum: 5};
      return element.getChangeFiles(123, range).then(() => {
        assert.isTrue(fetchStub.calledOnce);
        assert.equal(fetchStub.lastCall.args[0].revision, 5);
        assert.isOk(fetchStub.lastCall.args[0].params);
        assert.isNotOk(fetchStub.lastCall.args[0].params.base);
        assert.equal(fetchStub.lastCall.args[0].params.parent, 3);
      });
    });
  });

  suite('getDiff', () => {
    test('patchOnly', () => {
      const fetchStub = sinon.stub(element, '_getChangeURLAndFetch')
          .returns(Promise.resolve());
      return element.getDiff(123, 'PARENT', 2, 'foo/bar.baz').then(() => {
        assert.isTrue(fetchStub.calledOnce);
        assert.equal(fetchStub.lastCall.args[0].revision, 2);
        assert.isOk(fetchStub.lastCall.args[0].params);
        assert.isNotOk(fetchStub.lastCall.args[0].params.parent);
        assert.isNotOk(fetchStub.lastCall.args[0].params.base);
      });
    });

    test('simple range', () => {
      const fetchStub = sinon.stub(element, '_getChangeURLAndFetch')
          .returns(Promise.resolve());
      return element.getDiff(123, 4, 5, 'foo/bar.baz').then(() => {
        assert.isTrue(fetchStub.calledOnce);
        assert.equal(fetchStub.lastCall.args[0].revision, 5);
        assert.isOk(fetchStub.lastCall.args[0].params);
        assert.isNotOk(fetchStub.lastCall.args[0].params.parent);
        assert.equal(fetchStub.lastCall.args[0].params.base, 4);
      });
    });

    test('parent index', () => {
      const fetchStub = sinon.stub(element, '_getChangeURLAndFetch')
          .returns(Promise.resolve());
      return element.getDiff(123, -3, 5, 'foo/bar.baz').then(() => {
        assert.isTrue(fetchStub.calledOnce);
        assert.equal(fetchStub.lastCall.args[0].revision, 5);
        assert.isOk(fetchStub.lastCall.args[0].params);
        assert.isNotOk(fetchStub.lastCall.args[0].params.base);
        assert.equal(fetchStub.lastCall.args[0].params.parent, 3);
      });
    });
  });

  test('getDashboard', () => {
    const fetchCacheURLStub = sinon.stub(element._restApiHelper,
        'fetchCacheURL');
    element.getDashboard('gerrit/project', 'default:main');
    assert.isTrue(fetchCacheURLStub.calledOnce);
    assert.equal(
        fetchCacheURLStub.lastCall.args[0].url,
        '/projects/gerrit%2Fproject/dashboards/default%3Amain');
  });

  test('getFileContent', () => {
    sinon.stub(element, '_getChangeURLAndSend')
        .returns(Promise.resolve({
          ok: 'true',
          headers: {
            get(header) {
              if (header === 'X-FYI-Content-Type') {
                return 'text/java';
              }
            },
          },
        }));

    sinon.stub(element, 'getResponseObject')
        .returns(Promise.resolve('new content'));

    const edit = element.getFileContent('1', 'tst/path', 'EDIT').then(res => {
      assert.deepEqual(res,
          {content: 'new content', type: 'text/java', ok: true});
    });

    const normal = element.getFileContent('1', 'tst/path', '3').then(res => {
      assert.deepEqual(res,
          {content: 'new content', type: 'text/java', ok: true});
    });

    return Promise.all([edit, normal]);
  });

  test('getFileContent suppresses 404s', () => {
    const res = {status: 404};
    const spy = sinon.spy();
    addListenerForTest(document, 'server-error', spy);
    sinon.stub(getAppContext().authService, 'fetch')
        .returns(Promise.resolve(res));
    sinon.stub(element, '_changeBaseURL').returns(Promise.resolve(''));
    return element.getFileContent('1', 'tst/path', '1')
        .then(() => {
          flush();
          assert.isFalse(spy.called);

          res.status = 500;
          return element.getFileContent('1', 'tst/path', '1');
        })
        .then(() => {
          assert.isTrue(spy.called);
          assert.notEqual(spy.lastCall.args[0].detail.response.status, 404);
        });
  });

  test('getChangeFilesOrEditFiles is edit-sensitive', () => {
    const fn = element.getChangeOrEditFiles.bind(element);
    const getChangeFilesStub = sinon.stub(element, 'getChangeFiles')
        .returns(Promise.resolve({}));
    const getChangeEditFilesStub = sinon.stub(element, 'getChangeEditFiles')
        .returns(Promise.resolve({}));

    return fn('1', {patchNum: 'edit'}).then(() => {
      assert.isTrue(getChangeEditFilesStub.calledOnce);
      assert.isFalse(getChangeFilesStub.called);
      return fn('1', {patchNum: '1'}).then(() => {
        assert.isTrue(getChangeEditFilesStub.calledOnce);
        assert.isTrue(getChangeFilesStub.calledOnce);
      });
    });
  });

  test('_fetch forwards request and logs', () => {
    const logStub = sinon.stub(element._restApiHelper, '_logCall');
    const response = {status: 404, text: sinon.stub()};
    const url = 'my url';
    const fetchOptions = {method: 'DELETE'};
    sinon.stub(element.authService, 'fetch').returns(Promise.resolve(response));
    const startTime = 123;
    sinon.stub(Date, 'now').returns(startTime);
    const req = {url, fetchOptions};
    return element._restApiHelper.fetch(req).then(() => {
      assert.isTrue(logStub.calledOnce);
      assert.isTrue(logStub.calledWith(req, startTime, response.status));
      assert.isFalse(response.text.called);
    });
  });

  test('_logCall only reports requests with anonymized URLss', () => {
    sinon.stub(Date, 'now').returns(200);
    const handler = sinon.stub();
    addListenerForTest(document, 'gr-rpc-log', handler);

    element._restApiHelper._logCall({url: 'url'}, 100, 200);
    assert.isFalse(handler.called);

    element._restApiHelper
        ._logCall({url: 'url', anonymizedUrl: 'not url'}, 100, 200);
    flush();
    assert.isTrue(handler.calledOnce);
  });

  test('ported comment errors do not trigger error dialog', () => {
    const change = createChange();
    const handler = sinon.stub();
    addListenerForTest(document, 'server-error', handler);
    sinon.stub(element._restApiHelper, 'fetchJSON').returns(Promise.resolve({
      ok: false}));

    element.getPortedComments(change._number, CURRENT);

    assert.isFalse(handler.called);
  });

  test('ported drafts are not requested user is not logged in', () => {
    const change = createChange();
    sinon.stub(element, 'getLoggedIn').returns(Promise.resolve(false));
    const getChangeURLAndFetchStub = sinon.stub(element,
        '_getChangeURLAndFetch');

    element.getPortedDrafts(change._number, CURRENT);

    assert.isFalse(getChangeURLAndFetchStub.called);
  });

  test('saveChangeStarred', async () => {
    sinon.stub(element, 'getFromProjectLookup')
        .returns(Promise.resolve('test'));
    const sendStub =
        sinon.stub(element._restApiHelper, 'send').returns(Promise.resolve());

    await element.saveChangeStarred(123, true);
    assert.isTrue(sendStub.calledOnce);
    assert.deepEqual(sendStub.lastCall.args[0], {
      method: 'PUT',
      url: '/accounts/self/starred.changes/test~123',
      anonymizedUrl: '/accounts/self/starred.changes/*',
    });

    await element.saveChangeStarred(456, false);
    assert.isTrue(sendStub.calledTwice);
    assert.deepEqual(sendStub.lastCall.args[0], {
      method: 'DELETE',
      url: '/accounts/self/starred.changes/test~456',
      anonymizedUrl: '/accounts/self/starred.changes/*',
    });
  });
});

