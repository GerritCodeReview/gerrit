export function unexpetedValue<T>(x: T): never {
  throw new Error(`Unexpected value '${x}'`);
}
