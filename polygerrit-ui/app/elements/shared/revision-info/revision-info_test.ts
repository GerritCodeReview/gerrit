/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {
  createChange,
  createCommit,
  createRevision,
} from '../../../test/test-data-generators';
import {ChangeInfo, CommitId, PatchSetNumber} from '../../../types/common';
import './revision-info';
import {RevisionInfo} from './revision-info';

suite('revision-info tests', () => {
  let mockChange: ChangeInfo;

  setup(() => {
    mockChange = {
      ...createChange(),
      revisions: {
        r1: {
          ...createRevision(1),
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
          ...createRevision(2),
          commit: {
            ...createCommit(),
            parents: [
              {commit: 'p1' as CommitId, subject: ''},
              {commit: 'p4' as CommitId, subject: ''},
            ],
          },
        },
        r3: {
          ...createRevision(3),
          commit: {
            ...createCommit(),
            parents: [{commit: 'p5' as CommitId, subject: ''}],
          },
        },
        r4: {
          ...createRevision(4),
          commit: {
            ...createCommit(),
            parents: [
              {commit: 'p2' as CommitId, subject: ''},
              {commit: 'p3' as CommitId, subject: ''},
            ],
          },
        },
        r5: {
          ...createRevision(5),
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
    assert.deepEqual(
      ri.getParentCountMap(),
      new Map([
        [1 as PatchSetNumber, 3],
        [2 as PatchSetNumber, 2],
        [3 as PatchSetNumber, 1],
        [4 as PatchSetNumber, 2],
        [5 as PatchSetNumber, 3],
      ])
    );
  });

  test('getParentCount', () => {
    const ri = new RevisionInfo(mockChange);
    assert.deepEqual(ri.getParentCount(1 as PatchSetNumber), 3);
    assert.deepEqual(ri.getParentCount(3 as PatchSetNumber), 1);
  });

  test('getParentCount', () => {
    const ri = new RevisionInfo(mockChange);
    assert.deepEqual(ri.getParentCount(1 as PatchSetNumber), 3);
    assert.deepEqual(ri.getParentCount(3 as PatchSetNumber), 1);
  });

  test('getParentId', () => {
    const ri = new RevisionInfo(mockChange);
    assert.deepEqual(ri.getParentId(1 as PatchSetNumber, 2), 'p3' as CommitId);
    assert.deepEqual(ri.getParentId(2 as PatchSetNumber, 1), 'p4' as CommitId);
    assert.deepEqual(ri.getParentId(3 as PatchSetNumber, 0), 'p5' as CommitId);
  });
});
