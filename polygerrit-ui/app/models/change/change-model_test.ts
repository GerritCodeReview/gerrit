/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import {Subject} from 'rxjs';
import {ChangeStatus} from '../../constants/constants';
import '../../test/common-test-setup';
import {
  TEST_NUMERIC_CHANGE_ID,
  createChange,
  createChangeMessageInfo,
  createChangeViewState,
  createEditInfo,
  createMergeable,
  createParsedChange,
  createRevision,
} from '../../test/test-data-generators';
import {
  mockPromise,
  stubRestApi,
  waitUntilObserved,
} from '../../test/test-utils';
import {
  BasePatchSetNum,
  ChangeInfo,
  CommitId,
  EDIT,
  NumericChangeId,
  PARENT,
  PatchSetNum,
  PatchSetNumber,
} from '../../types/common';
import {
  EditRevisionInfo,
  LoadingStatus,
  ParsedChangeInfo,
} from '../../types/types';
import {getAppContext} from '../../services/app-context';
import {
  ChangeState,
  updateChangeWithEdit,
  updateRevisionsWithCommitShas,
} from './change-model';
import {ChangeModel} from './change-model';
import {assert} from '@open-wc/testing';
import {testResolver} from '../../test/common-test-setup';
import {userModelToken} from '../user/user-model';
import {
  ChangeChildView,
  ChangeViewModel,
  changeViewModelToken,
} from '../views/change';
import {navigationToken} from '../../elements/core/gr-navigation/gr-navigation';
import {SinonStub} from 'sinon';
import {pluginLoaderToken} from '../../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {ShowChangeDetail} from '../../elements/shared/gr-js-api-interface/gr-js-api-types';

