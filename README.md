# BER5 KPI Assistant

Small Spring Boot application for preparing the daily BER5 Return KPI Slack report.

The app is intentionally simple. It loads aggregated KPI values from Google Sheets through a Google Apps Script endpoint, asks the user for two manual Odoo values, validates the numbers, and generates a copy-ready Slack message.

## Architecture

| Layer | Purpose |
|---|---|
| Spring Boot | Serves the dashboard and JSON API. |
| Thymeleaf | Renders the single dashboard page. |
| Vanilla JavaScript | Loads KPI data, validates manual inputs, generates preview, and copies text. |
| Google Apps Script | Reads the Google Sheet and returns aggregated KPI values. |
| No database | The app stores no history and no user data. |

## Requirements

- Java 21 or newer
- Maven available on the machine
- Google Sheet access for coworkers
- Apps Script web app URL for `APPS_SCRIPT` mode

> **Note**
>
> This project includes lightweight `mvnw` and `mvnw.cmd` scripts that delegate to the installed Maven command.

## Run in MOCK Mode

MOCK mode returns deterministic sample KPI data and does not call Google.

macOS/Linux:

```bash
BER5_DATA_MODE=MOCK ./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
$env:BER5_DATA_MODE="MOCK"
.\mvnw.cmd spring-boot:run
```

Open:

```text
http://localhost:8080
```

## Run in APPS_SCRIPT Mode

Create a local configuration file:

```text
src/main/resources/application-local.properties
```

Use this format:

```properties
ber5.data-mode=APPS_SCRIPT
ber5.apps-script-url=https://script.google.com/macros/s/AKfycbxiK003l4ctfX69ZkodGxeIASd4pRySDm78l7ggpAot74DjyzS-0rpJHBzKraxfeKtY/exec
ber5.apps-script-api-key=replace-with-your-private-key
```

> **Important**
>
> Do not commit `application-local.properties`. It is ignored by Git because it contains the private Apps Script API key.

Start the app with the local profile from your IDE by setting the active Spring profile to:

```text
local
```

You can also use the included template:

```text
src/main/resources/application-local.example.properties
```

## Apps Script Setup

1. Open [script.google.com](https://script.google.com).
2. Create a new Apps Script project.
3. Paste `apps-script/Code.gs` into the editor.
4. If manifest editing is enabled, use `apps-script/appsscript.json`.
5. Open **Project Settings**.
6. Add the Script Properties below.
7. Deploy the script as a web app.
8. Copy the generated `/exec` URL.
9. Use that URL as `BER5_APPS_SCRIPT_URL`.

### Script Properties

| Property | Required | Description |
|---|---|---|
| `BER5_SPREADSHEET_ID` | Yes | The Google Sheet ID. |
| `BER5_RAW_DATA_SHEET_NAME` | No | Defaults to `Raw Data`. |
| `BER5_API_KEY` | Yes | Shared key used by the Spring Boot app. |
| `BER5_HEADER_ROW` | No | Defaults to `1`. |

Use these exact Script Properties:

| Property | Value |
|---|---|
| `BER5_SPREADSHEET_ID` | `1hc_EOqt5pZdTn-dhF0XlsMSH1EQjLl4oFFZqfzngtqc` |
| `BER5_RAW_DATA_SHEET_NAME` | `Raw Data` |
| `BER5_HEADER_ROW` | `1` |
| `BER5_API_KEY` | `user-defined private key` |

The spreadsheet ID belongs in Google Apps Script Script Properties. Do not add it as a Spring Boot environment variable.

## Expected Apps Script Response

```json
{
  "success": true,
  "reportDate": "2026-07-15",
  "receivedInOdoo": 36,
  "mdmLocked": 10,
  "fmiLocked": 4,
  "frpLocked": 3,
  "userLocked": 0,
  "successful": 30,
  "failed": 4,
  "gradingTotal": 34
}
```

The endpoint must return aggregated KPI values only. It must not return UUID, IMEI, serial number, tracking ID, operator, comments, or device-level rows.

### Expected Source Headers

The Google Sheet tab must be named `Raw Data`. The Apps Script reads only aggregated KPI values from these source headers:

| Header | Required | Used For |
|---|---|---|
| `FRP Status` | Yes | Counts `frpLocked` when the value is `locked`. |
| `FMiP Status` | Yes | Counts `fmiLocked` when the value is `locked`. |
| `MDM Status` | Yes | Counts `mdmLocked` when the value is `locked`. |
| `Successful?` | Yes | Counts `successful`, `failed`, and `gradingTotal`. |
| `DATE` or `Date` | Yes | Selects rows for the requested business date. `DATE` is preferred when both columns exist and the row value is not empty. |
| `UserLock` | No | Ignored in V1. The endpoint always returns `userLocked: 0`. |

Header matching trims leading and trailing spaces, lowercases text, collapses repeated whitespace, and preserves punctuation such as `?`, dots, and underscores.

Successful values are `true`, `yes`, `success`, `successful`, and `1`. Failed values are `false`, `no`, `failed`, `failure`, and `0`.

Supported source date formats are Google Sheet date cells, `2026-07-15`, `15.07.2026`, and `15/07/2026`.

## Coworker Workflow

1. Open the app.
2. Select the reporting date.
3. Click **Load KPI Data**.
4. Enter **Current locked count** from Odoo.
5. Enter **Grading backlog** from Odoo.
6. Click **Generate Preview**.
7. Review the Slack message.
8. Click **Copy Message**.
9. Paste into Slack.

## Slack Message Format

The generated message preserves the required German wording:

```text
Retoure Zahlen vom DD.MM.YYYY
X Retoure im Odoo vereinnahmt
Locked: X
X MDM locked bei Vereinnahmung (aktuell noch X ges.)
X FMI locked
X FRP locked
0 User locked
Gradings: X
X Successful
X Failed
Grading Backlog: X
X Retourenpakete vom DD.MM.YYYY sind noch nicht vereinnahmt
Anmerkungen:
```

## Troubleshooting

| Problem | What to Check |
|---|---|
| Google Sheet connection is not configured. | Set `BER5_APPS_SCRIPT_URL` and `BER5_APPS_SCRIPT_API_KEY`. |
| Unable to read KPI data. | Check the Apps Script deployment URL and access settings. |
| No KPI rows were found for the selected date. | Confirm the selected date exists in the raw-data sheet. |
| Apps Script endpoint returned an invalid response. | Confirm `Code.gs` returns all required fields. |
| Preview does not generate. | Load KPI data and enter both manual Odoo values. |
| Gradings validation fails. | Confirm `gradingTotal = successful + failed`. |

## Test and Build

```bash
./mvnw test
./mvnw clean package
```
