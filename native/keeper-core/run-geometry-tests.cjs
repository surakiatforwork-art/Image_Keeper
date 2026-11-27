const fs = require('node:fs');
const path = require('node:path');
const { WASI } = require('node:wasi');
const wasi = new WASI({version: 'preview1', args: ['geometry-tests', '--test-threads=1'], returnOnExit: true});
WebAssembly.instantiate(fs.readFileSync(path.join(__dirname, 'geometry-tests.wasm')), wasi.getImportObject())
  .then(({instance}) => { process.exitCode = wasi.start(instance); })
  .catch(err => { console.error(err); process.exitCode = 1; });