suite('updateRevisionsWithCommitShas() tests', () => {
  test('undefined edit', async () => {
    const change = createParsedChange();
    const updated = updateRevisionsWithCommitShas(change);
    assert.equal(change?.revisions?.['abc'].commit?.commit, undefined);
    assert.equal(updated?.revisions?.['abc'].commit?.commit, 'abc' as CommitId);
  });
});

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
    const editRev = change?.revisions[
      `${edit.commit.commit}`
    ] as EditRevisionInfo;
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
  let changeViewModel: ChangeViewModel;
  let changeModel: ChangeModel;
  let knownChange: ParsedChangeInfo;
  let knownChangeNoRevision: ChangeInfo;
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
    changeViewModel = testResolver(changeViewModelToken);
    changeModel = new ChangeModel(
      testResolver(navigationToken),
      changeViewModel,
      getAppContext().restApiService,
      testResolver(userModelToken),
      testResolver(pluginLoaderToken),
      getAppContext().reportingService
    );
    knownChangeNoRevision = {
      ...createChange(),
      status: ChangeStatus.NEW,
      current_revision_number: 2 as PatchSetNumber,
      messages: [],
    };
    knownChange = {
      ...knownChangeNoRevision,
      revisions: {
        sha1: {...createRevision(1), description: 'patch 1'},
        sha2: {...createRevision(2), description: 'patch 2'},
      },
      current_revision: 'abc' as CommitId,
    };
  });

  teardown(() => {
    testCompleted.next();
    changeModel.finalize();
  });

  suite('mergeability', async () => {
    let getMergeableStub: SinonStub;
    let mergeableApiResponse = false;

    setup(() => {
      getMergeableStub = stubRestApi('getMergeable').callsFake(() =>
        Promise.resolve(createMergeable(mergeableApiResponse))
      );
    });

    test('mergeability initially undefined', async () => {
      waitUntilObserved(
        changeModel.mergeable$,
        mergeable => mergeable === undefined
      );
      assert.isFalse(getMergeableStub.called);
    });

    test('mergeability true from change', async () => {
      changeModel.updateStateChange({...knownChange, mergeable: true});

      waitUntilObserved(
        changeModel.mergeable$,
        mergeable => mergeable === true
      );
      assert.isFalse(getMergeableStub.called);
    });

    test('mergeability false from change', async () => {
      changeModel.updateStateChange({...knownChange, mergeable: false});

      waitUntilObserved(
        changeModel.mergeable$,
        mergeable => mergeable === true
      );
      assert.isFalse(getMergeableStub.called);
    });

    test('mergeability false for MERGED change', async () => {
      changeModel.updateStateChange({
        ...knownChange,
        status: ChangeStatus.MERGED,
      });

      waitUntilObserved(
        changeModel.mergeable$,
        mergeable => mergeable === false
      );
      assert.isFalse(getMergeableStub.called);
    });

    test('mergeability false for ABANDONED change', async () => {
      changeModel.updateStateChange({
        ...knownChange,
        status: ChangeStatus.ABANDONED,
      });

      waitUntilObserved(
        changeModel.mergeable$,
        mergeable => mergeable === false
      );
      assert.isFalse(getMergeableStub.called);
    });

    test('mergeability true from API', async () => {
      mergeableApiResponse = true;
      changeModel.updateStateChange(knownChange);

      waitUntilObserved(
        changeModel.mergeable$,
        mergeable => mergeable === true
      );
      assert.isTrue(getMergeableStub.calledOnce);
    });

    test('mergeability false from API', async () => {
      mergeableApiResponse = false;
      changeModel.updateStateChange(knownChange);

      waitUntilObserved(
        changeModel.mergeable$,
        mergeable => mergeable === false
      );
      assert.isTrue(getMergeableStub.calledOnce);
    });
  });

  test('fireShowChange from overview', async () => {
    await waitForLoadingStatus(LoadingStatus.NOT_LOADED);
    const pluginLoader = testResolver(pluginLoaderToken);
    const jsApiService = pluginLoader.jsApiService;
    const showChangeStub = sinon.stub(jsApiService, 'handleShowChange');

    changeViewModel.updateState({
      childView: ChangeChildView.OVERVIEW,
      basePatchNum: 2 as BasePatchSetNum,
      patchNum: 3 as PatchSetNumber,
    });
    changeModel.updateState({
      change: createParsedChange(),
      mergeable: true,
    });

    assert.isTrue(showChangeStub.calledOnce);
    const detail: ShowChangeDetail = showChangeStub.lastCall.firstArg;
    assert.equal(detail.change?._number, createParsedChange()._number);
    assert.equal(detail.patchNum, 3 as PatchSetNumber);
    assert.equal(detail.basePatchNum, 2 as BasePatchSetNum);
    assert.equal(detail.info.mergeable, true);
  });

  test('fireShowChange from diff', async () => {
    await waitForLoadingStatus(LoadingStatus.NOT_LOADED);
    const pluginLoader = testResolver(pluginLoaderToken);
    const jsApiService = pluginLoader.jsApiService;
    const showChangeStub = sinon.stub(jsApiService, 'handleShowChange');

    changeViewModel.updateState({
      childView: ChangeChildView.DIFF,
      patchNum: 1 as PatchSetNumber,
    });
    changeModel.updateState({
      change: createParsedChange(),
      mergeable: true,
    });

    assert.isTrue(showChangeStub.calledOnce);
    const detail: ShowChangeDetail = showChangeStub.lastCall.firstArg;
    assert.equal(detail.change?._number, createParsedChange()._number);
    assert.equal(detail.patchNum, 1 as PatchSetNumber);
    assert.equal(detail.basePatchNum, PARENT);
    assert.equal(detail.info.mergeable, true);
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
    assert.deepEqual(state?.change, updateRevisionsWithCommitShas(knownChange));
  });

  test('reload a change', async () => {
    // setting up a loaded change
    const promise = mockPromise<ParsedChangeInfo | undefined>();
    const stub = stubRestApi('getChangeDetail').callsFake(() => promise);
    let state: ChangeState;
    testResolver(changeViewModelToken).setState(createChangeViewState());
    promise.resolve(knownChange);
    state = await waitForLoadingStatus(LoadingStatus.LOADED);
    assert.equal(stub.callCount, 1);

    // Reloading same change
    document.dispatchEvent(new CustomEvent('reload'));
    state = await waitForLoadingStatus(LoadingStatus.LOADING);
    assert.equal(stub.callCount, 3);
    assert.equal(stub.getCall(1).firstArg, undefined);
    assert.equal(stub.getCall(2).firstArg, TEST_NUMERIC_CHANGE_ID);
    assert.deepEqual(state?.change, undefined);

    promise.resolve(knownChange);
    state = await waitForLoadingStatus(LoadingStatus.LOADED);
    assert.equal(stub.callCount, 3);
    assert.deepEqual(state?.change, updateRevisionsWithCommitShas(knownChange));
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
    assert.deepEqual(state?.change, updateRevisionsWithCommitShas(otherChange));
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
    assert.deepEqual(state?.change, updateRevisionsWithCommitShas(knownChange));
  });

  test('changeModel.fetchChangeUpdates on latest', async () => {
    stubRestApi('getChange').returns(Promise.resolve(knownChangeNoRevision));
    const result = await changeModel.fetchChangeUpdates(knownChange);
    assert.isTrue(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.isNotOk(result.newMessages);
  });

  test('changeModel.fetchChangeUpdates not on latest', async () => {
    const actualChange = {
      ...knownChangeNoRevision,
      current_revision_number: 3 as PatchSetNumber,
    };
    stubRestApi('getChange').returns(Promise.resolve(actualChange));
    const result = await changeModel.fetchChangeUpdates(knownChange);
    assert.isFalse(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.isNotOk(result.newMessages);
  });

  test('changeModel.fetchChangeUpdates new status', async () => {
    const actualChange = {
      ...knownChangeNoRevision,
      status: ChangeStatus.MERGED,
    };
    stubRestApi('getChange').returns(Promise.resolve(actualChange));
    const result = await changeModel.fetchChangeUpdates(knownChange);
    assert.isTrue(result.isLatest);
    assert.equal(result.newStatus, ChangeStatus.MERGED);
    assert.isNotOk(result.newMessages);
  });

  test('changeModel.fetchChangeUpdates new messages', async () => {
    const actualChange = {
      ...knownChangeNoRevision,
      messages: [{...createChangeMessageInfo(), message: 'blah blah'}],
    };
    const getChangeStub = stubRestApi('getChange').returns(
      Promise.resolve(actualChange)
    );
    const result = await changeModel.fetchChangeUpdates(knownChange);
    assert.isTrue(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.deepEqual(getChangeStub.lastCall.args, [
      42 as NumericChangeId,
      undefined,
      undefined,
    ]);
    assert.deepEqual(result.newMessages, {
      ...createChangeMessageInfo(),
      message: 'blah blah',
    });
  });

  test('changeModel.fetchChangeUpdates new messages with extra options', async () => {
    const actualChange = {
      ...knownChangeNoRevision,
      messages: [{...createChangeMessageInfo(), message: 'blah blah'}],
    };
    const getChangeStub = stubRestApi('getChange').returns(
      Promise.resolve(actualChange)
    );
    const result = await changeModel.fetchChangeUpdates(
      knownChange,
      /* includeExtraOptions=*/ true
    );
    assert.isTrue(result.isLatest);
    assert.isNotOk(result.newStatus);
    assert.deepEqual(getChangeStub.lastCall.args, [
      42 as NumericChangeId,
      undefined,
      '80204',
    ]);
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

  test('revision$ selector latest', async () => {
    changeViewModel.updateState({patchNum: undefined});
    changeModel.updateState({change: knownChange});
    await waitUntilObserved(changeModel.revision$, x => x?._number === 2);
  });

  test('revision$ selector 1', async () => {
    changeViewModel.updateState({patchNum: 1 as PatchSetNumber});
    changeModel.updateState({change: knownChange});
    await waitUntilObserved(changeModel.revision$, x => x?._number === 1);
  });

  test('latestRevision$ selector latest', async () => {
    changeViewModel.updateState({patchNum: undefined});
    changeModel.updateState({change: knownChange});
    await waitUntilObserved(changeModel.latestRevision$, x => x?._number === 2);
  });

  test('latestRevision$ selector 1', async () => {
    changeViewModel.updateState({patchNum: 1 as PatchSetNumber});
    changeModel.updateState({change: knownChange});
    await waitUntilObserved(changeModel.latestRevision$, x => x?._number === 2);
  });
});
