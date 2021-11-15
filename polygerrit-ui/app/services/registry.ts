
export type Factory<Context, K extends keyof Context> = (ctx: Partial<Context>) => Context[K];
export type Registry<Context> = {[P in keyof Context]: Factory<Context, P>};

export function create<Context>(registry: Registry<Context>): Partial<Context> {
  const context: Partial<Context> = {} as Partial<Context>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const initialized: Map<keyof Context, any> = new Map<keyof Context, any>();
  const properties = Object.getOwnPropertyNames(registry).reduce(
    (properties, key) => {
      const name = key as (keyof Context & string);
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
