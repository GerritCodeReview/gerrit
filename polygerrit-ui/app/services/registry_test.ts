import {create, Finalizable, Registry} from './registry';
import '../test/common-test-setup-karma.js';

class Foo implements Finalizable {
  constructor(private readonly final: Array<string>) {}

  finalize() {
    this.final.push('Foo');
  }
}

class Bar implements Finalizable {
  constructor(private readonly final: Array<string>, _foo?: Foo) {}

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
    const final: Array<string> = [];
    const demoRegistry: Registry<DemoContext> = {
      foo: (_ctx: Partial<DemoContext>) => new Foo(final),
      bar: (ctx: Partial<DemoContext>) => new Bar(final, ctx.foo),
    };
    const demoContext: DemoContext & Finalizable = create<DemoContext>(
      demoRegistry
    ) as DemoContext & Finalizable;
    demoContext.finalize();
    assert.deepEqual(final, ['Foo', 'Bar']);
  });
});
