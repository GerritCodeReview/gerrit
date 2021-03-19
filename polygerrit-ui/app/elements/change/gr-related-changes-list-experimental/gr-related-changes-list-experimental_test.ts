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

import '../../../test/common-test-setup-karma';
import {
  createChange,
  createParsedChange,
  createRelatedChangeAndCommitInfo,
  createRelatedChangesInfo,
  createSubmittedTogetherInfo,
} from '../../../test/test-data-generators';
import {queryAndAssert, stubRestApi} from '../../../test/test-utils';
import {
  PatchSetNum,
  RelatedChangesInfo,
  SubmittedTogetherInfo,
} from '../../../types/common';
import './gr-related-changes-list-experimental';
import {
  ChangeMarkersInList,
  GrRelatedChangesListExperimental,
  GrRelatedCollapse,
  Section,
} from './gr-related-changes-list-experimental';

const basicFixture = fixtureFromElement('gr-related-changes-list-experimental');

suite('gr-related-changes-list-experimental', () => {
  let element: GrRelatedChangesListExperimental;

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
      return instructions
        .map(inst => Array.from({length: inst.len}, () => inst.v))
        .reduce((acc, val) => acc.concat(val), []);
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
      assert.isTrue(relatedChanges!.classList.contains('first'));
    });

    test('first empty second non-empty', async () => {
      stubRestApi('getRelatedChanges').returns(
        Promise.resolve(createRelatedChangesInfo())
      );
      stubRestApi('getChangesSubmittedTogether').returns(
        Promise.resolve(submittedTogether)
      );
      await element.reload();
      const relatedChanges = queryAndAssert<GrRelatedCollapse>(
        queryAndAssert<HTMLElement>(element, '#relatedChanges'),
        'gr-related-collapse'
      );
      assert.isFalse(relatedChanges!.classList.contains('first'));
      const submittedTogetherSection = queryAndAssert<GrRelatedCollapse>(
        queryAndAssert<HTMLElement>(element, '#submittedTogether'),
        'gr-related-collapse'
      );
      assert.isTrue(submittedTogetherSection!.classList.contains('first'));
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
      assert.isTrue(relatedChanges!.classList.contains('first'));
      const submittedTogetherSection = queryAndAssert<GrRelatedCollapse>(
        queryAndAssert<HTMLElement>(element, '#submittedTogether'),
        'gr-related-collapse'
      );
      assert.isFalse(submittedTogetherSection!.classList.contains('first'));
      const cherryPicks = queryAndAssert<GrRelatedCollapse>(
        queryAndAssert<HTMLElement>(element, '#cherryPicks'),
        'gr-related-collapse'
      );
      assert.isFalse(cherryPicks!.classList.contains('first'));
    });
  });
});
