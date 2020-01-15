const {spawnSync} = require('child_process');
const path = require('path');

const nodePath = process.argv[0];
const scriptArgs = process.argv.slice(2);
const nodeArgs = process.execArgv;

const pathToBin = path.join(__dirname, "node_modules/rollup/dist/bin/rollup");

const options = {
  stdio: 'inherit'
};

const spawnResult = spawnSync(nodePath, [...nodeArgs, pathToBin, ...scriptArgs], options);

if(spawnResult.status !== null) {
  process.exit(spawnResult.status);
}

if(spawnResult.error) {
  console.error(spawnResult.error);
  process.exit(1);
}
