## What is services folder

'services' folder contains services used across multiple components.

## What should be considered as services in Gerrit UI

Services should be stateful, if its just pure functions, consider having them in 'utils' instead.

Regarding all stateful should be considered as services or not, it's still TBD. Will update as soon
as it's finalized.

## How to access service

We use AppContext to access instance of service. It helps in mocking service in tests as well.

```
import {appContext} from '../../../services/app-context.js';

class T {
  constructor() {
    super();
    this.flagsService = appContext.flagsService;
  }

  action1() {
    if (this.flagsService.isEnabled('test)) {
       // do something
    }
  }
}
```

## What services we have

### Flags

'flags' is a service to provide easy access to all enabled experiments.

```
import {appContext} from '../../../services/app-context.js';

// check if an experiment is enabled or not
if (appContext.flagsService.isEnabled('test')) {
  // do something
}
```