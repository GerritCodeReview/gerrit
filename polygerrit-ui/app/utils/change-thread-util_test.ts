/**
 * @license
 * Copyright 2026 Monty Taylor
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {assert} from '@open-wc/testing';
import {
  threadChangeList,
  threadChanges,
  ThreadNode,
  threadRelatedChanges,
} from './change-thread-util';
import {
  createChange,
  createRelatedChangeAndCommitInfo,
} from '../test/test-data-generators';
import {CommitId, NumericChangeId} from '../types/common';
import {ChangeStatus} from '../constants/constants';

suite('change-thread-util', () => {
  const node = (id: string, parents: string[] = []): ThreadNode<string> => {
    return {id, value: id, parents};
  };

  test('disabled threading preserves native result order without rails', () => {
    const base = createChange({_number: 1 as NumericChangeId});
    const child = createChange({
      _number: 2 as NumericChangeId,
      current_revision: 'child' as CommitId,
      indexed_dependencies: {
        revision: 'child' as CommitId,
        patch_set_number: 1,
        parents: [
          {
            change_number: base._number,
            patch_set_number: 1,
            commit_id: 'old-base' as CommitId,
          },
        ],
      },
    });
    const rows = threadChangeList([child, base], false);
    assert.deepEqual(
      rows.map(row => row.node.value._number),
      [2, 1]
    );
    assert.isTrue(rows.every(row => row.branches.length === 0));
  });

  test('keeps flat results in their original order without mutating them', () => {
    const nodes = [node('b'), node('a'), node('c')];
    assert.deepEqual(
      threadChanges(nodes).map(row => row.node.id),
      ['b', 'a', 'c']
    );
    assert.deepEqual(
      nodes.map(n => n.id),
      ['b', 'a', 'c']
    );
  });

  test('moves a stack to its earliest result and keeps branches together', () => {
    const rows = threadChanges([
      node('tip', ['left']),
      node('unrelated'),
      node('right', ['base']),
      node('base'),
      node('left', ['base']),
    ]);
    assert.deepEqual(
      rows.map(row => row.node.id),
      ['base', 'left', 'tip', 'right', 'unrelated']
    );
    assert.deepEqual(
      rows.map(row => row.branches),
      [[], [false], [false, true], [false], []]
    );
    assert.deepEqual(
      rows.map(row => row.last),
      [true, false, true, true, true]
    );
  });

  test('does not invent rows for missing dependencies', () => {
    const rows = threadChanges([node('child', ['missing']), node('other')]);
    assert.lengthOf(rows, 2);
    assert.deepEqual(rows[0].branches, []);
  });

  test('renders diamond dependencies once and retains both edges', () => {
    const rows = threadChanges([
      node('merge', ['left', 'right']),
      node('left', ['base']),
      node('right', ['base']),
      node('base'),
    ]);
    assert.deepEqual(
      rows.map(row => row.node.id),
      ['base', 'left', 'merge', 'right']
    );
    assert.deepEqual(rows[2].parents, ['left', 'right']);
  });

  test('breaks cycles only for display, including self dependencies', () => {
    const rows = threadChanges([
      node('a', ['b']),
      node('b', ['a']),
      node('self', ['self']),
    ]);
    assert.deepEqual(
      rows.map(row => row.node.id),
      ['b', 'a', 'self']
    );
    assert.deepEqual(rows.find(row => row.node.id === 'b')!.parents, ['a']);
    assert.deepEqual(rows[2].parents, ['self']);
  });

  test('merged rows are boundaries while abandoned and WIP rows still thread', () => {
    const change = (number: number, parent?: number) =>
      createChange({
        _number: number as NumericChangeId,
        current_revision: `revision-${number}` as CommitId,
        indexed_dependencies: {
          revision: `revision-${number}` as CommitId,
          patch_set_number: 1,
          parents:
            parent === undefined
              ? []
              : [
                  {
                    change_number: parent as NumericChangeId,
                    patch_set_number: 1,
                    commit_id: `revision-${parent}` as CommitId,
                  },
                ],
        },
      });
    const merged = {...change(1, 2), status: ChangeStatus.MERGED};
    const abandoned = {...change(2, 1), status: ChangeStatus.ABANDONED};
    const wip = {...change(3, 2), work_in_progress: true};
    const rows = threadChangeList([wip, abandoned, merged]);
    assert.deepEqual(
      rows.map(row => row.node.value._number),
      [2, 3, 1]
    );
    assert.deepEqual(
      rows.map(row => row.parents.length),
      [0, 1, 0]
    );
    assert.deepEqual(
      rows.map(row => row.branches.length),
      [0, 1, 0]
    );
  });

  test('missing or stale indexed metadata leaves older-server rows flat', () => {
    const base = createChange({_number: 1 as NumericChangeId});
    const child = createChange({
      _number: 2 as NumericChangeId,
      current_revision: 'new' as CommitId,
    });
    for (const indexed_dependencies of [
      undefined,
      {
        revision: 'old' as CommitId,
        patch_set_number: 1,
        parents: [
          {
            change_number: base._number,
            patch_set_number: 1,
            commit_id: 'base' as CommitId,
          },
        ],
      },
    ]) {
      const rows = threadChangeList([{...child, indexed_dependencies}, base]);
      assert.deepEqual(
        rows.map(row => row.node.value._number),
        [2, 1]
      );
      assert.isTrue(rows.every(row => row.branches.length === 0));
    }
  });

  test('uses parent change identities even when the parent patchset is old', () => {
    const base = createChange({_number: 1 as NumericChangeId});
    const child = createChange({
      _number: 2 as NumericChangeId,
      current_revision: 'child' as CommitId,
      indexed_dependencies: {
        revision: 'child' as CommitId,
        patch_set_number: 1,
        parents: [
          {
            change_number: base._number,
            patch_set_number: 1,
            commit_id: 'old-base' as CommitId,
          },
        ],
      },
    });
    const rows = threadChangeList([child, base]);
    assert.deepEqual(
      rows.map(row => row.node.value._number),
      [1, 2]
    );
    assert.lengthOf(rows[1].branches, 1);
  });

  test('merged relation-chain rows remain standalone boundaries', () => {
    const merged = {
      ...createRelatedChangeAndCommitInfo(),
      status: ChangeStatus.MERGED,
    };
    const child = {
      ...createRelatedChangeAndCommitInfo(),
      commit: {
        ...merged.commit,
        commit: 'child' as CommitId,
        parents: [
          {commit: merged.commit.commit, subject: merged.commit.subject},
        ],
      },
    };
    const rows = threadRelatedChanges([child, merged]);
    assert.strictEqual(rows[0].node.value, child);
    assert.isTrue(
      rows.every(row => row.parents.length === 0 && row.branches.length === 0)
    );
  });

  test('threads related changes by the actual patchset commit', () => {
    const base = createRelatedChangeAndCommitInfo();
    const child = {
      ...createRelatedChangeAndCommitInfo(),
      commit: {
        ...base.commit,
        commit: 'child' as CommitId,
        parents: [{commit: base.commit.commit, subject: base.commit.subject}],
      },
    };
    const rows = threadRelatedChanges([child, base]);
    assert.strictEqual(rows[0].node.value, base);
    assert.strictEqual(rows[1].node.value, child);
    assert.lengthOf(rows[1].branches, 1);
  });
});
