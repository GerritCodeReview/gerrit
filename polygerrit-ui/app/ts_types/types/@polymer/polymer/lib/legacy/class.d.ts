
declare module '@polymer/polymer/lib/legacy/class' {
  export function mixinBehaviors<T>(
      behaviors: object | object[], klass: { new(): T }): { new(): T };
}
