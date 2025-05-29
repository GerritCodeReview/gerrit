/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {getAppContext} from '../../services/app-context';
import {ChangeModel, changeModelToken} from '../change/change-model';
import {assert} from '@open-wc/testing';
import {testResolver} from '../../test/common-test-setup';
import {RelatedChangesModel} from './related-changes-model';
import {configModelToken} from '../config/config-model';
import {SinonStub} from 'sinon';
import {
  ChangeInfo,
  RelatedChangesInfo,
  SubmittedTogetherInfo,
} from '../../types/common';
import {stubRestApi, waitUntilObserved} from '../../test/test-utils';
import {
  createChange,
  createChangeMessage,
  createParsedChange,
  createRelatedChangeAndCommitInfo,
  createRelatedChangesInfo,
} from '../../test/test-data-generators';
import {ChangeStatus, ReviewInputTag, TopicName} from '../../api/rest-api';
import {MessageTag} from '../../constants/constants';

suite('related-changes-model tests', () => {
  let model: RelatedChangesModel;
  let changeModel: ChangeModel;

  setup(async () => {
    changeModel = testResolver(changeModelToken);
    model = new RelatedChangesModel(
      changeModel,
      testResolver(configModelToken),
      getAppContext().restApiService
    );
    await waitUntilObserved(changeModel.change$, c => c === undefined);
  });

  teardown(() => {
    model.finalize();
  });

  test('register and fetch', async () => {
    assert.equal('', '');
  });

  suite('related changes and hasParent', async () => {
    let getRelatedChangesStub: SinonStub;
    let getRelatedChangesResponse: RelatedChangesInfo;
    let hasParent: boolean | undefined;

    setup(() => {
      getRelatedChangesStub = stubRestApi('getRelatedChanges').callsFake(() =>
        Promise.resolve(getRelatedChangesResponse)
      );
      model.hasParent$.subscribe(x => (hasParent = x));
    });

    test('relatedChanges initially undefined', async () => {
      await waitUntilObserved(
        model.relatedChanges$,
        relatedChanges => relatedChanges === undefined
      );
      assert.isFalse(getRelatedChangesStub.called);
      assert.isUndefined(hasParent);
    });

    test('relatedChanges loading empty', async () => {
      changeModel.updateStateChange({...createParsedChange()});

      await waitUntilObserved(
        model.relatedChanges$,
        relatedChanges => relatedChanges?.length === 0
      );
      assert.isTrue(getRelatedChangesStub.calledOnce);
      assert.isFalse(hasParent);
    });

    test('relatedChanges loading one change', async () => {
      getRelatedChangesResponse = {
        ...createRelatedChangesInfo(),
        changes: [createRelatedChangeAndCommitInfo()],
      };
      changeModel.updateStateChange({...createParsedChange()});

      await waitUntilObserved(
        model.relatedChanges$,
        relatedChanges => relatedChanges?.length === 1
      );
      assert.isTrue(getRelatedChangesStub.calledOnce);
      assert.isTrue(hasParent);
    });
  });

  suite('loadSubmittedTogether', async () => {
    let getChangesSubmittedTogetherStub: SinonStub;
    let getChangesSubmittedTogetherResponse: SubmittedTogetherInfo;

    setup(() => {
      getChangesSubmittedTogetherStub = stubRestApi(
        'getChangesSubmittedTogether'
      ).callsFake(() => Promise.resolve(getChangesSubmittedTogetherResponse));
    });

    test('submittedTogether initially undefined', async () => {
      await waitUntilObserved(
        model.submittedTogether$,
        submittedTogether => submittedTogether === undefined
      );
      assert.isFalse(getChangesSubmittedTogetherStub.called);
    });

    test('submittedTogether emits', async () => {
      getChangesSubmittedTogetherResponse = {
        changes: [createChange()],
        non_visible_changes: 0,
      };
      changeModel.updateStateChange({...createParsedChange()});

      await waitUntilObserved(
        model.submittedTogether$,
        submittedTogether => submittedTogether?.changes?.length === 1
      );
      assert.isTrue(getChangesSubmittedTogetherStub.calledOnce);
    });
  });

  suite('loadCherryPicks', async () => {
    let getChangeCherryPicksStub: SinonStub;
    let getChangeCherryPicksResponse: ChangeInfo[];

    setup(() => {
      getChangeCherryPicksStub = stubRestApi('getChangeCherryPicks').callsFake(
        () => Promise.resolve(getChangeCherryPicksResponse)
      );
    });

    test('cherryPicks initially undefined', async () => {
      await waitUntilObserved(
        model.cherryPicks$,
        cherryPicks => cherryPicks === undefined
      );
      assert.isFalse(getChangeCherryPicksStub.called);
    });

    test('cherryPicks emits', async () => {
      getChangeCherryPicksResponse = [createChange()];
      changeModel.updateStateChange({...createParsedChange()});

      await waitUntilObserved(
        model.cherryPicks$,
        cherryPicks => cherryPicks?.length === 1
      );
      assert.isTrue(getChangeCherryPicksStub.calledOnce);
    });
  });

  suite('loadConflictingChanges', async () => {
    let getChangeConflictsStub: SinonStub;
    let getChangeConflictsResponse: ChangeInfo[];

    setup(() => {
      getChangeConflictsStub = stubRestApi('getChangeConflicts').callsFake(() =>
        Promise.resolve(getChangeConflictsResponse)
      );
    });

    test('conflictingChanges initially undefined', async () => {
      await waitUntilObserved(
        model.conflictingChanges$,
        conflictingChanges => conflictingChanges === undefined
      );
      assert.isFalse(getChangeConflictsStub.called);
    });

    test('conflictingChanges not loaded for merged changes', async () => {
      getChangeConflictsResponse = [createChange()];
      changeModel.updateStateChange({
        ...createParsedChange(),
        mergeable: true,
        status: ChangeStatus.MERGED,
      });

      await waitUntilObserved(
        model.conflictingChanges$,
        conflictingChanges => conflictingChanges === undefined
      );
      assert.isFalse(getChangeConflictsStub.called);
    });

    test('conflictingChanges emits', async () => {
      getChangeConflictsResponse = [createChange()];
      changeModel.updateStateChange({...createParsedChange(), mergeable: true});

      await waitUntilObserved(
        model.conflictingChanges$,
        conflictingChanges => conflictingChanges?.length === 1
      );
      assert.isTrue(getChangeConflictsStub.calledOnce);
    });
  });

  suite('loadSameTopicChanges', async () => {
    let getChangesWithSameTopicStub: SinonStub;
    let getChangesWithSameTopicResponse: ChangeInfo[];

    setup(() => {
      getChangesWithSameTopicStub = stubRestApi(
        'getChangesWithSameTopic'
      ).callsFake(() => Promise.resolve(getChangesWithSameTopicResponse));
    });

    test('sameTopicChanges initially undefined', async () => {
      await waitUntilObserved(
        model.sameTopicChanges$,
        sameTopicChanges => sameTopicChanges === undefined
      );
      assert.isFalse(getChangesWithSameTopicStub.called);
    });

    test('sameTopicChanges emits', async () => {
      getChangesWithSameTopicResponse = [createChange()];
      changeModel.updateStateChange({
        ...createParsedChange(),
        topic: 'test-topic' as TopicName,
      });

      await waitUntilObserved(
        model.sameTopicChanges$,
        sameTopicChanges => sameTopicChanges?.length === 1
      );
      assert.isTrue(getChangesWithSameTopicStub.calledOnce);
    });
  });

  suite('loadRevertingChanges', async () => {
    let getChangeStub: SinonStub;

    setup(() => {
      getChangeStub = stubRestApi('getChange').callsFake(() =>
        Promise.resolve(createChange())
      );
    });

    test('revertingChanges initially empty', async () => {
      await waitUntilObserved(
        model.revertingChanges$,
        revertingChanges => revertingChanges.length === 0
      );
      assert.isFalse(getChangeStub.called);
    });

    test('revertingChanges empty when change does not contain a revert message', async () => {
      changeModel.updateStateChange(createParsedChange());
      await waitUntilObserved(
        model.revertingChanges$,
        revertingChanges => revertingChanges.length === 0
      );
      assert.isFalse(getChangeStub.called);
    });

    test('revertingChanges emits', async () => {
      changeModel.updateStateChange({
        ...createParsedChange(),
        messages: [
          {
            ...createChangeMessage(),
            message:
              'Created a revert of this change as If02ca1cd494579d6bb92a157bf1819e3689cd6b1',
            tag: MessageTag.TAG_REVERT as ReviewInputTag,
          },
        ],
      });

      await waitUntilObserved(
        model.revertingChanges$,
        revertingChanges => revertingChanges?.length === 1
      );
      assert.isTrue(getChangeStub.calledOnce);
    });
  });
});
