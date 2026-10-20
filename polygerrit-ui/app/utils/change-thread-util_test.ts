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
  const node = (
    id: string,
    parents: string[] = [],
    topic?: string
  ): ThreadNode<string> => {
    return {id, value: id, parents, topic};
  };

  test('topic grouping and dependency threading support all four combinations', () => {
    const base = createChange({
      _number: 1 as NumericChangeId,
      topic: 'bundle' as ReturnType<typeof createChange>['topic'],
    });
    const mate = createChange({
      _number: 2 as NumericChangeId,
      topic: base.topic,
    });
    const child = createChange({_number: 3 as NumericChangeId});
    const edges = [{from: 3, to: 1, type: 'git'}] as const;
    for (const threaded of [false, true]) {
      for (const groupTopics of [false, true]) {
        const rows = threadChangeList(
          [mate, child, base],
          [...edges],
          threaded,
          groupTopics
        );
        assert.deepEqual(
          rows.map(row => row.node.value._number),
          threaded || groupTopics ? [2, 1, 3] : [2, 3, 1]
        );
        assert.equal(
          rows.some(row => row.branches.length > 0),
          threaded
        );
        assert.equal(
          rows.some(row => row.topicStart),
          groupTopics
        );
        assert.equal(
          rows.filter(row => row.node.topic).length,
          groupTopics ? 2 : 0
        );
      }
    }
    const merged = {
      ...base,
      _number: 4 as NumericChangeId,
      status: ChangeStatus.MERGED,
    };
    const rows = threadChangeList([mate, merged, base], [], false, true);
    assert.isUndefined(
      rows.find(row => row.node.value._number === 4)!.node.topic
    );
    assert.deepEqual(
      rows.map(row => row.node.value._number),
      [2, 1, 4]
    );
  });

  test('flat preference keeps result order and removes topic groups', () => {
    const changes = [2, 1].map(number =>
      createChange({
        _number: number as NumericChangeId,
        topic: 'bundle' as ReturnType<typeof createChange>['topic'],
      })
    );
    const rows = threadChangeList(
      changes,
      [{from: 2, to: 1, type: 'depends-on'}],
      false
    );
    assert.deepEqual(
      rows.map(row => Number(row.node.value._number)),
      [2, 1]
    );
    assert.isTrue(
      rows.every(row => row.branches.length === 0 && !row.node.topic)
    );
  });

  test('provider edges never attach to historical related patchsets', () => {
    const base = {
      ...createRelatedChangeAndCommitInfo(),
      _change_number: 1 as NumericChangeId,
      _revision_number: 2,
      _current_revision_number: 2,
    };
    const historical = {
      ...createRelatedChangeAndCommitInfo(),
      _change_number: 2 as NumericChangeId,
      _revision_number: 1,
      _current_revision_number: 2,
      commit: {...base.commit, commit: 'old' as CommitId, parents: []},
    };
    const rows = threadRelatedChanges(
      [historical, base],
      [{from: 2, to: 1, type: 'depends-on'}]
    );
    assert.strictEqual(rows[0].node.value, historical);
    assert.lengthOf(rows[0].branches, 0);
    assert.lengthOf(rows[0].parents, 0);
  });

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
    const rows = threadChangeList([child, base], [], false);
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

  test('groups topics among siblings without inventing dependency edges', () => {
    const rows = threadChanges([
      node('a', [], 'bundle'),
      node('other'),
      node('b', [], 'bundle'),
    ]);
    assert.deepEqual(
      rows.map(row => row.node.id),
      ['a', 'b', 'other']
    );
    assert.isTrue(rows[0].topicStart);
    assert.isFalse(rows[0].topicEnd);
    assert.isFalse(rows[1].topicStart);
    assert.isTrue(rows[1].topicEnd);
    assert.deepEqual(rows[1].branches, []);
  });

  test('topic boundaries do not override ancestry', () => {
    const rows = threadChanges([
      node('child', ['base'], 'bundle'),
      node('base', [], 'other'),
    ]);
    assert.deepEqual(
      rows.map(row => row.node.id),
      ['base', 'child']
    );
    assert.isTrue(rows[0].topicEnd);
    assert.isTrue(rows[1].topicStart);
  });

  test('a topic stack shares one depth and keeps its real dependencies', () => {
    const rows = threadChanges([
      node('tip', ['base'], 'bundle'),
      node('dependent', ['tip']),
      node('unrelated'),
      node('base', ['outside'], 'bundle'),
      node('outside'),
    ]);
    assert.deepEqual(
      rows.map(row => row.node.id),
      ['outside', 'base', 'tip', 'dependent', 'unrelated']
    );
    assert.deepEqual(
      rows.map(row => row.branches.length),
      [0, 1, 1, 2, 0]
    );
    assert.deepEqual(
      rows.map(row => row.last),
      [true, false, true, true, true]
    );
    assert.deepEqual(rows[2].parents, ['base']);
    assert.isTrue(rows[1].topicStart);
    assert.isFalse(rows[1].topicEnd);
    assert.isFalse(rows[2].topicStart);
    assert.isTrue(rows[2].topicEnd);
  });

  test('groups a topic across separate branches without losing external parents', () => {
    const rows = threadChanges([
      node('a', ['left'], 'bundle'),
      node('b', ['right'], 'bundle'),
      node('left'),
      node('right'),
    ]);
    assert.deepEqual(
      rows.map(row => row.node.id),
      ['left', 'a', 'b', 'right']
    );
    assert.deepEqual(rows[1].branches, rows[2].branches);
    assert.deepEqual(rows[2].parents, ['right']);
  });

  test('topic grouping breaks display cycles without dropping changes or edges', () => {
    const rows = threadChanges([
      node('a', [], 'bundle'),
      node('middle', ['a']),
      node('b', ['middle'], 'bundle'),
    ]);
    assert.sameMembers(
      rows.map(row => row.node.id),
      ['a', 'b', 'middle']
    );
    assert.equal(
      rows.find(row => row.node.id === 'a')!.branches.length,
      rows.find(row => row.node.id === 'b')!.branches.length
    );
    assert.deepEqual(rows.find(row => row.node.id === 'b')!.parents, [
      'middle',
    ]);
    assert.deepEqual(rows.find(row => row.node.id === 'middle')!.parents, [
      'a',
    ]);
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

  test('provider edges cannot connect through merged list rows', () => {
    const changes = [1, 2, 3].map(number =>
      createChange({_number: number as NumericChangeId})
    );
    changes[0].status = ChangeStatus.MERGED;
    changes[1].status = ChangeStatus.ABANDONED;
    const rows = threadChangeList(changes, [
      {from: 1, to: 2, type: 'depends-on'},
      {from: 3, to: 1, type: 'git'},
      {from: 3, to: 2, type: 'depends-on'},
    ]);
    const merged = rows.find(row => row.node.value._number === 1)!;
    const child = rows.find(row => row.node.value._number === 3)!;
    assert.isEmpty(merged.parents);
    assert.isEmpty(merged.branches);
    assert.lengthOf(child.parents, 1);
    assert.include(child.parents[0], '~2');
  });

  test('accepts cross-project typed edges and prefers Git over Depends-On', () => {
    const changes = [1, 2, 3].map(number =>
      createChange({_number: number as NumericChangeId})
    );
    const rows = threadChangeList(changes, [
      {from: 1, to: 2, type: 'depends-on'},
      {from: 1, to: 3, type: 'git'},
    ]);
    assert.deepEqual(
      rows.map(row => row.node.value._number),
      [3, 1, 2]
    );
    assert.lengthOf(rows[1].parents, 2);
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
