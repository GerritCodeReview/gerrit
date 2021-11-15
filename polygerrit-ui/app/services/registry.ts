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
export type Factory<Context, K extends keyof Context> = (
  ctx: Partial<Context>
) => Context[K];
export type Registry<Context> = {[P in keyof Context]: Factory<Context, P>};

export function create<Context>(
  registry: Registry<Context>,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  cache?: Map<keyof Context, any>
): Partial<Context> {
  const context: Partial<Context> = {} as Partial<Context>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const initialized: Map<keyof Context, any> = cache
    ? cache
    : // eslint-disable-next-line @typescript-eslint/no-explicit-any
      new Map<keyof Context, any>();
  const properties = Object.getOwnPropertyNames(registry).reduce(
    (properties, key) => {
      const name = key as keyof Context & string;
      const factory = registry[name];
      let initializing = false;
      properties[name] = {
        configurable: true, // Tests can mock properties
        get() {
          if (!initialized.has(name)) {
            if (initializing) throw new Error(`Circular dependency for ${key}`);
            initializing = true;
            initialized.set(name, factory(context));
            initializing = false;
          }
          return initialized.get(name);
        },
      };
      return properties;
    },
    {} as PropertyDescriptorMap
  );
  Object.defineProperties(context, properties);
  return context;
}
