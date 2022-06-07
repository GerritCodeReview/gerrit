/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  createAccountWithIdNameAndEmail,
  createChange,
  createGroupInfo,
  createRevisions,
} from '../../test/test-data-generators';
import {
  ChangeInfo,
  NumericChangeId,
  ChangeStatus,
  HttpMethod,
  SubmitRequirementStatus,
  AccountInfo,
  ReviewerState,
  AccountId,
  GroupInfo,
} from '../../api/rest-api';
import {BulkActionsModel, LoadingState} from './bulk-actions-model';
import {getAppContext} from '../../services/app-context';
import '../../test/common-test-setup-karma';
import {stubRestApi, waitUntilObserved} from '../../test/test-utils';
import {mockPromise} from '../../test/test-utils';
import {SinonStubbedMember} from 'sinon';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {ReviewInput} from '../../types/common';

suite('bulk actions model test', () => {
  let bulkActionsModel: BulkActionsModel;
  setup(() => {
    bulkActionsModel = new BulkActionsModel(getAppContext().restApiService);
  });

  test('does not request detailed changes when no changes are synced', () => {
    const detailedActionsStub = stubRestApi('getDetailedChangesWithActions');

    bulkActionsModel.sync([]);

    assert.isTrue(detailedActionsStub.notCalled);
  });

  test('add changes before sync', () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;

    assert.isEmpty(bulkActionsModel.getState().selectedChangeNums);

    assert.throws(() => bulkActionsModel.addSelectedChangeNum(c1._number));
    assert.isEmpty(bulkActionsModel.getState().selectedChangeNums);

    bulkActionsModel.sync([c1, c2]);

    bulkActionsModel.addSelectedChangeNum(c2._number);
    assert.sameMembers(bulkActionsModel.getState().selectedChangeNums, [
      c2._number,
    ]);

    bulkActionsModel.removeSelectedChangeNum(c2._number);
    assert.isEmpty(bulkActionsModel.getState().selectedChangeNums);
  });

  test('add and remove selected changes', () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;
    bulkActionsModel.sync([c1, c2]);

    assert.isEmpty(bulkActionsModel.getState().selectedChangeNums);

    bulkActionsModel.addSelectedChangeNum(c1._number);
    assert.sameMembers(bulkActionsModel.getState().selectedChangeNums, [
      c1._number,
    ]);

    bulkActionsModel.addSelectedChangeNum(c2._number);
    assert.sameMembers(bulkActionsModel.getState().selectedChangeNums, [
      c1._number,
      c2._number,
    ]);

    bulkActionsModel.removeSelectedChangeNum(c1._number);
    assert.sameMembers(bulkActionsModel.getState().selectedChangeNums, [
      c2._number,
    ]);

    bulkActionsModel.removeSelectedChangeNum(c2._number);
    assert.isEmpty(bulkActionsModel.getState().selectedChangeNums);
  });

  test('clears selected change numbers', async () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;
    bulkActionsModel.sync([c1, c2]);
    bulkActionsModel.addSelectedChangeNum(c1._number);
    bulkActionsModel.addSelectedChangeNum(c2._number);
    let selectedChangeNums = await waitUntilObserved(
      bulkActionsModel.selectedChangeNums$,
      s => s.length === 2
    );
    let totalChangeCount = await waitUntilObserved(
      bulkActionsModel.totalChangeCount$,
      totalChangeCount => totalChangeCount === 2
    );
    assert.sameMembers(selectedChangeNums, [c1._number, c2._number]);
    assert.equal(totalChangeCount, 2);

    bulkActionsModel.clearSelectedChangeNums();
    selectedChangeNums = await waitUntilObserved(
      bulkActionsModel.selectedChangeNums$,
      s => s.length === 0
    );
    totalChangeCount = await waitUntilObserved(
      bulkActionsModel.totalChangeCount$,
      totalChangeCount => totalChangeCount === 2
    );

    assert.isEmpty(selectedChangeNums);
    assert.equal(totalChangeCount, 2);
  });

  test('selects all changes', async () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;
    bulkActionsModel.sync([c1, c2]);
    let selectedChangeNums = await waitUntilObserved(
      bulkActionsModel.selectedChangeNums$,
      s => s.length === 0
    );
    let totalChangeCount = await waitUntilObserved(
      bulkActionsModel.totalChangeCount$,
      totalChangeCount => totalChangeCount === 2
    );
    assert.isEmpty(selectedChangeNums);
    assert.equal(totalChangeCount, 2);

    bulkActionsModel.selectAll();
    selectedChangeNums = await waitUntilObserved(
      bulkActionsModel.selectedChangeNums$,
      s => s.length === 2
    );
    totalChangeCount = await waitUntilObserved(
      bulkActionsModel.totalChangeCount$,
      totalChangeCount => totalChangeCount === 2
    );

    assert.sameMembers(selectedChangeNums, [c1._number, c2._number]);
    assert.equal(totalChangeCount, 2);
  });

  suite('abandon changes', () => {
    let detailedActionsStub: SinonStubbedMember<
      RestApiService['getDetailedChangesWithActions']
    >;
    setup(async () => {
      detailedActionsStub = stubRestApi('getDetailedChangesWithActions');
      const c1 = createChange();
      c1._number = 1 as NumericChangeId;
      const c2 = createChange();
      c2._number = 2 as NumericChangeId;

      detailedActionsStub.returns(
        Promise.resolve([
          {...c1, actions: {abandon: {method: HttpMethod.POST}}},
          {...c2, status: ChangeStatus.ABANDONED},
        ])
      );

      bulkActionsModel.sync([c1, c2]);

      bulkActionsModel.addSelectedChangeNum(c1._number);
      bulkActionsModel.addSelectedChangeNum(c2._number);
    });

    test('already abandoned change does not call executeChangeAction', () => {
      const actionStub = stubRestApi('executeChangeAction');
      bulkActionsModel.abandonChanges();
      assert.equal(actionStub.callCount, 1);
      assert.deepEqual(actionStub.lastCall.args.slice(0, 5), [
        1 as NumericChangeId,
        HttpMethod.POST,
        '/abandon',
        undefined,
        {message: ''},
      ]);
    });
  });

  suite('add reviewers', () => {
    const accounts: AccountInfo[] = [
      createAccountWithIdNameAndEmail(0),
      createAccountWithIdNameAndEmail(1),
    ];
    const groups: GroupInfo[] = [createGroupInfo('groupId')];
    const changes: ChangeInfo[] = [
      {
        ...createChange(),
        _number: 1 as NumericChangeId,
        subject: 'Subject 1',
        reviewers: {
          REVIEWER: [accounts[0]],
          CC: [accounts[1]],
        },
      },
      {
        ...createChange(),
        _number: 2 as NumericChangeId,
        subject: 'Subject 2',
      },
    ];
    let saveChangeReviewStub: sinon.SinonStub;

    setup(async () => {
      saveChangeReviewStub = stubRestApi('saveChangeReview').resolves(
        new Response()
      );
      stubRestApi('getDetailedChangesWithActions').resolves([
        {...changes[0], actions: {abandon: {method: HttpMethod.POST}}},
        {...changes[1], status: ChangeStatus.ABANDONED},
      ]);
      bulkActionsModel.sync(changes);
      bulkActionsModel.addSelectedChangeNum(changes[0]._number);
      bulkActionsModel.addSelectedChangeNum(changes[1]._number);
    });

    test('adds reviewers/cc only to changes that need it', async () => {
      bulkActionsModel.addReviewers(
        new Map([
          [ReviewerState.REVIEWER, [accounts[0], groups[0]]],
          [ReviewerState.CC, [accounts[1]]],
        ]),
        '<GERRIT_ACCOUNT_12345> replied on the change'
      );

      assert.isTrue(saveChangeReviewStub.calledTwice);
      // changes[0] only adds the group since it already has the other
      // reviewer/CCs
      assert.sameDeepOrderedMembers(saveChangeReviewStub.firstCall.args, [
        changes[0]._number,
        'current',
        {
          reviewers: [{reviewer: groups[0].id, state: ReviewerState.REVIEWER}],
          ignore_automatic_attention_set_rules: true,
          add_to_attention_set: [
            {
              reason: '<GERRIT_ACCOUNT_12345> replied on the change',
              user: groups[0].id,
            },
          ],
        },
      ]);
      assert.sameDeepOrderedMembers(saveChangeReviewStub.secondCall.args, [
        changes[1]._number,
        'current',
        {
          reviewers: [
            {reviewer: accounts[0]._account_id, state: ReviewerState.REVIEWER},
            {reviewer: groups[0].id, state: ReviewerState.REVIEWER},
            {reviewer: accounts[1]._account_id, state: ReviewerState.CC},
          ],
          ignore_automatic_attention_set_rules: true,
          add_to_attention_set: [
            {
              reason: '<GERRIT_ACCOUNT_12345> replied on the change',
              user: accounts[0]._account_id,
            },
            {
              reason: '<GERRIT_ACCOUNT_12345> replied on the change',
              user: groups[0].id,
            },
          ],
        },
      ]);
    });
  });

  suite('voteChanges', () => {
    let detailedActionsStub: SinonStubbedMember<
      RestApiService['getDetailedChangesWithActions']
    >;
    setup(async () => {
      const c1 = {...createChange(), revisions: createRevisions(10)};
      c1._number = 1 as NumericChangeId;
      const c2 = {...createChange(), revisions: createRevisions(4)};
      c2._number = 2 as NumericChangeId;

      detailedActionsStub = stubRestApi('getDetailedChangesWithActions');
      detailedActionsStub.returns(
        Promise.resolve([
          {...c1, actions: {abandon: {method: HttpMethod.POST}}},
          {...c2, status: ChangeStatus.ABANDONED},
        ])
      );

      await bulkActionsModel.sync([c1, c2]);

      bulkActionsModel.addSelectedChangeNum(c1._number);
      bulkActionsModel.addSelectedChangeNum(c2._number);
    });

    test('vote changes', () => {
      const reviewStub = stubRestApi('saveChangeReview');
      const reviewInput: ReviewInput = {
        labels: {
          a: 1,
        },
      };
      bulkActionsModel.voteChanges(reviewInput);
      assert.equal(reviewStub.callCount, 2);
      assert.deepEqual(reviewStub.firstCall.args.slice(0, 3), [
        1 as NumericChangeId,
        'current',
        {
          labels: {
            a: 1,
          },
        },
      ]);

      assert.deepEqual(reviewStub.secondCall.args.slice(0, 3), [
        2 as NumericChangeId,
        'current',
        {
          labels: {
            a: 1,
          },
        },
      ]);
    });
  });

  test('stale changes are removed from the model', async () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;
    bulkActionsModel.sync([c1, c2]);

    bulkActionsModel.addSelectedChangeNum(c1._number);
    bulkActionsModel.addSelectedChangeNum(c2._number);

    let selectedChangeNums = await waitUntilObserved(
      bulkActionsModel.selectedChangeNums$,
      s => s.length === 2
    );
    let totalChangeCount = await waitUntilObserved(
      bulkActionsModel.totalChangeCount$,
      totalChangeCount => totalChangeCount === 2
    );

    assert.sameMembers(selectedChangeNums, [c1._number, c2._number]);
    assert.equal(totalChangeCount, 2);

    bulkActionsModel.sync([c1]);
    selectedChangeNums = await waitUntilObserved(
      bulkActionsModel.selectedChangeNums$,
      s => s.length === 1
    );
    totalChangeCount = await waitUntilObserved(
      bulkActionsModel.totalChangeCount$,
      totalChangeCount => totalChangeCount === 1
    );

    assert.sameMembers(selectedChangeNums, [c1._number]);
    assert.equal(totalChangeCount, 1);
  });

  test('sync fetches new changes', async () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;

    assert.equal(
      bulkActionsModel.getState().loadingState,
      LoadingState.NOT_SYNCED
    );

    bulkActionsModel.sync([c1, c2]);
    await waitUntilObserved(
      bulkActionsModel.loadingState$,
      s => s === LoadingState.LOADING
    );

    await waitUntilObserved(
      bulkActionsModel.loadingState$,
      s => s === LoadingState.LOADED
    );
    const model = bulkActionsModel.getState();

    assert.strictEqual(
      model.allChanges.get(1 as NumericChangeId)?.subject,
      'Subject 1'
    );
    assert.strictEqual(
      model.allChanges.get(2 as NumericChangeId)?.subject,
      'Subject 2'
    );
  });

  test('sync retains keys from original change including reviewers', async () => {
    const c1: ChangeInfo = {
      ...createChange(),
      _number: 1 as NumericChangeId,
      submit_requirements: [
        {
          name: 'a',
          status: SubmitRequirementStatus.FORCED,
          submittability_expression_result: {
            expression: 'b',
          },
        },
      ],
      reviewers: {
        REVIEWER: [{_account_id: 1 as AccountId, display_name: 'MyName'}],
      },
    };

    stubRestApi('getDetailedChangesWithActions').callsFake(() => {
      const change: ChangeInfo = {
        ...createChange(),
        _number: 1 as NumericChangeId,
        actions: {abandon: {}},
        // detailed data will be missing names
        reviewers: {REVIEWER: [createAccountWithIdNameAndEmail()]},
      };
      assert.isNotOk(change.submit_requirements);
      return Promise.resolve([change]);
    });

    bulkActionsModel.sync([c1]);

    await waitUntilObserved(
      bulkActionsModel.loadingState$,
      s => s === LoadingState.LOADED
    );

    const changeAfterSync = bulkActionsModel
      .getState()
      .allChanges.get(1 as NumericChangeId);
    assert.deepEqual(changeAfterSync!.submit_requirements, [
      {
        name: 'a',
        status: SubmitRequirementStatus.FORCED,
        submittability_expression_result: {
          expression: 'b',
        },
      },
    ]);
    assert.deepEqual(changeAfterSync!.actions, {abandon: {}});
    // original reviewers are kept, which includes more details than loaded ones
    assert.deepEqual(changeAfterSync!.reviewers, c1.reviewers);
  });

  test('sync ignores outdated fetch responses', async () => {
    const c1 = createChange();
    c1._number = 1 as NumericChangeId;
    const c2 = createChange();
    c2._number = 2 as NumericChangeId;

    const responsePromise1 = mockPromise<ChangeInfo[]>();
    let promise = responsePromise1;
    const getChangesStub = stubRestApi(
      'getDetailedChangesWithActions'
    ).callsFake(() => promise);
    bulkActionsModel.sync([c1, c2]);
    assert.strictEqual(getChangesStub.callCount, 1);
    await waitUntilObserved(
      bulkActionsModel.loadingState$,
      s => s === LoadingState.LOADING
    );
    const responsePromise2 = mockPromise<ChangeInfo[]>();

    promise = responsePromise2;
    bulkActionsModel.sync([c1, c2]);
    assert.strictEqual(getChangesStub.callCount, 2);

    responsePromise2.resolve([
      {...createChange(), _number: 1, subject: 'Subject 1'},
      {...createChange(), _number: 2, subject: 'Subject 2'},
    ] as ChangeInfo[]);

    await waitUntilObserved(
      bulkActionsModel.loadingState$,
      s => s === LoadingState.LOADED
    );
    const model = bulkActionsModel.getState();
    assert.strictEqual(
      model.allChanges.get(1 as NumericChangeId)?.subject,
      'Subject 1'
    );
    assert.strictEqual(
      model.allChanges.get(2 as NumericChangeId)?.subject,
      'Subject 2'
    );

    // Resolve the old promise.
    responsePromise1.resolve([
      {...createChange(), _number: 1, subject: 'Subject 1-old'},
      {...createChange(), _number: 2, subject: 'Subject 2-old'},
    ] as ChangeInfo[]);
    await flush();
    const model2 = bulkActionsModel.getState();

    // No change should happen.
    assert.strictEqual(
      model2.allChanges.get(1 as NumericChangeId)?.subject,
      'Subject 1'
    );
    assert.strictEqual(
      model2.allChanges.get(2 as NumericChangeId)?.subject,
      'Subject 2'
    );
  });
});
