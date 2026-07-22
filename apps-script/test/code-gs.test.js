const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const code = fs.readFileSync(path.join(__dirname, '..', 'Code.gs'), 'utf8');
const context = vm.createContext({ console });
vm.runInContext(code, context);

const headers = [
  'UUID',
  'FRP Status',
  'FMiP Status',
  'MDM Status',
  'UserLock',
  'Date',
  'Successful?',
  'DATE',
  'Tracking ID',
  'IMEI1',
  'Serial No.',
  'Operator'
];

function build(rows, requestedDate = '2026-07-15') {
  return context.buildKpiResponseFromValues([headers, ...rows], requestedDate, 1);
}

function row({ frp = '', fmip = '', mdm = '', userLock = '', date = '', successful = '', upperDate = '' }) {
  return ['device-id', frp, fmip, mdm, userLock, date, successful, upperDate, 'tracking', 'imei', 'serial', 'operator'];
}

function testExactHeaderMatchingAndCounts() {
  const result = build([
    row({ frp: ' locked ', fmip: 'LOCKED', mdm: 'locked', date: '2026-07-15', successful: 'true' }),
    row({ frp: 'unlocked', fmip: 'locked', mdm: 'LOCKED', date: '15.07.2026', successful: 'yes' }),
    row({ frp: 'locked', fmip: 'unlocked', mdm: 'unlocked', date: '15/07/2026', successful: 'failed' }),
    row({ frp: 'locked', fmip: 'locked', mdm: 'locked', date: '2026-07-16', successful: 'true' })
  ]);

  assert.equal(result.success, true);
  assert.equal(result.receivedInOdoo, 3);
  assert.equal(result.frpLocked, 2);
  assert.equal(result.fmiLocked, 2);
  assert.equal(result.mdmLocked, 2);
  assert.equal(result.userLocked, 0);
  assert.equal(result.successful, 2);
  assert.equal(result.failed, 1);
  assert.equal(result.gradingTotal, 3);
}

function testNormalizedHeaderMatching() {
  const messyHeaders = [
    '  frp   status ',
    ' fmip status ',
    ' mdm status ',
    ' successful? ',
    ' date '
  ];
  const rows = [
    ['locked', 'locked', 'locked', 'success', '2026-07-15']
  ];

  const result = context.buildKpiResponseFromValues([messyHeaders, ...rows], '2026-07-15', 1);

  assert.equal(result.success, true);
  assert.equal(result.frpLocked, 1);
  assert.equal(result.fmiLocked, 1);
  assert.equal(result.mdmLocked, 1);
  assert.equal(result.successful, 1);
}

function testDatePreferenceAndFallback() {
  const result = build([
    row({ date: '2026-07-14', upperDate: '2026-07-15', successful: '1' }),
    row({ date: '2026-07-15', upperDate: '', successful: '0' })
  ]);

  assert.equal(result.success, true);
  assert.equal(result.receivedInOdoo, 2);
  assert.equal(result.successful, 1);
  assert.equal(result.failed, 1);
}

function testGoogleDateCell() {
  const result = build([
    row({ date: new Date(2026, 6, 15), successful: 'successful' })
  ]);

  assert.equal(result.success, true);
  assert.equal(result.receivedInOdoo, 1);
  assert.equal(result.successful, 1);
}

function testMissingHeaders() {
  const result = context.buildKpiResponseFromValues([
    ['FRP Status', 'FMiP Status', 'MDM Status', 'DATE'],
    ['locked', 'locked', 'locked', '2026-07-15']
  ], '2026-07-15', 1);

  assert.equal(result.success, false);
  assert.equal(result.error, 'MISSING_HEADERS');
  assert.deepEqual(Array.from(result.missingHeaders), ['Successful?']);
}

function testNoMatchingDateRows() {
  const result = build([
    row({ date: '2026-07-14', successful: 'true' })
  ]);

  assert.equal(result.success, false);
  assert.equal(result.error, 'No KPI rows were found for the selected date.');
}

testExactHeaderMatchingAndCounts();
testNormalizedHeaderMatching();
testDatePreferenceAndFallback();
testGoogleDateCell();
testMissingHeaders();
testNoMatchingDateRows();

console.log('Apps Script logic tests passed.');
