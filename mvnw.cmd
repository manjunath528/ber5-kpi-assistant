@echo off
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)
echo Maven is required to run this project. Install Maven or replace this script with the standard Maven Wrapper.
exit /b 1
