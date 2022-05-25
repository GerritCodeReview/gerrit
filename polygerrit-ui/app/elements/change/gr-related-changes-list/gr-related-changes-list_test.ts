/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import {SinonStubbedMember} from 'sinon';
import {PluginApi} from '../../../api/plugin';
import {ChangeStatus} from '../../../constants/constants';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import '../../../test/common-test-setup-karma';
import {
  createChange,
  createCommitInfoWithRequiredCommit,
  createParsedChange,
  createRelatedChangeAndCommitInfo,
  createRelatedChangesInfo,
  createRevision,
  createSubmittedTogetherInfo,
} from '../../../test/test-data-generators';
import {
  query,
  queryAndAssert,
  resetPlugins,
  stubRestApi,
} from '../../../test/test-utils';
import {
  ChangeId,
  ChangeInfo,
  CommitId,
  NumericChangeId,
  PatchSetNum,
  RelatedChangeAndCommitInfo,
  RelatedChangesInfo,
  SubmittedTogetherInfo,
} from '../../../types/common';
import {ParsedChangeInfo} from '../../../types/types';
import {GrEndpointDecorator} from '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import './gr-related-changes-list';
import {
  ChangeMarkersInList,
  GrRelatedChangesList,
  GrRelatedCollapse,
  Section,
} from './gr-related-changes-list';

const basicFixture = fixtureFromElement('gr-related-changes-list');

