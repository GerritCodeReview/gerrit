const fs = require('fs');
const twinkie = require('fried-twinkie');

fs.readdir('./polygerrit-ui/temp/behaviors/', (err, data) => {
  if (err) {
    console.log('error /polygerrit-ui/temp/behaviors/ directory');
  }
  const behaviors = data;
  const additionalSources = [];
  const externMap = {};

  for (const behavior of behaviors) {
    if (!externMap[behavior]) {
      additionalSources.push({
        path: `./polygerrit-ui/temp/behaviors/${behavior}`,
        src: fs.readFileSync(
            `./polygerrit-ui/temp/behaviors/${behavior}`, 'utf-8'),
      });
      externMap[behavior] = true;
    }
  }

  let mappings = JSON.parse(fs.readFileSync(
      `./polygerrit-ui/temp/map.json`, 'utf-8'));

  // The directory is passed as arg2 by the test target.
  const directory = process.argv[2];
  if (directory) {
    const mappingSpecificDirectory = {};

    for (key of Object.keys(mappings)) {
      if (directory === mappings[key].directory) {
        mappingSpecificDirectory[key] = mappings[key];
      }
    }
    mappings = mappingSpecificDirectory;
  }

  // If a particular file was passed by the user, don't test everything.
  const file = process.argv[3];
  if (file) {
    const mappingSpecificFile = {};
    for (key of Object.keys(mappings)) {
      if (key.includes(file)) {
        mappingSpecificFile[key] = mappings[key];
      }
    }
    mappings = mappingSpecificFile;
  }

  /**
   * Types in Gerrit.
   * All types should be under `./polygerrit-ui/app/types` folder and end with `js`.
   */
  fs.readdir('./polygerrit-ui/app/types/', (err, typeFiles) => {
    for (const typeFile of typeFiles) {
      if (!typeFile.endsWith('.js')) continue;
      additionalSources.push({
        path: `./polygerrit-ui/app/types/${typeFile}`,
        src: fs.readFileSync(
            `./polygerrit-ui/app/types/${typeFile}`, 'utf-8'),
      });
    }

    const toCheck = [];
    for (key of Object.keys(mappings)) {
      if (mappings[key].html && mappings[key].js) {
        toCheck.push({
          htmlSrcPath: mappings[key].html,
          jsSrcPath: mappings[key].js,
          jsModule: 'polygerrit.' + mappings[key].package,
        });
      }
    }

    twinkie.checkTemplate(toCheck, additionalSources)
        .then(() => {}, joinedErrors => {
          if (joinedErrors) {
            process.exit(1);
          }
        }).catch(e => {
          console.error(e);
          process.exit(1);
        });
  });
});
