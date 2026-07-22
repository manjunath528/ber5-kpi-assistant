const DEFAULT_RAW_DATA_SHEET_NAME = 'Raw Data';
const REQUIRED_SOURCE_HEADERS = ['FRP Status', 'FMiP Status', 'MDM Status', 'Successful?'];
const SUCCESSFUL_VALUES = ['true', 'yes', 'success', 'successful', '1'];
const FAILED_VALUES = ['false', 'no', 'failed', 'failure', '0'];

function doGet(e) {
  try {
    const params = e && e.parameter ? e.parameter : {};
    const properties = PropertiesService.getScriptProperties();
    const expectedKey = properties.getProperty('BER5_API_KEY');

    if (!expectedKey || params.key !== expectedKey) {
      return jsonResponse({ success: false, error: 'Unauthorized request.' });
    }

    const dateText = String(params.date || '').trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dateText)) {
      return jsonResponse({ success: false, error: 'The selected date does not exist in the sheet.' });
    }

    const spreadsheetId = properties.getProperty('BER5_SPREADSHEET_ID');
    if (!spreadsheetId) {
      return jsonResponse({ success: false, error: 'Google Sheet connection is not configured.' });
    }

    const sheetName = properties.getProperty('BER5_RAW_DATA_SHEET_NAME') || DEFAULT_RAW_DATA_SHEET_NAME;
    const headerRow = Number(properties.getProperty('BER5_HEADER_ROW') || '1');
    const spreadsheet = SpreadsheetApp.openById(spreadsheetId);
    const sheet = spreadsheet.getSheetByName(sheetName);

    if (!sheet) {
      return jsonResponse({ success: false, error: 'Google Sheet connection is not configured.' });
    }

    const values = sheet.getDataRange().getValues();
    return jsonResponse(buildKpiResponseFromValues(values, dateText, headerRow));
  } catch (error) {
    return jsonResponse({ success: false, error: 'Unable to read KPI data.' });
  }
}

function buildKpiResponseFromValues(values, requestedDateText, headerRow) {
  if (!Array.isArray(values) || values.length < headerRow || headerRow < 1) {
    return { success: false, error: 'The Apps Script endpoint returned an invalid response.' };
  }

  const headerCells = values[headerRow - 1];
  const headerIndex = buildHeaderIndex(headerCells);
  const missingHeaders = missingRequiredHeaders(headerIndex);

  if (missingHeaders.length > 0) {
    return {
      success: false,
      error: 'MISSING_HEADERS',
      message: 'Required headers are missing.',
      missingHeaders: missingHeaders
    };
  }

  const dateIndexes = getDateIndexes(headerIndex);
  const rows = values.slice(headerRow).filter(row => rowBusinessDate(row, dateIndexes) === requestedDateText);

  if (rows.length === 0) {
    return { success: false, error: 'No KPI rows were found for the selected date.' };
  }

  const successful = countSuccessful(rows, headerIndex.successful);
  const failed = countFailed(rows, headerIndex.successful);

  return {
    success: true,
    reportDate: requestedDateText,
    receivedInOdoo: rows.length,
    mdmLocked: countLocked(rows, headerIndex.mdmStatus),
    fmiLocked: countLocked(rows, headerIndex.fmipStatus),
    frpLocked: countLocked(rows, headerIndex.frpStatus),
    userLocked: 0,
    successful: successful,
    failed: failed,
    gradingTotal: successful + failed
  };
}

function buildHeaderIndex(headerCells) {
  const normalizedHeaders = headerCells.map(normalizeHeader);

  return {
    frpStatus: firstHeaderIndex(normalizedHeaders, 'FRP Status'),
    fmipStatus: firstHeaderIndex(normalizedHeaders, 'FMiP Status'),
    mdmStatus: firstHeaderIndex(normalizedHeaders, 'MDM Status'),
    successful: firstHeaderIndex(normalizedHeaders, 'Successful?'),
    date: headerIndexes(headerCells, normalizedHeaders, 'Date'),
    upperDate: headerIndexes(headerCells, normalizedHeaders, 'DATE')
  };
}

function missingRequiredHeaders(headerIndex) {
  const missing = [];
  if (headerIndex.frpStatus < 0) missing.push('FRP Status');
  if (headerIndex.fmipStatus < 0) missing.push('FMiP Status');
  if (headerIndex.mdmStatus < 0) missing.push('MDM Status');
  if (headerIndex.successful < 0) missing.push('Successful?');
  if (getDateIndexes(headerIndex).length === 0) missing.push('Date or DATE');
  return missing;
}

function normalizeHeader(value) {
  return String(value || '').trim().toLowerCase().replace(/\s+/g, ' ');
}

function normalizeStatus(value) {
  return String(value || '').trim().toLowerCase();
}

function firstHeaderIndex(normalizedHeaders, sourceHeader) {
  return normalizedHeaders.indexOf(normalizeHeader(sourceHeader));
}

function headerIndexes(headerCells, normalizedHeaders, sourceHeader) {
  const normalizedSource = normalizeHeader(sourceHeader);
  const exactSource = String(sourceHeader).trim();
  const indexes = [];

  for (let index = 0; index < normalizedHeaders.length; index++) {
    if (normalizedHeaders[index] === normalizedSource && String(headerCells[index] || '').trim() === exactSource) {
      indexes.push(index);
    }
  }

  if (indexes.length === 0) {
    for (let index = 0; index < normalizedHeaders.length; index++) {
      if (normalizedHeaders[index] === normalizedSource) {
        indexes.push(index);
      }
    }
  }

  return indexes;
}

function getDateIndexes(headerIndex) {
  const indexes = [];
  headerIndex.upperDate.forEach(index => {
    if (indexes.indexOf(index) < 0) indexes.push(index);
  });
  headerIndex.date.forEach(index => {
    if (indexes.indexOf(index) < 0) indexes.push(index);
  });
  return indexes;
}

function rowBusinessDate(row, dateIndexes) {
  for (const dateIndex of dateIndexes) {
    const dateText = toDateText(row[dateIndex]);
    if (dateText) {
      return dateText;
    }
  }
  return '';
}

function toDateText(value) {
  if (Object.prototype.toString.call(value) === '[object Date]' && !isNaN(value)) {
    return formatDateCell(value);
  }

  const text = String(value || '').trim();
  if (!text) {
    return '';
  }

  let match = text.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (match) {
    return `${match[1]}-${match[2]}-${match[3]}`;
  }

  match = text.match(/^(\d{2})\.(\d{2})\.(\d{4})$/);
  if (match) {
    return `${match[3]}-${match[2]}-${match[1]}`;
  }

  match = text.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
  if (match) {
    return `${match[3]}-${match[2]}-${match[1]}`;
  }

  return '';
}

function formatDateCell(value) {
  if (typeof Utilities !== 'undefined' && typeof Session !== 'undefined') {
    return Utilities.formatDate(value, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  }

  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function countLocked(rows, columnIndex) {
  return countMatching(rows, columnIndex, value => normalizeStatus(value) === 'locked');
}

function countSuccessful(rows, columnIndex) {
  return countMatching(rows, columnIndex, value => SUCCESSFUL_VALUES.indexOf(normalizeStatus(value)) >= 0);
}

function countFailed(rows, columnIndex) {
  return countMatching(rows, columnIndex, value => FAILED_VALUES.indexOf(normalizeStatus(value)) >= 0);
}

function countMatching(rows, columnIndex, matcher) {
  if (columnIndex < 0) {
    return 0;
  }
  return rows.filter(row => matcher(row[columnIndex])).length;
}

function jsonResponse(payload) {
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}
