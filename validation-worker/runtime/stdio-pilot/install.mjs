// Image preparation only. npm lifecycle scripts are never enabled.
import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
const samples = [
  ['memory', '@modelcontextprotocol/server-memory', '2.1.1'],
  ['filesystem', '@modelcontextprotocol/server-filesystem', '2.3.0'],
  ['missing-release', '@modelcontextprotocol/server-memory', '0.0.0-pilot-missing'],
];
const records = samples.map(([id, name, version]) => {
  const directory = `/opt/stdio-pilot/${id}`;
  mkdirSync(directory);
  const started = Date.now();
  const result = spawnSync('npm', ['install', '--prefix', directory, '--ignore-scripts',
    '--no-audit', '--no-fund', '--json', `${name}@${version}`],
    { encoding: 'utf8', timeout: 90000, maxBuffer: 65536 });
  // E404/ETARGET cannot distinguish unpublished, yanked and never-existent releases.
  let code;
  try { code = JSON.parse(result.stdout).error?.code; } catch {}
  return { id, name, version, directory, installExit: result.status,
    installMs: Date.now() - started,
    installStatus: result.status === 0 ? 'INSTALLED' :
      ['E404', 'ETARGET'].includes(code) ? 'MISSING_OR_YANKED_PACKAGE' : 'NOT_TESTED' };
});
for (const id of ['process-exit-control', 'protocol-error-control'])
  records.push({ id, installStatus: 'INSTALLED', syntheticControl: true });
writeFileSync('/opt/stdio-pilot/install-results.json', JSON.stringify(records));
