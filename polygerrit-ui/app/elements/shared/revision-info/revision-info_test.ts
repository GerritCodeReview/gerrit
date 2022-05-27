/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {
  createChange,
  createCommit,
  createRevision,
} from '../../../test/test-data-generators';
import {ChangeInfo, CommitId, PatchSetNum} from '../../../types/common';
import './revision-info';
import {RevisionInfo} from './revision-info';

suite('revision-info tests', () => {
  let mockChange: ChangeInfo;

  setup(() => {
    mockChange = {
      ...createChange(),
      revisions: {
        r1: {
          ...createRevision(),
          _number: 1 as PatchSetNum,
          commit: {
            ...createCommit(),
            parents: [
              {commit: 'p1' as CommitId, subject: ''},
              {commit: 'p2' as CommitId, subject: ''},
              {commit: 'p3' as CommitId, subject: ''},
            ],
          },
        },
        r2: {
          ...createRevision(),
          _number: 2 as PatchSetNum,
          commit: {
            ...createCommit(),
            parents: [
              {commit: 'p1' as CommitId, subject: ''},
              {commit: 'p4' as CommitId, subject: ''},
            ],
          },
        },
        r3: {
          ...createRevision(),
          _number: 3 as PatchSetNum,
          commit: {
            ...createCommit(),
            parents: [{commit: 'p5' as CommitId, subject: ''}],
          },
        },
        r4: {
          ...createRevision(),
          _number: 4 as PatchSetNum,
          commit: {
            ...createCommit(),
            parents: [
              {commit: 'p2' as CommitId, subject: ''},
              {commit: 'p3' as CommitId, subject: ''},
            ],
          },
        },
        r5: {
          ...createRevision(),
          _number: 5 as PatchSetNum,
          commit: {
            ...createCommit(),
            parents: [
              {commit: 'p5' as CommitId, subject: ''},
              {commit: 'p2' as CommitId, subject: ''},
              {commit: 'p3' as CommitId, subject: ''},
            ],
          },
        },
      },
    };
  });

  test('getMaxParents', () => {
    const ri = new RevisionInfo(mockChange);
    assert.equal(ri.getMaxParents(), 3);
  });

  test('getParentCountMap', () => {
    const ri = new RevisionInfo(mockChange);
    assert.deepEqual(ri.getParentCountMap(), {1: 3, 2: 2, 3: 1, 4: 2, 5: 3});
  });

  test('getParentCount', () => {
    const ri = new RevisionInfo(mockChange);
    assert.deepEqual(ri.getParentCount(1 as PatchSetNum), 3);
    assert.deepEqual(ri.getParentCount(3 as PatchSetNum), 1);
  });

  test('getParentCount', () => {
    const ri = new RevisionInfo(mockChange);
    assert.deepEqual(ri.getParentCount(1 as PatchSetNum), 3);
    assert.deepEqual(ri.getParentCount(3 as PatchSetNum), 1);
  });

  test('getParentId', () => {
    const ri = new RevisionInfo(mockChange);
    assert.deepEqual(ri.getParentId(1 as PatchSetNum, 2), 'p3' as CommitId);
    assert.deepEqual(ri.getParentId(2 as PatchSetNum, 1), 'p4' as CommitId);
    assert.deepEqual(ri.getParentId(3 as PatchSetNum, 0), 'p5' as CommitId);
  });
});
