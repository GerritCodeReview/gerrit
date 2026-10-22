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
  createServerInfo,
} from '../../test/test-data-generators';
import {
  ChangeStatus,
  NumericChangeId,
  ReviewInputTag,
  TopicName,
} from '../../api/rest-api';
import {MessageTag} from '../../constants/constants';
import {ConfigModel, configModelToken} from '../config/config-model';

suite('related-changes-model tests', () => {
  let model: RelatedChangesModel;
  let changeModel: ChangeModel;
  let configModel: ConfigModel;

  setup(async () => {
    changeModel = testResolver(changeModelToken);
    configModel = testResolver(configModelToken);
    model = new RelatedChangesModel(
      changeModel,
      configModel,
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

  suite('topicChanges$, prevTopicChange$, and nextTopicChange$', () => {
    let getChangesWithSameTopicStub: SinonStub;
    let getChangesWithSameTopicResponse: ChangeInfo[];
    let getChangesSubmittedTogetherStub: SinonStub;
    let getChangesSubmittedTogetherResponse: SubmittedTogetherInfo;

    setup(() => {
      getChangesWithSameTopicResponse = [];
      getChangesSubmittedTogetherResponse = {
        changes: [],
        non_visible_changes: 0,
      };
      getChangesWithSameTopicStub = stubRestApi(
        'getChangesWithSameTopic'
      ).callsFake(() => Promise.resolve(getChangesWithSameTopicResponse));
      getChangesSubmittedTogetherStub = stubRestApi(
        'getChangesSubmittedTogether'
      ).callsFake(() => Promise.resolve(getChangesSubmittedTogetherResponse));
    });

    test('undefined when change has no topic', async () => {
      changeModel.updateStateChange({
        ...createParsedChange(),
        topic: undefined,
      });

      await waitUntilObserved(
        model.topicChanges$,
        topicChanges => topicChanges.length === 0
      );
      await waitUntilObserved(
        model.prevTopicChange$,
        prev => prev === undefined
      );
      await waitUntilObserved(
        model.nextTopicChange$,
        next => next === undefined
      );
      assert.isFalse(getChangesWithSameTopicStub.called);
    });

    test('wraps prev from first topic change and navigates next with sameTopicChanges (submit_whole_topic=false)', async () => {
      const topic = 'my-topic' as TopicName;
      const change101 = createChange({
        _number: 101 as NumericChangeId,
        topic,
        subject: 'Change 101',
      });
      const change102 = createChange({
        _number: 102 as NumericChangeId,
        topic,
        subject: 'Change 102',
      });

      // Sorted top-to-bottom order is [103, 102, 101].
      // Start at 103 (first item in the list): prev wraps to 101, next is 102.
      getChangesWithSameTopicResponse = [change101, change102];
      changeModel.updateStateChange({
        ...createParsedChange(),
        _number: 103 as NumericChangeId,
        topic,
        subject: 'Change 103',
      });

      await waitUntilObserved(
        model.topicChanges$,
        changes => changes.length === 3
      );
      await waitUntilObserved(
        model.prevTopicChange$,
        prev => prev?._number === (101 as NumericChangeId)
      );
      await waitUntilObserved(
        model.nextTopicChange$,
        next => next?._number === (102 as NumericChangeId)
      );
    });

    test('navigates prev and wraps next from last topic change with sameTopicChanges (submit_whole_topic=false)', async () => {
      const topic = 'my-topic' as TopicName;
      const change102 = createChange({
        _number: 102 as NumericChangeId,
        topic,
        subject: 'Change 102',
      });
      const change103 = createChange({
        _number: 103 as NumericChangeId,
        topic,
        subject: 'Change 103',
      });

      // Sorted top-to-bottom order is [103, 102, 101].
      // Start at 101 (last item in the list): prev is 102, next wraps to 103.
      getChangesWithSameTopicResponse = [change103, change102];
      changeModel.updateStateChange({
        ...createParsedChange(),
        _number: 101 as NumericChangeId,
        topic,
        subject: 'Change 101',
      });

      await waitUntilObserved(
        model.prevTopicChange$,
        prev => prev?._number === (102 as NumericChangeId)
      );
      await waitUntilObserved(
        model.nextTopicChange$,
        next => next?._number === (103 as NumericChangeId)
      );
    });

    test('navigates and wraps around with submittedTogether (submit_whole_topic=true)', async () => {
      const serverConfig = createServerInfo();
      serverConfig.change.submit_whole_topic = true;
      configModel.updateServerConfig(serverConfig);

      const topic = 'whole-topic' as TopicName;
      const change203 = createChange({
        _number: 203 as NumericChangeId,
        topic,
        subject: 'Change 203',
      });
      const change202 = createChange({
        _number: 202 as NumericChangeId,
        topic,
        subject: 'Change 202',
      });
      const change201 = createChange({
        _number: 201 as NumericChangeId,
        topic,
        subject: 'Change 201',
      });

      getChangesSubmittedTogetherResponse = {
        changes: [change203, change202, change201],
        non_visible_changes: 0,
      };
      changeModel.updateStateChange({
        ...createParsedChange(),
        _number: 202 as NumericChangeId,
        topic,
        subject: 'Change 202',
      });

      await waitUntilObserved(
        model.prevTopicChange$,
        prev => prev?._number === (203 as NumericChangeId)
      );
      await waitUntilObserved(
        model.nextTopicChange$,
        next => next?._number === (201 as NumericChangeId)
      );
      assert.isTrue(getChangesSubmittedTogetherStub.called);
      assert.isFalse(getChangesWithSameTopicStub.called);
    });
  });
});
