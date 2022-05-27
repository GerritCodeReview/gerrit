/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// A finalizable object has a single method `finalize` that is called when
// the object is no longer needed and should clean itself up.
export interface Finalizable {
  finalize(): void;
}

// A factory can take a partially created TContext and generate a property
// for a given key on that TContext.
export type Factory<TContext, K extends keyof TContext> = (
  ctx: Partial<TContext>
) => TContext[K] & Finalizable;

// A registry contains a factory for each key in TContext.
export type Registry<TContext> = {[P in keyof TContext]: Factory<TContext, P>} &
  Record<string, (_: TContext) => Finalizable>;

// Creates a context given a registry.
export function create<TContext>(
  registry: Registry<TContext>
): TContext & Finalizable {
  const context: Partial<TContext> & Finalizable = {
    finalize() {
      for (const key of Object.getOwnPropertyNames(registry)) {
        const name = key as keyof TContext;
        try {
          if (this[name]) {
            (this[name] as unknown as Finalizable).finalize();
          }
        } catch (e) {
          console.info(`Failed to finalize ${String(name)}`);
          throw e;
        }
      }
    },
  } as Partial<TContext> & Finalizable;

  const initialized: Map<keyof TContext, Finalizable> = new Map<
    keyof TContext,
    Finalizable
  >();
  for (const key of Object.keys(registry)) {
    const name = key as keyof TContext;
    const factory = registry[name];
    let initializing = false;
    Object.defineProperty(context, name, {
      configurable: true, // Tests can mock properties
      get() {
        if (!initialized.has(name)) {
          // Notice that this is the getter for the property in question.
          // It is possible that during the initialization of one property,
          // another property is required. This extra check ensures that
          // the construction of propertiers on Context are not circularly
          // dependent.
          if (initializing) throw new Error(`Circular dependency for ${key}`);
          try {
            initializing = true;
            initialized.set(name, factory(context));
          } catch (e) {
            console.error(`Failed to initialize ${String(name)}`, e);
          } finally {
            initializing = false;
          }
        }
        return initialized.get(name);
      },
    });
  }
  return context as TContext & Finalizable;
}
