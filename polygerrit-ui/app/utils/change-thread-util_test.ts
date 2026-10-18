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
  createRevision,
} from '../test/test-data-generators';
import {CommitId, NumericChangeId} from '../types/common';

suite('change-thread-util', () => {
  const node = (
    id: string,
    parents: string[] = [],
    topic?: string
  ): ThreadNode<string> => {
    return {id, value: id, parents, topic};
  };

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

  test('uses parent change identities even when the parent patchset is old', () => {
    const base = createChange({_number: 1 as NumericChangeId});
    const child = createChange({
      _number: 2 as NumericChangeId,
      current_revision: 'child' as CommitId,
      revisions: {
        child: {
          ...createRevision(),
          parents_data: [{change_number: base._number}],
        },
      },
    });
    const rows = threadChangeList([child, base]);
    assert.deepEqual(
      rows.map(row => row.node.value._number),
      [1, 2]
    );
    assert.lengthOf(rows[1].branches, 1);
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
