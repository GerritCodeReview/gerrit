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

// A factory can take a partially created TContext and generate a property
// for a given key on that TContext.
export type Factory<TContext, K extends keyof TContext> = (
  ctx: Partial<TContext>
) => TContext[K];

// A registry contains a factory for each key in TContext.
export type Registry<TContext> = {[P in keyof TContext]: Factory<TContext, P>};

// Creates a context given a registry.
// The cache parameter is a stop-gap solution because currently services do not
// clean up after themselves. By passing in a cache, we can ensure that in
// tests services are only created once.
export function create<TContext>(
  registry: Registry<TContext>,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  cache?: Map<keyof TContext, any>
): TContext {
  const context: Partial<TContext> = {} as Partial<TContext>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const initialized: Map<keyof TContext, any> = cache
    ? cache
    : // eslint-disable-next-line @typescript-eslint/no-explicit-any
      new Map<keyof TContext, any>();
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
            console.info(`Initializing ${name}`);
            initialized.set(name, factory(context));
          } catch (e) {
            console.error(`Failed to initialize ${name}`, e);
          } finally {
            initializing = false;
          }
        }
        return initialized.get(name);
      },
    });
  }
  return context as TContext;
}
