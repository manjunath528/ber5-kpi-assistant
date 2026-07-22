# BER5 KPI Apps Script

This folder contains the Google Apps Script web endpoint used by the BER5 KPI Assistant.

## Deploy

1. Open [script.google.com](https://script.google.com).
2. Create a new Apps Script project.
3. Paste `Code.gs` into the project.
4. Add `appsscript.json` in the project settings if you use manifest editing.
5. Configure Script Properties.
6. Deploy as a web app.
7. Copy the `/exec` URL into `BER5_APPS_SCRIPT_URL`.

## Script Properties

| Property | Description |
|---|---|
| `BER5_SPREADSHEET_ID` | Google Sheet ID that contains the raw data. |
| `BER5_RAW_DATA_SHEET_NAME` | Optional. Defaults to `Raw Data`. |
| `BER5_API_KEY` | Shared key required by the Spring Boot app. |
| `BER5_HEADER_ROW` | Optional. Defaults to `1`. |

Use these exact Script Properties:

| Property | Value |
|---|---|
| `BER5_SPREADSHEET_ID` | `1hc_EOqt5pZdTn-dhF0XlsMSH1EQjLl4oFFZqfzngtqc` |
| `BER5_RAW_DATA_SHEET_NAME` | `Raw Data` |
| `BER5_HEADER_ROW` | `1` |
| `BER5_API_KEY` | `user-defined private key` |

## Request

```text
GET https://script.google.com/macros/s/.../exec?date=2026-07-15&key=your-key
```

## Response

The endpoint returns aggregated KPI values only. It never returns UUID, IMEI, serial number, tracking ID, operator, comments, or device-level rows.

## Expected Source Headers

The `Raw Data` tab must contain these source headers. Header matching trims leading and trailing spaces, lowercases text, collapses repeated whitespace, and preserves punctuation such as `?`, dots, and underscores.

Required headers:

| Header | Used For |
|---|---|
| `FRP Status` | Counts `frpLocked` when the value is `locked`. |
| `FMiP Status` | Counts `fmiLocked` when the value is `locked`. |
| `MDM Status` | Counts `mdmLocked` when the value is `locked`. |
| `Successful?` | Counts `successful`, `failed`, and `gradingTotal`. |
| `DATE` or `Date` | Selects rows for the requested business date. `DATE` is preferred when both columns exist and the row value is not empty. |

Optional for V1:

| Header | Behavior |
|---|---|
| `UserLock` | Ignored in V1. The endpoint always returns `userLocked: 0`. |

Ignored fields include `UUID`, `Tracking ID`, `IMEI1`, `IMEI2`, `Serial No.`, `Operator`, comments, and all hardware test fields.

Successful values:

```text
true
yes
success
successful
1
```

Failed values:

```text
false
no
failed
failure
0
```

Supported date formats:

```text
2026-07-15
15.07.2026
15/07/2026
Google Sheet date cells
```

## Local Apps Script Logic Tests

The helper test does not call Google. It checks header matching, date fallback, locked counts, success/failed counts, missing headers, and no matching date rows.

```bash
node apps-script/test/code-gs.test.js
```