suite('gr-related-changes-list', () => {
  let element: GrRelatedChangesList;

  setup(() => {
    element = basicFixture.instantiate();
  });

  suite('show when collapsed', () => {
    function genBoolArray(
      instructions: Array<{
        len: number;
        v: boolean;
      }>
    ) {
      return instructions.flatMap(inst =>
        Array.from({length: inst.len}, () => inst.v)
      );
    }

    function checkShowWhenCollapsed(
      expected: boolean[],
      markersPredicate: (index: number) => ChangeMarkersInList,
      msg: string
    ) {
      for (let i = 0; i < expected.length; i++) {
        assert.equal(
          markersPredicate(i).showWhenCollapsed,
          expected[i],
          `change on pos (${i}) ${msg}`
        );
      }
    }

    test('size 5', () => {
      const markersPredicate = element.markersPredicateFactory(10, 4, 5);
      const expectedCollapsing = genBoolArray([
        {len: 2, v: false},
        {len: 5, v: true},
        {len: 3, v: false},
      ]);
      checkShowWhenCollapsed(
        expectedCollapsing,
        markersPredicate,
        'highlight 4, size 10, size 5'
      );

      const markersPredicate2 = element.markersPredicateFactory(10, 8, 5);
      const expectedCollapsing2 = genBoolArray([
        {len: 5, v: false},
        {len: 5, v: true},
      ]);
      checkShowWhenCollapsed(
        expectedCollapsing2,
        markersPredicate2,
        'highlight 8, size 10, size 5'
      );

      const markersPredicate3 = element.markersPredicateFactory(10, 1, 5);
      const expectedCollapsing3 = genBoolArray([
        {len: 5, v: true},
        {len: 5, v: false},
      ]);
      checkShowWhenCollapsed(
        expectedCollapsing3,
        markersPredicate3,
        'highlight 1, size 10, size 5'
      );
    });

    test('size 4', () => {
      const markersPredicate = element.markersPredicateFactory(10, 4, 4);
      const expectedCollapsing = genBoolArray([
        {len: 2, v: false},
        {len: 4, v: true},
        {len: 4, v: false},
      ]);
      checkShowWhenCollapsed(
        expectedCollapsing,
        markersPredicate,
        'highlight 4, len 10, size 4'
      );

      const markersPredicate2 = element.markersPredicateFactory(10, 8, 4);
      const expectedCollapsing2 = genBoolArray([
        {len: 6, v: false},
        {len: 4, v: true},
      ]);
      checkShowWhenCollapsed(
        expectedCollapsing2,
        markersPredicate2,
        'highlight 8, len 10, size 4'
      );

      const markersPredicate3 = element.markersPredicateFactory(10, 1, 4);
      const expectedCollapsing3 = genBoolArray([
        {len: 4, v: true},
        {len: 6, v: false},
      ]);
      checkShowWhenCollapsed(
        expectedCollapsing3,
        markersPredicate3,
        'highlight 1, len 10, size 4'
      );
    });
  });

  suite('section size', () => {
    test('1 section', () => {
      const sectionSize = element.sectionSizeFactory(20, 0, 0, 0, 0);
      assert.equal(sectionSize(Section.RELATED_CHANGES), 15);
      const sectionSize2 = element.sectionSizeFactory(0, 0, 10, 0, 0);
      assert.equal(sectionSize2(Section.SAME_TOPIC), 10);
    });
    test('2 sections', () => {
      const sectionSize = element.sectionSizeFactory(20, 20, 0, 0, 0);
      assert.equal(sectionSize(Section.RELATED_CHANGES), 11);
      assert.equal(sectionSize(Section.SUBMITTED_TOGETHER), 3);
      const sectionSize2 = element.sectionSizeFactory(4, 0, 10, 0, 0);
      assert.equal(sectionSize2(Section.RELATED_CHANGES), 4);
      assert.equal(sectionSize2(Section.SAME_TOPIC), 10);
    });
    test('many sections', () => {
      const sectionSize = element.sectionSizeFactory(20, 20, 3, 3, 3);
      assert.equal(sectionSize(Section.RELATED_CHANGES), 3);
      assert.equal(sectionSize(Section.SUBMITTED_TOGETHER), 3);
      const sectionSize2 = element.sectionSizeFactory(4, 1, 10, 1, 1);
      assert.equal(sectionSize2(Section.RELATED_CHANGES), 4);
      assert.equal(sectionSize2(Section.SAME_TOPIC), 4);
    });
  });

  suite('test first non-empty list', () => {
    const relatedChangeInfo: RelatedChangesInfo = {
      ...createRelatedChangesInfo(),
      changes: [createRelatedChangeAndCommitInfo()],
    };
    const submittedTogether: SubmittedTogetherInfo = {
      ...createSubmittedTogetherInfo(),
      changes: [createChange()],
    };

    setup(() => {
      element.change = createParsedChange();
      element.patchNum = 1 as PatchSetNum;
    });

    test('first list', async () => {
      stubRestApi('getRelatedChanges').returns(
        Promise.resolve(relatedChangeInfo)
      );
      await element.reload();
      const section = queryAndAssert<HTMLElement>(element, '#relatedChanges');
      const relatedChanges = queryAndAssert<GrRelatedCollapse>(
        section,
        'gr-related-collapse'
      );
      assert.isTrue(relatedChanges.classList.contains('first'));
    });

    test('first empty second non-empty', async () => {
      stubRestApi('getRelatedChanges').returns(
        Promise.resolve(createRelatedChangesInfo())
      );
      stubRestApi('getChangesSubmittedTogether').returns(
        Promise.resolve(submittedTogether)
      );
      await element.reload();
      const relatedChanges = query<HTMLElement>(element, '#relatedChanges');
      assert.notExists(relatedChanges);
      const submittedTogetherSection = queryAndAssert<GrRelatedCollapse>(
        queryAndAssert<HTMLElement>(element, '#submittedTogether'),
        'gr-related-collapse'
      );
      assert.isTrue(submittedTogetherSection.classList.contains('first'));
    });

    test('first non-empty second empty third non-empty', async () => {
      stubRestApi('getRelatedChanges').returns(
        Promise.resolve(relatedChangeInfo)
      );
      stubRestApi('getChangesSubmittedTogether').returns(
        Promise.resolve(createSubmittedTogetherInfo())
      );
      stubRestApi('getChangeCherryPicks').returns(
        Promise.resolve([createChange()])
      );
      await element.reload();
      const relatedChanges = queryAndAssert<GrRelatedCollapse>(
        queryAndAssert<HTMLElement>(element, '#relatedChanges'),
        'gr-related-collapse'
      );
      assert.isTrue(relatedChanges.classList.contains('first'));
      const submittedTogetherSection = query<HTMLElement>(
        element,
        '#submittedTogether'
      );
      assert.notExists(submittedTogetherSection);
      const cherryPicks = queryAndAssert<GrRelatedCollapse>(
        queryAndAssert<HTMLElement>(element, '#cherryPicks'),
        'gr-related-collapse'
      );
      assert.isFalse(cherryPicks.classList.contains('first'));
    });
  });

  test('_changesEqual', () => {
    const change1: ChangeInfo = {
      ...createChange(),
      change_id: '123' as ChangeId,
      _number: 0 as NumericChangeId,
    };
    const change2: ChangeInfo = {
      ...createChange(),
      change_id: '456' as ChangeId,
      _number: 1 as NumericChangeId,
    };
    const change3: ChangeInfo = {
      ...createChange(),
      change_id: '123' as ChangeId,
      _number: 2 as NumericChangeId,
    };
    const change4: RelatedChangeAndCommitInfo = {
      ...createRelatedChangeAndCommitInfo(),
      change_id: '123' as ChangeId,
      _change_number: 1 as NumericChangeId,
    };

    assert.isTrue(element._changesEqual(change1, change1));
    assert.isFalse(element._changesEqual(change1, change2));
    assert.isFalse(element._changesEqual(change1, change3));
    assert.isTrue(element._changesEqual(change2, change4));
  });

  test('_getChangeNumber', () => {
    const change1: ChangeInfo = {
      ...createChange(),
      change_id: '123' as ChangeId,
      _number: 0 as NumericChangeId,
    };
    const change2: ChangeInfo = {
      ...createChange(),
      change_id: '456' as ChangeId,
      _number: 1 as NumericChangeId,
    };
    assert.equal(element._getChangeNumber(change1), 0);
    assert.equal(element._getChangeNumber(change2), 1);
  });

  suite('get conflicts tests', () => {
    let element: GrRelatedChangesList;
    let conflictsStub: SinonStubbedMember<RestApiService['getChangeConflicts']>;

    setup(() => {
      element = basicFixture.instantiate();
      conflictsStub = stubRestApi('getChangeConflicts').returns(
        Promise.resolve(undefined)
      );
    });

    test('request conflicts if open and mergeable', () => {
      element.patchNum = 7 as PatchSetNum;
      element.change = {
        ...createParsedChange(),
        change_id: '123' as ChangeId,
        status: ChangeStatus.NEW,
      };
      element.mergeable = true;
      element.reload();
      assert.isTrue(conflictsStub.called);
    });

    test('does not request conflicts if closed and mergeable', () => {
      element.patchNum = 7 as PatchSetNum;
      element.change = {
        ...createParsedChange(),
        change_id: '123' as ChangeId,
        status: ChangeStatus.NEW,
      };
      element.reload();
      assert.isFalse(conflictsStub.called);
    });

    test('does not request conflicts if open and not mergeable', () => {
      element.patchNum = 7 as PatchSetNum;
      element.change = {
        ...createParsedChange(),
        change_id: '123' as ChangeId,
        status: ChangeStatus.NEW,
      };
      element.mergeable = false;
      element.reload();
      assert.isFalse(conflictsStub.called);
    });

    test('doesnt request conflicts if closed and not mergeable', () => {
      element.patchNum = 7 as PatchSetNum;
      element.change = {
        ...createParsedChange(),
        change_id: '123' as ChangeId,
        status: ChangeStatus.NEW,
      };
      element.mergeable = false;
      element.reload();
      assert.isFalse(conflictsStub.called);
    });
  });

  test('connected revisions', () => {
    const change: ParsedChangeInfo = {
      ...createParsedChange(),
      revisions: {
        e3c6d60783bfdec9ebae7dcfec4662360433449e: createRevision(1),
        '26e5e4c9c7ae31cbd876271cca281ce22b413997': createRevision(2),
        bf7884d695296ca0c91702ba3e2bc8df0f69a907: createRevision(7),
        b5fc49f2e67d1889d5275cac04ad3648f2ec7fe3: createRevision(5),
        d6bcee67570859ccb684873a85cf50b1f0e96fda: createRevision(6),
        cc960918a7f90388f4a9e05753d0f7b90ad44546: createRevision(3),
        '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6': createRevision(4),
      },
    };
    let patchNum = 7 as PatchSetNum;
    let relatedChanges: RelatedChangeAndCommitInfo[] = [
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '2cebeedfb1e80f4b872d0a13ade529e70652c0c8'
          ),
          parents: [
            {
              commit: '87ed20b241576b620bbaa3dfd47715ce6782b7dd' as CommitId,
              subject: 'subject1',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '87ed20b241576b620bbaa3dfd47715ce6782b7dd'
          ),
          parents: [
            {
              commit: '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb' as CommitId,
              subject: 'subject2',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb'
          ),
          parents: [
            {
              commit: 'b0ccb183494a8e340b8725a2dc553967d61e6dae' as CommitId,
              subject: 'subject3',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            'b0ccb183494a8e340b8725a2dc553967d61e6dae'
          ),
          parents: [
            {
              commit: 'bf7884d695296ca0c91702ba3e2bc8df0f69a907' as CommitId,
              subject: 'subject4',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            'bf7884d695296ca0c91702ba3e2bc8df0f69a907'
          ),
          parents: [
            {
              commit: '613bc4f81741a559c6667ac08d71dcc3348f73ce' as CommitId,
              subject: 'subject5',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '613bc4f81741a559c6667ac08d71dcc3348f73ce'
          ),
          parents: [
            {
              commit: '455ed9cd27a16bf6991f04dcc57ef575dc4d5e75' as CommitId,
              subject: 'subject6',
            },
          ],
        },
      },
    ];

    let connectedChanges = element._computeConnectedRevisions(
      change,
      patchNum,
      relatedChanges
    );
    assert.deepEqual(connectedChanges, [
      '613bc4f81741a559c6667ac08d71dcc3348f73ce',
      'bf7884d695296ca0c91702ba3e2bc8df0f69a907',
      'bf7884d695296ca0c91702ba3e2bc8df0f69a907',
      'b0ccb183494a8e340b8725a2dc553967d61e6dae',
      '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb',
      '87ed20b241576b620bbaa3dfd47715ce6782b7dd',
      '2cebeedfb1e80f4b872d0a13ade529e70652c0c8',
    ]);

    patchNum = 4 as PatchSetNum;
    relatedChanges = [
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '2cebeedfb1e80f4b872d0a13ade529e70652c0c8'
          ),
          parents: [
            {
              commit: '87ed20b241576b620bbaa3dfd47715ce6782b7dd' as CommitId,
              subject: 'My parent commit',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '87ed20b241576b620bbaa3dfd47715ce6782b7dd'
          ),
          parents: [
            {
              commit: '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb' as CommitId,
              subject: 'My parent commit',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '6c71f9e86ba955a7e01e2088bce0050a90eb9fbb'
          ),
          parents: [
            {
              commit: 'b0ccb183494a8e340b8725a2dc553967d61e6dae' as CommitId,
              subject: 'My parent commit',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            'a3e5d9d4902b915a39e2efba5577211b9b3ebe7b'
          ),
          parents: [
            {
              commit: '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6' as CommitId,
              subject: 'My parent commit',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6'
          ),
          parents: [
            {
              commit: 'af815dac54318826b7f1fa468acc76349ffc588e' as CommitId,
              subject: 'My parent commit',
            },
          ],
        },
      },
      {
        ...createRelatedChangeAndCommitInfo(),
        commit: {
          ...createCommitInfoWithRequiredCommit(
            'af815dac54318826b7f1fa468acc76349ffc588e'
          ),
          parents: [
            {
              commit: '58f76e406e24cb8b0f5d64c7f5ac1e8616d0a22c' as CommitId,
              subject: 'My parent commit',
            },
          ],
        },
      },
    ];

    connectedChanges = element._computeConnectedRevisions(
      change,
      patchNum,
      relatedChanges
    );
    assert.deepEqual(connectedChanges, [
      'af815dac54318826b7f1fa468acc76349ffc588e',
      '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6',
      '9e593f6dcc2c0785a2ad2c895a34ad2aa9a0d8b6',
      'a3e5d9d4902b915a39e2efba5577211b9b3ebe7b',
    ]);
  });

  suite('gr-related-changes-list plugin tests', () => {
    let element: GrRelatedChangesList;

    setup(() => {
      resetPlugins();
      element = basicFixture.instantiate();
    });

    teardown(() => {
      resetPlugins();
    });

    test('endpoint params', async () => {
      element.change = {...createParsedChange(), labels: {}};
      interface RelatedChangesListGrEndpointDecorator
        extends GrEndpointDecorator {
        plugin: PluginApi;
        change: ParsedChangeInfo;
      }
      let hookEl: RelatedChangesListGrEndpointDecorator;
      let plugin: PluginApi;
      window.Gerrit.install(
        p => {
          plugin = p;
          plugin
            .hook('related-changes-section')
            .getLastAttached()
            .then(el => (hookEl = el as RelatedChangesListGrEndpointDecorator));
        },
        '0.1',
        'http://some/plugins/url1.js'
      );
      getPluginLoader().loadPlugins([]);
      await flush();
      assert.strictEqual(hookEl!.plugin, plugin!);
      assert.strictEqual(hookEl!.change, element.change);
    });
  });
});
