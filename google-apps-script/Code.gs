var CONFIG = {
  SPREADSHEET_ID: '',
  SHEET_NAME: 'GBKK4_List',
  HEADERS: {
    account: 'account',
    branchCode: 'branchCode',
    shopName: 'shopName',
    route: 'route'
  },
  ACTIONS: {
    health: 'health',
    branches: 'branches',
    addBranch: 'addBranch',
    updateRoute: 'updateRoute'
  }
};

function doGet(e) {
  var action = getAction_(e);

  try {
    switch (action) {
      case CONFIG.ACTIONS.health:
        return jsonOutput_(buildHealthResponse_());
      case CONFIG.ACTIONS.branches:
        return jsonOutput_(buildBranchesResponse_());
      default:
        return jsonOutput_(errorResponse_(action, 'Unsupported GET action.'));
    }
  } catch (error) {
    return jsonOutput_(errorResponse_(action, error.message));
  }
}

function doPost(e) {
  var request = getRequestData_(e);
  var action = request.action;

  try {
    switch (action) {
      case CONFIG.ACTIONS.addBranch:
        return jsonOutput_(handleAddBranch_(request));
      case CONFIG.ACTIONS.updateRoute:
        return jsonOutput_(handleUpdateRoute_(request));
      default:
        return jsonOutput_(errorResponse_(action, 'Unsupported POST action.'));
    }
  } catch (error) {
    return jsonOutput_(errorResponse_(action, error.message));
  }
}

function buildHealthResponse_() {
  var sheet = getSheet_();
  var spreadsheet = sheet.getParent();

  return {
    ok: true,
    action: CONFIG.ACTIONS.health,
    spreadsheetName: spreadsheet.getName(),
    sheet: sheet.getName(),
    updatedAt: formatDateTime_(getSpreadsheetLastUpdated_(spreadsheet)),
    timestamp: formatDateTime_(new Date()),
    message: 'Branch Photo Vault Apps Script is healthy.'
  };
}

function buildBranchesResponse_() {
  var sheet = getSheet_();
  var spreadsheet = sheet.getParent();
  var headerMap = getHeaderMap_(sheet);
  var lastRow = sheet.getLastRow();
  var lastColumn = sheet.getLastColumn();
  var items = [];

  if (lastRow >= 2 && lastColumn > 0) {
    var values = sheet.getRange(2, 1, lastRow - 1, lastColumn).getValues();

    for (var i = 0; i < values.length; i++) {
      var row = values[i];
      var rowNumber = i + 2;
      if (isRowEmpty_(row)) {
        continue;
      }

      items.push(mapRowToBranchItem_(row, rowNumber, headerMap));
    }
  }

  return {
    ok: true,
    action: CONFIG.ACTIONS.branches,
    spreadsheetName: spreadsheet.getName(),
    sheet: sheet.getName(),
    updatedAt: formatDateTime_(getSpreadsheetLastUpdated_(spreadsheet)),
    count: items.length,
    items: items
  };
}

function handleAddBranch_(request) {
  var sheet = getSheet_();
  var spreadsheet = sheet.getParent();
  var headerMap = getHeaderMap_(sheet);
  var account = normalizeText_(request.account, true);
  var branchCode = normalizeText_(request.branchCode, true);
  var shopName = normalizeText_(request.shopName, false);
  var route = normalizeRoute_(request.route, true);
  var lock = LockService.getScriptLock();

  if (!account) {
    throw new Error('account is required.');
  }
  if (!branchCode) {
    throw new Error('branchCode is required.');
  }
  if (!shopName) {
    throw new Error('shopName is required.');
  }

  lock.waitLock(30000);
  try {
    var existing = findBranchRow_(sheet, headerMap, account, branchCode);
    if (existing) {
      return {
        ok: false,
        action: CONFIG.ACTIONS.addBranch,
        message: 'Branch already exists.',
        error: 'DUPLICATE_BRANCH',
        item: existing.item,
        updatedAt: formatDateTime_(new Date())
      };
    }

    var rowData = createRowData_(headerMap, {
      account: account,
      branchCode: branchCode,
      shopName: shopName,
      route: route
    });

    sheet.appendRow(rowData);
    var rowNumber = sheet.getLastRow();
    var item = {
      account: account,
      branchCode: branchCode,
      shopName: shopName,
      route: route,
      rowNumber: rowNumber
    };

    return {
      ok: true,
      action: CONFIG.ACTIONS.addBranch,
      message: 'Branch added successfully.',
      spreadsheetName: spreadsheet.getName(),
      sheet: sheet.getName(),
      item: item,
      updatedAt: formatDateTime_(getSpreadsheetLastUpdated_(spreadsheet))
    };
  } finally {
    lock.releaseLock();
  }
}

