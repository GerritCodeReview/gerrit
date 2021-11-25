export * from 'lit';

export function observe(...propertyNames: string[]) {
  return (proto: any, method: string) => {
    const clazz = proto.constructor as DesignerElementConstructor;
    if (!clazz.hasOwnProperty('_observers')) {
      Object.defineProperty(clazz, '_observers', {value: new Map()});
    }

    for (const property of propertyNames) {
      let observers = clazz._observers.get(property);
      observers ?? clazz._observers.set(property, (observers = []));
      observers.push(clazz.prototype[method]);
    }
  };
}