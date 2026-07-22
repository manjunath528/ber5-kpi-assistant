# BER5 KPI Assistant Test Data

This folder contains fictional sample data for development and testing only.

The files are designed to match the existing Google Sheet `Raw Data` tab structure:

- `raw-data-sample.csv`
- `raw-data-sample.xlsx`

The dataset contains 100 fictional warehouse return rows across roughly two weeks. It includes successful gradings, failed gradings, MDM locked, FMI locked, FRP locked, User locked, and normal completed returns.

No real customer names, tracking numbers, addresses, employee names, emails, device identifiers, or confidential business information are included. All identifiers use synthetic `TEST-*` values.

## How To Use

1. Open the development or test Google Sheet.
2. Open the `Raw Data` tab.
3. Import either `raw-data-sample.csv` or `raw-data-sample.xlsx`.
4. Keep the first row as the header row.
5. Do not rename, reorder, add, or remove columns.
6. Run the BER5 KPI Assistant for one of the sample dates from `2026-07-01` through `2026-07-14`.

> **Important**
>
> Use this data only in development or test environments. Do not import it into a production reporting sheet.
