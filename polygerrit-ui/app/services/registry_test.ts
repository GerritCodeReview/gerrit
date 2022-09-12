/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {create, Finalizable, Registry} from './registry';
import '../test/common-test-setup';
import {assert} from '@open-wc/testing';

class Foo implements Finalizable {
  constructor(private readonly final: string[]) {}

  finalize() {
    this.final.push('Foo');
  }
}

class Bar implements Finalizable {
  constructor(private readonly final: string[], _foo?: Foo) {}

  finalize() {
    this.final.push('Bar');
  }
}

interface DemoContext {
  foo: Foo;
  bar: Bar;
}

suite('Registry', () => {
  setup(() => {});

  test('It finalizes correctly', () => {
    const final: string[] = [];
    const demoRegistry: Registry<DemoContext> = {
      foo: (_ctx: Partial<DemoContext>) => new Foo(final),
      bar: (ctx: Partial<DemoContext>) => new Bar(final, ctx.foo),
    };
    const demoContext: DemoContext & Finalizable =
      create<DemoContext>(demoRegistry);
    demoContext.finalize();
    assert.deepEqual(final, ['Foo', 'Bar']);
  });
});
