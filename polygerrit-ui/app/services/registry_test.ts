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

import {create, Finalizable, Registry} from './registry';
import '../test/common-test-setup-karma.js';

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
