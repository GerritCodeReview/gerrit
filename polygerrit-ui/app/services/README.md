## What is services folder

'services' folder contains services used across multiple components.

## What should be considered as services in Gerrit UI

Services should be stateful, if its just pure functions, consider having them in 'utils' instead.

Regarding all stateful should be considered as services or not, it's still TBD. Will update as soon
as it's finalized.

## How to access a service

We use AppContext to access instance of service. It helps in mocking service in tests as well.
We prefer setting instance of service in constructor and then accessing it from variable. We also
allow access straight from getAppContext() especially in static methods.

```
import {getAppContext()} from '../../../services/app-context.js';

class T {
  private readonly flagsService = getAppContext().flagsService;

  action1() {
    if (this.flagsService.isEnabled('test)) {
      // do something
    }
  }
}

staticMethod() {
  if (appContext.flagsService.isEnabled('test)) {
    // do something
  }
}
```

## What services we have

### Flags

'flags' is a service to provide easy access to all enabled experiments.

```
import {getAppContext} from '../../../services/app-context.js';

// check if an experiment is enabled or not
if (getAppContext().flagsService.isEnabled('test')) {
  // do something
}
```
