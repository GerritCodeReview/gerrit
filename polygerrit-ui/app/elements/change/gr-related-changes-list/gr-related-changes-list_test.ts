/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, html, assert} from '@open-wc/testing';
import {PluginApi} from '../../../api/plugin';
import '../../../test/common-test-setup';
import {testResolver} from '../../../test/common-test-setup';
import {
  createChange,
  createCommitInfoWithRequiredCommit,
  createParsedChange,
  createRelatedChangeAndCommitInfo,
  createRelatedChangesInfo,
  createRevision,
  createSubmittedTogetherInfo,
} from '../../../test/test-data-generators';
import {query, queryAndAssert, waitEventLoop} from '../../../test/test-utils';
import {
  ChangeId,
  ChangeInfo,
  CommitId,
  NumericChangeId,
  PatchSetNumber,
  RelatedChangeAndCommitInfo,
  RelatedChangesInfo,
  SubmittedTogetherInfo,
} from '../../../types/common';
import {ParsedChangeInfo} from '../../../types/types';
import {getChangeNumber} from '../../../utils/change-util';
import {GrEndpointDecorator} from '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import './gr-related-changes-list';
import {
  ChangeMarkersInList,
  GrRelatedChangesList,
  Section,
} from './gr-related-changes-list';
import {GrRelatedCollapse} from './gr-related-collapse';

suite('gr-related-changes-list', () => {
  let element: GrRelatedChangesList;

  setup(async () => {
    element = await fixture(
      html`<gr-related-changes-list></gr-related-changes-list>`
    );
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
      element.latestPatchNum = 1 as PatchSetNumber;
    });

    test('render', async () => {
      element.relatedChanges = relatedChangeInfo.changes;
      element.submittedTogether = submittedTogether;
      element.cherryPickChanges = [createChange()];
      await element.updateComplete;

      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <gr-endpoint-decorator name="related-changes-section">
            <gr-endpoint-param name="change"> </gr-endpoint-param>
            <gr-endpoint-slot name="top"> </gr-endpoint-slot>
            <section id="relatedChanges">
              <gr-related-collapse class="first" title="Relation chain">
                <div class="relatedChangeLine show-when-collapsed">
                  <span class="marker space"> </span>
                  <gr-related-change
                    show-change-status=""
                    show-submittable-check=""
                  >
                    Test commit subject
                  </gr-related-change>
                </div>
              </gr-related-collapse>
            </section>
            <section id="submittedTogether">
              <gr-related-collapse title="Submitted together">
                <div class="relatedChangeLine show-when-collapsed">
                  <span
                    aria-label="Arrow marking current change"
                    class="arrowToCurrentChange marker"
                    role="img"
                  >
                    ➔
                  </span>
                  <gr-related-change show-submittable-check="">
                    Test subject
                  </gr-related-change>
                  <span class="repo" title="test-project">test-project</span>
                  <span class="branch">&nbsp;|&nbsp;test-branch&nbsp;</span>
                </div>
              </gr-related-collapse>
              <div class="note" hidden="">(+ )</div>
            </section>
            <section id="cherryPicks">
              <gr-related-collapse title="Cherry picks">
                <div class="relatedChangeLine show-when-collapsed">
                  <span class="marker space"> </span>
                  <gr-related-change show-change-status="">
                    test-branch: Test subject
                  </gr-related-change>
                </div>
              </gr-related-collapse>
            </section>
            <gr-endpoint-slot name="bottom"> </gr-endpoint-slot>
          </gr-endpoint-decorator>
        `
      );
    });

    test('first list', async () => {
      element.relatedChanges = relatedChangeInfo.changes;
      await element.updateComplete;

      const section = queryAndAssert<HTMLElement>(element, '#relatedChanges');
      const relatedChanges = queryAndAssert<GrRelatedCollapse>(
        section,
        'gr-related-collapse'
      );
      assert.isTrue(relatedChanges.classList.contains('first'));
    });

    test('first empty second non-empty', async () => {
      element.relatedChanges = createRelatedChangesInfo().changes;
      element.submittedTogether = submittedTogether;
      await element.updateComplete;

      const relatedChanges = query<HTMLElement>(element, '#relatedChanges');
      assert.notExists(relatedChanges);
      const submittedTogetherSection = queryAndAssert<GrRelatedCollapse>(
        queryAndAssert<HTMLElement>(element, '#submittedTogether'),
        'gr-related-collapse'
      );
      assert.isTrue(submittedTogetherSection.classList.contains('first'));
    });

    test('first non-empty second empty third non-empty', async () => {
      element.relatedChanges = relatedChangeInfo.changes;
      element.submittedTogether = createSubmittedTogetherInfo();
      element.cherryPickChanges = [createChange()];
      await element.updateComplete;

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
    assert.equal(getChangeNumber(change1), 0);
    assert.equal(getChangeNumber(change2), 1);
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
    let latestPatchNum = 7 as PatchSetNumber;
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
      latestPatchNum,
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

    latestPatchNum = 4 as PatchSetNumber;
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
      latestPatchNum,
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

    setup(async () => {
      element = await fixture(
        html`<gr-related-changes-list></gr-related-changes-list>`
      );
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
      testResolver(pluginLoaderToken).loadPlugins([]);
      await waitEventLoop();
      assert.strictEqual(hookEl!.plugin, plugin!);
      assert.strictEqual(hookEl!.change, element.change);
    });
  });
});
