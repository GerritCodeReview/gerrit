/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Subject} from 'rxjs';
import {ChangeStatus} from '../../constants/constants';
import '../../test/common-test-setup';
import {
  createChange,
  createChangeMessageInfo,
  createChangeViewState,
  createEditInfo,
  createParsedChange,
  createRevision,
} from '../../test/test-data-generators';
import {
  mockPromise,
  stubRestApi,
  waitUntilObserved,
} from '../../test/test-utils';
import {
  CommitId,
  EDIT,
  NumericChangeId,
  PARENT,
  PatchSetNum,
  PatchSetNumber,
} from '../../types/common';
import {ParsedChangeInfo} from '../../types/types';
import {getAppContext} from '../../services/app-context';
import {ChangeState, LoadingStatus, updateChangeWithEdit} from './change-model';
import {ChangeModel} from './change-model';
import {assert} from '@open-wc/testing';
import {testResolver} from '../../test/common-test-setup';
import {userModelToken} from '../user/user-model';
import {changeViewModelToken} from '../views/change';
import {navigationToken} from '../../elements/core/gr-navigation/gr-navigation';

suite('updateChangeWithEdit() tests', () => {
  test('undefined change', async () => {
    assert.isUndefined(updateChangeWithEdit());
  });

  test('undefined edit', async () => {
    const change = createParsedChange();
    assert.equal(updateChangeWithEdit(change), change);
  });

  test('set edit rev and current rev', async () => {
    let change: ParsedChangeInfo | undefined = createParsedChange();
    const edit = createEditInfo();
    change = updateChangeWithEdit(change, edit);
    const editRev = change?.revisions[`${edit.commit.commit}`];
    assert.isDefined(editRev);
    assert.equal(editRev?._number, EDIT);
    assert.equal(editRev?.basePatchNum, edit.base_patch_set_number);
    assert.equal(change?.current_revision, edit.commit.commit);
  });

  test('do not set current rev when patchNum already set', async () => {
    let change: ParsedChangeInfo | undefined = createParsedChange();
    const edit = createEditInfo();
    change = updateChangeWithEdit(change, edit, 1 as PatchSetNum);
    const editRev = change?.revisions[`${edit.commit.commit}`];
    assert.isDefined(editRev);
    assert.equal(change?.current_revision, 'abc' as CommitId);
  });
});

