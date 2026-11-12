// Runs only inside the existing isolated, network-disabled worker sandbox.
import { spawn } from 'node:child_process';
import { readFileSync, mkdirSync } from 'node:fs';
import { createInterface } from 'node:readline';
const sample = JSON.parse(readFileSync('/opt/stdio-pilot/install-results.json', 'utf8'))
  .find(value => value.id === process.argv[2]);
if (!sample) throw new Error('Unsupported pilot sample');
const started = Date.now();
const report = (status, detail, extra = {}) => console.log(JSON.stringify({
  ...sample, status, detail, ...extra, protocolVersion: '2024-11-05',
  startedAt: new Date(started).toISOString(), runtimeMs: Date.now() - started,
  nodeVersion: process.version, network: 'none', lifecycleScripts: false,
}));
if (sample.installStatus !== 'INSTALLED') {
  report(sample.installStatus, 'No server was launched; see image install result');
} else {
  mkdirSync('/tmp/pilot-data', { recursive: true });
  const command = sample.id === 'process-exit-control' ? ['-e', 'process.exit(7)'] :
    sample.id === 'protocol-error-control' ? ['-e', 'console.log("invalid-json");setInterval(()=>{},1000)'] :
    [`${sample.directory}/node_modules/${sample.name}/dist/index.js`,
      ...(sample.id === 'filesystem' ? ['/tmp/pilot-data'] : [])];
  const child = spawn('node', command,
    { env: { PATH: process.env.PATH, HOME: '/tmp', MEMORY_FILE_PATH: '/tmp/memory.json' },
      stdio: ['pipe', 'pipe', 'ignore'] });
  let finished = false;
  let initialized = false;
  let bytes = 0;
  const finish = (status, detail, extra) => {
    if (finished) return;
    finished = true;
    clearTimeout(timer);
    report(status, detail, extra);
    child.kill('SIGKILL');
  };
  const send = message => child.stdin.write(JSON.stringify(message) + '\n');
  child.stdin.on('error', () => {}); // exit event supplies the distinct process outcome
  child.on('error', () => finish('PROCESS_EXIT', 'Server launch failed'));
  child.on('exit', (exitCode, signal) => finish('PROCESS_EXIT', 'Server exited before tools/list completed', { exitCode, signal }));
  const timer = setTimeout(() => finish('PROTOCOL_ERROR', 'Handshake/tools/list exceeded 15 seconds'), 15000);
  const lines = createInterface({ input: child.stdout });
  child.stdout.on('data', chunk => {
    bytes += chunk.length;
    if (bytes > 32768) finish('PROTOCOL_ERROR', 'Protocol response exceeded 32 KiB');
  });
  lines.on('line', line => {
    if (finished) return;
    let response;
    try { response = JSON.parse(line); } catch { finish('PROTOCOL_ERROR', 'Invalid JSON on stdout'); return; }
    if (response.jsonrpc !== '2.0') return finish('PROTOCOL_ERROR', 'Invalid JSON-RPC envelope');
    if (response.id === 1 && !initialized) {
      if (!response.result?.protocolVersion || !response.result?.capabilities || !response.result?.serverInfo)
        return finish('PROTOCOL_ERROR', 'Invalid initialize result');
      initialized = true;
      send({ jsonrpc: '2.0', method: 'notifications/initialized' });
      send({ jsonrpc: '2.0', id: 2, method: 'tools/list', params: {} });
    } else if (response.id === 2 && initialized) {
      if (!Array.isArray(response.result?.tools) || response.result.tools.some(tool =>
        typeof tool.name !== 'string' || tool.inputSchema?.type !== 'object'))
        return finish('PROTOCOL_ERROR', 'Invalid tools/list result');
      finish('INSTALLABLE', 'initialize and tools/list completed; no tools invoked', { toolCount: response.result.tools.length });
    }
  });
  send({ jsonrpc: '2.0', id: 1, method: 'initialize', params: {
    protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'mcp-compass-stdio-pilot', version: '1' },
  } });
}