function handleUpdateRoute_(request) {
  var sheet = getSheet_();
  var spreadsheet = sheet.getParent();
  var headerMap = getHeaderMap_(sheet);
  var account = normalizeText_(request.account, true);
  var branchCode = normalizeText_(request.branchCode, true);
  var route = normalizeRoute_(request.route, true);
  var rowNumber = toIntegerOrNull_(request.rowNumber);
  var lock = LockService.getScriptLock();

  if (!account) {
    throw new Error('account is required.');
  }
  if (!branchCode) {
    throw new Error('branchCode is required.');
  }

  lock.waitLock(30000);
  try {
    var target = resolveBranchForUpdate_(sheet, headerMap, account, branchCode, rowNumber);
    if (!target) {
      return {
        ok: false,
        action: CONFIG.ACTIONS.updateRoute,
        message: 'Branch not found.',
        error: 'BRANCH_NOT_FOUND',
        updatedAt: formatDateTime_(new Date())
      };
    }

    var routeColumn = headerMap[CONFIG.HEADERS.route];
    sheet.getRange(target.rowNumber, routeColumn).setValue(route === null ? '' : route);

    var updatedItem = {
      account: account,
      branchCode: branchCode,
      shopName: target.item.shopName,
      route: route,
      rowNumber: target.rowNumber
    };

    return {
      ok: true,
      action: CONFIG.ACTIONS.updateRoute,
      message: 'Route updated successfully.',
      spreadsheetName: spreadsheet.getName(),
      sheet: sheet.getName(),
      item: updatedItem,
      updatedAt: formatDateTime_(getSpreadsheetLastUpdated_(spreadsheet))
    };
  } finally {
    lock.releaseLock();
  }
}

function resolveBranchForUpdate_(sheet, headerMap, account, branchCode, rowNumber) {
  if (rowNumber && rowNumber >= 2 && rowNumber <= sheet.getLastRow()) {
    var row = sheet.getRange(rowNumber, 1, 1, sheet.getLastColumn()).getValues()[0];
    var item = mapRowToBranchItem_(row, rowNumber, headerMap);
    if (
      normalizeText_(item.account, true) === account &&
      normalizeText_(item.branchCode, true) === branchCode
    ) {
      return {
        rowNumber: rowNumber,
        item: item
      };
    }
  }

  return findBranchRow_(sheet, headerMap, account, branchCode);
}

function findBranchRow_(sheet, headerMap, account, branchCode) {
  var lastRow = sheet.getLastRow();
  var lastColumn = sheet.getLastColumn();
  if (lastRow < 2 || lastColumn === 0) {
    return null;
  }

  var values = sheet.getRange(2, 1, lastRow - 1, lastColumn).getValues();

  for (var i = 0; i < values.length; i++) {
    var row = values[i];
    if (isRowEmpty_(row)) {
      continue;
    }

    var item = mapRowToBranchItem_(row, i + 2, headerMap);
    if (
      normalizeText_(item.account, true) === account &&
      normalizeText_(item.branchCode, true) === branchCode
    ) {
      return {
        rowNumber: i + 2,
        item: item
      };
    }
  }

  return null;
}

function getSheet_() {
  var spreadsheet = CONFIG.SPREADSHEET_ID
    ? SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID)
    : SpreadsheetApp.getActiveSpreadsheet();

  if (!spreadsheet) {
    throw new Error('Spreadsheet not found. Set CONFIG.SPREADSHEET_ID or bind this script to a spreadsheet.');
  }

  var sheet = spreadsheet.getSheetByName(CONFIG.SHEET_NAME);
  if (!sheet) {
    throw new Error('Sheet not found: ' + CONFIG.SHEET_NAME);
  }

  return sheet;
}

function getHeaderMap_(sheet) {
  if (sheet.getLastRow() < 1 || sheet.getLastColumn() < 1) {
    throw new Error('Sheet header row is missing.');
  }

  var headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
  var headerMap = {};

  for (var i = 0; i < headers.length; i++) {
    var headerName = String(headers[i]).trim();
    if (headerName) {
      headerMap[headerName] = i + 1;
    }
  }

  assertRequiredHeader_(headerMap, CONFIG.HEADERS.account);
  assertRequiredHeader_(headerMap, CONFIG.HEADERS.branchCode);
  assertRequiredHeader_(headerMap, CONFIG.HEADERS.shopName);
  assertRequiredHeader_(headerMap, CONFIG.HEADERS.route);

  return headerMap;
}

