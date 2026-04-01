@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0scripts"
set "RUNNER=%SCRIPT_DIR%\run_docs_instances.bat"

if not exist "%RUNNER%" (
  echo Script nao encontrado: "%RUNNER%"
  exit /b 1
)

call "%RUNNER%" %*
exit /b %ERRORLEVEL%
