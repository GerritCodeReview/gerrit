/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';
import {ChangeStatus} from '../../constants/constants';
import '../../test/common-test-setup-karma';
import {
  createChange,
  createChangeMessageInfo,
  createRevision,
} from '../../test/test-data-generators';
import {mockPromise, stubRestApi, waitUntil} from '../../test/test-utils';
import {CommitId, NumericChangeId, PatchSetNum} from '../../types/common';
import {ParsedChangeInfo} from '../../types/types';
import {getAppContext} from '../app-context';
import {
  GerritView,
  _testOnly_setState as setRouterState,
} from '../router/router-model';
import {ChangeState, changeState$, LoadingStatus} from './change-model';
import {ChangeService} from './change-service';

suite('change service tests', () => {
  let changeService: ChangeService;
  let knownChange: ParsedChangeInfo;
  const testCompleted = new Subject<void>();
  setup(() => {
    changeService = new ChangeService(getAppContext().restApiService);
    knownChange = {
      ...createChange(),
      revisions: {
        sha1: {
          ...createRevision(1),
          description: 'patch 1',
          _number: 1 as PatchSetNum,
        },
        sha2: {
          ...createRevision(2),
          description: 'patch 2',
          _number: 2 as PatchSetNum,
        },
      },
      status: ChangeStatus.NEW,
      current_revision: 'abc' as CommitId,
      messages: [],
    };
  });

  teardown(() => {
    changeService.finalize();
    testCompleted.next();
  });

  test('load a change', async () => {
    const promise = mockPromise<ParsedChangeInfo | undefined>();
    const stub = stubRestApi('getChangeDetail').callsFake(() => promise);
    let state: ChangeState | undefined = {
      loadingStatus: LoadingStatus.NOT_LOADED,
    };
    changeState$.pipe(takeUntil(testCompleted)).subscribe(s => (state = s));

    await waitUntil(() => state?.loadingStatus === LoadingStatus.NOT_LOADED);
    assert.equal(stub.callCount, 0);
    assert.isUndefined(state?.change);

    setRouterState({view: GerritView.CHANGE, changeNum: knownChange._number});
    await waitUntil(() => state?.loadingStatus === LoadingStatus.LOADING);
    assert.equal(stub.callCount, 1);
    assert.isUndefined(state?.change);

    promise.resolve(knownChange);
    await waitUntil(() => state?.loadingStatus === LoadingStatus.LOADED);
    assert.equal(stub.callCount, 1);
    assert.equal(state?.change, knownChange);
  });

  test('reload a change', async () => {
    // setting up a loaded change
    const promise = mockPromise<ParsedChangeInfo | undefined>();
    const stub = stubRestApi('getChangeDetail').callsFake(() => promise);
    let state: ChangeState | undefined = {
      loadingStatus: LoadingStatus.NOT_LOADED,
    };
    changeState$.pipe(takeUntil(testCompleted)).subscribe(s => (state = s));
    setRouterState({view: GerritView.CHANGE, changeNum: knownChange._number});
    promise.resolve(knownChange);
    await waitUntil(() => state?.loadingStatus === LoadingStatus.LOADED);

    // Reloading same change
    document.dispatchEvent(new CustomEvent('reload'));
    await waitUntil(() => state?.loadingStatus === LoadingStatus.RELOADING);
    assert.equal(stub.callCount, 2);
    assert.equal(state?.change, knownChange);

    promise.resolve(knownChange);
    await waitUntil(() => state?.loadingStatus === LoadingStatus.LOADED);
    assert.equal(stub.callCount, 2);
    assert.equal(state?.change, knownChange);
  });

  test('navigating to another change', async () => {
    // setting up a loaded change
    let promise = mockPromise<ParsedChangeInfo | undefined>();
    const stub = stubRestApi('getChangeDetail').callsFake(() => promise);
    let state: ChangeState | undefined = {
      loadingStatus: LoadingStatus.NOT_LOADED,
    };
    changeState$.pipe(takeUntil(testCompleted)).subscribe(s => (state = s));
    setRouterState({view: GerritView.CHANGE, changeNum: knownChange._number});
    promise.resolve(knownChange);
    await waitUntil(() => state?.loadingStatus === LoadingStatus.LOADED);

    // Navigating to other change

    const otherChange: ParsedChangeInfo = {
      ...knownChange,
      _number: 123 as NumericChangeId,
    };
    promise = mockPromise<ParsedChangeInfo | undefined>();
    setRouterState({view: GerritView.CHANGE, changeNum: otherChange._number});
    await waitUntil(() => state?.loadingStatus === LoadingStatus.LOADING);
    assert.equal(stub.callCount, 2);
    assert.isUndefined(state?.change);

    promise.resolve(otherChange);
    await waitUntil(() => state?.loadingStatus === LoadingStatus.LOADED);
    assert.equal(stub.callCount, 2);
    assert.equal(state?.change, otherChange);
  });

  test('navigating to dashboard', async () => {
    // setting up a loaded change
    let promise = mockPromise<ParsedChangeInfo | undefined>();
    const stub = stubRestApi('getChangeDetail').callsFake(() => promise);
    let state: ChangeState | undefined = {
      loadingStatus: LoadingStatus.NOT_LOADED,
    };
    changeState$.pipe(takeUntil(testCompleted)).subscribe(s => (state = s));
    setRouterState({view: GerritView.CHANGE, changeNum: knownChange._number});
    promise.resolve(knownChange);
    await waitUntil(() => state?.loadingStatus === LoadingStatus.LOADED);

    // Navigating to dashboard

    promise = mockPromise<ParsedChangeInfo | undefined>();
    promise.resolve(undefined);
    setRouterState({view: GerritView.DASHBOARD, changeNum: undefined});
    await waitUntil(() => state?.loadingStatus === LoadingStatus.NOT_LOADED);
    assert.equal(stub.callCount, 2);
    assert.isUndefined(state?.change);

    // Navigating back from dashboard to change page

    promise = mockPromise<ParsedChangeInfo | undefined>();
    promise.resolve(knownChange);
    setRouterState({view: GerritView.CHANGE, changeNum: knownChange._number});
    await waitUntil(() => state?.loadingStatus === LoadingStatus.LOADED);
    assert.equal(stub.callCount, 3);
    assert.equal(state?.change, knownChange);
  });

  test('changeService.fetchChangeUpdates on latest', async () => {
    stubRestApi('getChangeDetail').returns(Promise.resolve(knownChange));
    const result = await changeService.fetchChangeUpdates(knownChange);
    assert.isTrue(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.isNotOk(result.newMessages);
  });

  test('changeService.fetchChangeUpdates not on latest', async () => {
    const actualChange = {
      ...knownChange,
      revisions: {
        ...knownChange.revisions,
        sha3: {
          ...createRevision(3),
          description: 'patch 3',
          _number: 3 as PatchSetNum,
        },
      },
    };
    stubRestApi('getChangeDetail').returns(Promise.resolve(actualChange));
    const result = await changeService.fetchChangeUpdates(knownChange);
    assert.isFalse(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.isNotOk(result.newMessages);
  });

  test('changeService.fetchChangeUpdates new status', async () => {
    const actualChange = {
      ...knownChange,
      status: ChangeStatus.MERGED,
    };
    stubRestApi('getChangeDetail').returns(Promise.resolve(actualChange));
    const result = await changeService.fetchChangeUpdates(knownChange);
    assert.isTrue(result.isLatest);
    assert.equal(result.newStatus, ChangeStatus.MERGED);
    assert.isNotOk(result.newMessages);
  });

  test('changeService.fetchChangeUpdates new messages', async () => {
    const actualChange = {
      ...knownChange,
      messages: [{...createChangeMessageInfo(), message: 'blah blah'}],
    };
    stubRestApi('getChangeDetail').returns(Promise.resolve(actualChange));
    const result = await changeService.fetchChangeUpdates(knownChange);
    assert.isTrue(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.deepEqual(result.newMessages, {
      ...createChangeMessageInfo(),
      message: 'blah blah',
    });
  });
});