function assertRequiredHeader_(headerMap, headerName) {
  if (!headerMap[headerName]) {
    throw new Error('Missing required header: ' + headerName);
  }
}

function mapRowToBranchItem_(row, rowNumber, headerMap) {
  return {
    account: getCellByHeader_(row, headerMap, CONFIG.HEADERS.account),
    branchCode: getCellByHeader_(row, headerMap, CONFIG.HEADERS.branchCode),
    shopName: getCellByHeader_(row, headerMap, CONFIG.HEADERS.shopName),
    route: toIntegerOrNull_(getCellByHeader_(row, headerMap, CONFIG.HEADERS.route)),
    rowNumber: rowNumber
  };
}

function createRowData_(headerMap, payload) {
  var rowData = [];
  var lastColumn = 0;
  for (var key in headerMap) {
    if (headerMap[key] > lastColumn) {
      lastColumn = headerMap[key];
    }
  }

  for (var i = 0; i < lastColumn; i++) {
    rowData.push('');
  }

  rowData[headerMap[CONFIG.HEADERS.account] - 1] = payload.account;
  rowData[headerMap[CONFIG.HEADERS.branchCode] - 1] = payload.branchCode;
  rowData[headerMap[CONFIG.HEADERS.shopName] - 1] = payload.shopName;
  rowData[headerMap[CONFIG.HEADERS.route] - 1] = payload.route === null ? '' : payload.route;

  return rowData;
}

function getCellByHeader_(row, headerMap, headerName) {
  var columnIndex = headerMap[headerName];
  if (!columnIndex) {
    return '';
  }

  var value = row[columnIndex - 1];
  return value === null || value === undefined ? '' : String(value).trim();
}

function isRowEmpty_(row) {
  for (var i = 0; i < row.length; i++) {
    if (String(row[i]).trim() !== '') {
      return false;
    }
  }
  return true;
}

function getRequestData_(e) {
  var data = {};

  if (e && e.parameter) {
    copyProperties_(e.parameter, data);
  }

  if (e && e.postData && e.postData.contents) {
    var bodyText = String(e.postData.contents).trim();
    if (bodyText && looksLikeJson_(bodyText)) {
      var jsonData = JSON.parse(bodyText);
      copyProperties_(jsonData, data);
    }
  }

  data.action = String(data.action || '').trim();
  return data;
}

function getAction_(e) {
  var action = e && e.parameter && e.parameter.action ? String(e.parameter.action).trim() : '';
  return action;
}

function copyProperties_(source, target) {
  if (!source) {
    return;
  }

  for (var key in source) {
    if (Object.prototype.hasOwnProperty.call(source, key) && source[key] !== undefined) {
      target[key] = source[key];
    }
  }
}

function looksLikeJson_(text) {
  return text.indexOf('{') === 0 && text.lastIndexOf('}') === text.length - 1;
}

function normalizeText_(value, upperCase) {
  if (value === null || value === undefined) {
    return '';
  }

  var text = String(value).trim();
  return upperCase ? text.toUpperCase() : text;
}

function normalizeRoute_(value, allowBlank) {
  if (value === null || value === undefined || String(value).trim() === '') {
    return allowBlank ? null : 0;
  }

  var route = parseInt(String(value).trim(), 10);
  if (isNaN(route)) {
    throw new Error('route must be an integer.');
  }

  return route;
}

function toIntegerOrNull_(value) {
  if (value === null || value === undefined || String(value).trim() === '') {
    return null;
  }

  var parsed = parseInt(String(value).trim(), 10);
  return isNaN(parsed) ? null : parsed;
}

function getSpreadsheetLastUpdated_(spreadsheet) {
  try {
    return DriveApp.getFileById(spreadsheet.getId()).getLastUpdated();
  } catch (error) {
    return new Date();
  }
}

function formatDateTime_(date) {
  return Utilities.formatDate(date, Session.getScriptTimeZone(), "yyyy-MM-dd'T'HH:mm:ssXXX");
}

function errorResponse_(action, message) {
  return {
    ok: false,
    action: action || null,
    message: message || 'Unknown error.',
    error: message || 'Unknown error.',
    updatedAt: formatDateTime_(new Date())
  };
}

function jsonOutput_(payload) {
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}