suite('change model tests', () => {
  let changeModel: ChangeModel;
  let knownChange: ParsedChangeInfo;
  const testCompleted = new Subject<void>();

  async function waitForLoadingStatus(
    loadingStatus: LoadingStatus
  ): Promise<ChangeState> {
    return await waitUntilObserved(
      changeModel.state$,
      state => state.loadingStatus === loadingStatus,
      `LoadingStatus was never ${loadingStatus}`
    );
  }

  setup(() => {
    changeModel = new ChangeModel(
      testResolver(navigationToken),
      testResolver(changeViewModelToken),
      getAppContext().restApiService,
      testResolver(userModelToken)
    );
    knownChange = {
      ...createChange(),
      revisions: {
        sha1: {
          ...createRevision(1),
          description: 'patch 1',
          _number: 1 as PatchSetNumber,
        },
        sha2: {
          ...createRevision(2),
          description: 'patch 2',
          _number: 2 as PatchSetNumber,
        },
      },
      status: ChangeStatus.NEW,
      current_revision: 'abc' as CommitId,
      messages: [],
    };
  });

  teardown(() => {
    testCompleted.next();
    changeModel.finalize();
  });

  test('load a change', async () => {
    const promise = mockPromise<ParsedChangeInfo | undefined>();
    const stub = stubRestApi('getChangeDetail').callsFake(() => promise);
    let state: ChangeState;

    state = await waitForLoadingStatus(LoadingStatus.NOT_LOADED);
    assert.equal(stub.callCount, 0);
    assert.isUndefined(state?.change);

    testResolver(changeViewModelToken).setState(createChangeViewState());
    state = await waitForLoadingStatus(LoadingStatus.LOADING);
    assert.equal(stub.callCount, 1);
    assert.isUndefined(state?.change);

    promise.resolve(knownChange);
    state = await waitForLoadingStatus(LoadingStatus.LOADED);
    assert.equal(stub.callCount, 1);
    assert.equal(state?.change, knownChange);
  });

  test('reload a change', async () => {
    // setting up a loaded change
    const promise = mockPromise<ParsedChangeInfo | undefined>();
    const stub = stubRestApi('getChangeDetail').callsFake(() => promise);
    let state: ChangeState;
    testResolver(changeViewModelToken).setState(createChangeViewState());
    promise.resolve(knownChange);
    state = await waitForLoadingStatus(LoadingStatus.LOADED);

    // Reloading same change
    document.dispatchEvent(new CustomEvent('reload'));
    state = await waitForLoadingStatus(LoadingStatus.RELOADING);
    assert.equal(stub.callCount, 2);
    assert.equal(state?.change, knownChange);

    promise.resolve(knownChange);
    state = await waitForLoadingStatus(LoadingStatus.LOADED);
    assert.equal(stub.callCount, 2);
    assert.equal(state?.change, knownChange);
  });

  test('navigating to another change', async () => {
    // setting up a loaded change
    let promise = mockPromise<ParsedChangeInfo | undefined>();
    const stub = stubRestApi('getChangeDetail').callsFake(() => promise);
    let state: ChangeState;
    testResolver(changeViewModelToken).setState(createChangeViewState());
    promise.resolve(knownChange);
    state = await waitForLoadingStatus(LoadingStatus.LOADED);

    // Navigating to other change

    const otherChange: ParsedChangeInfo = {
      ...knownChange,
      _number: 123 as NumericChangeId,
    };
    promise = mockPromise<ParsedChangeInfo | undefined>();
    testResolver(changeViewModelToken).setState({
      ...createChangeViewState(),
      changeNum: otherChange._number,
    });
    state = await waitForLoadingStatus(LoadingStatus.LOADING);
    assert.equal(stub.callCount, 2);
    assert.isUndefined(state?.change);

    promise.resolve(otherChange);
    state = await waitForLoadingStatus(LoadingStatus.LOADED);
    assert.equal(stub.callCount, 2);
    assert.equal(state?.change, otherChange);
  });

  test('navigating to dashboard', async () => {
    // setting up a loaded change
    let promise = mockPromise<ParsedChangeInfo | undefined>();
    const stub = stubRestApi('getChangeDetail').callsFake(() => promise);
    let state: ChangeState;
    testResolver(changeViewModelToken).setState(createChangeViewState());
    promise.resolve(knownChange);
    state = await waitForLoadingStatus(LoadingStatus.LOADED);

    // Navigating to dashboard

    promise = mockPromise<ParsedChangeInfo | undefined>();
    promise.resolve(undefined);
    testResolver(changeViewModelToken).setState(undefined);
    state = await waitForLoadingStatus(LoadingStatus.NOT_LOADED);
    assert.equal(stub.callCount, 2);
    assert.isUndefined(state?.change);

    // Navigating back from dashboard to change page

    promise = mockPromise<ParsedChangeInfo | undefined>();
    promise.resolve(knownChange);
    testResolver(changeViewModelToken).setState(createChangeViewState());
    state = await waitForLoadingStatus(LoadingStatus.LOADED);
    assert.equal(stub.callCount, 3);
    assert.equal(state?.change, knownChange);
  });

  test('changeModel.fetchChangeUpdates on latest', async () => {
    stubRestApi('getChangeDetail').returns(Promise.resolve(knownChange));
    const result = await changeModel.fetchChangeUpdates(knownChange);
    assert.isTrue(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.isNotOk(result.newMessages);
  });

  test('changeModel.fetchChangeUpdates not on latest', async () => {
    const actualChange = {
      ...knownChange,
      revisions: {
        ...knownChange.revisions,
        sha3: {
          ...createRevision(3),
          description: 'patch 3',
          _number: 3 as PatchSetNumber,
        },
      },
    };
    stubRestApi('getChangeDetail').returns(Promise.resolve(actualChange));
    const result = await changeModel.fetchChangeUpdates(knownChange);
    assert.isFalse(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.isNotOk(result.newMessages);
  });

  test('changeModel.fetchChangeUpdates new status', async () => {
    const actualChange = {
      ...knownChange,
      status: ChangeStatus.MERGED,
    };
    stubRestApi('getChangeDetail').returns(Promise.resolve(actualChange));
    const result = await changeModel.fetchChangeUpdates(knownChange);
    assert.isTrue(result.isLatest);
    assert.equal(result.newStatus, ChangeStatus.MERGED);
    assert.isNotOk(result.newMessages);
  });

  test('changeModel.fetchChangeUpdates new messages', async () => {
    const actualChange = {
      ...knownChange,
      messages: [{...createChangeMessageInfo(), message: 'blah blah'}],
    };
    stubRestApi('getChangeDetail').returns(Promise.resolve(actualChange));
    const result = await changeModel.fetchChangeUpdates(knownChange);
    assert.isTrue(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.deepEqual(result.newMessages, {
      ...createChangeMessageInfo(),
      message: 'blah blah',
    });
  });

  // At some point we had forgotten the `select()` wrapper for this selector.
  // And the missing `replay` led to a bug that was hard to find. That is why
  // we are testing this explicitly here.
  test('basePatchNum$ selector', async () => {
    // Let's first wait for the selector to emit. Then we can test the replay
    // below.
    await waitUntilObserved(changeModel.basePatchNum$, x => x === PARENT);

    const spy = sinon.spy();
    changeModel.basePatchNum$.subscribe(spy);

    // test replay
    assert.equal(spy.callCount, 1);
    assert.equal(spy.lastCall.firstArg, PARENT);

    // test update
    testResolver(changeViewModelToken).updateState({
      basePatchNum: 1 as PatchSetNumber,
    });
    assert.equal(spy.callCount, 2);
    assert.equal(spy.lastCall.firstArg, 1 as PatchSetNumber);

    // test distinctUntilChanged
    changeModel.updateStateChange(createParsedChange());
    assert.equal(spy.callCount, 2);
  });
});
