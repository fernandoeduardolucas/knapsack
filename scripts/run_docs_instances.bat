@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM Script para executar todas as instâncias de docs/inst_test/instancias no Windows (cmd.exe)

set "ROOT_DIR=%~dp0.."
for %%I in ("%ROOT_DIR%") do set "ROOT_DIR=%%~fI"
set "SEARCH_DIR=%ROOT_DIR%\docs\inst_test\instancias"

if "%HEURISTIC_NAME%"=="" set "HEURISTIC_NAME=aco"
if "%CSV_OUT%"=="" set "CSV_OUT=%ROOT_DIR%\results\docs_instances_%HEURISTIC_NAME%_results.csv"

if not exist "%SEARCH_DIR%" (
  echo Pasta de instancias nao encontrada: "%SEARCH_DIR%"
  exit /b 1
)

for %%I in ("%CSV_OUT%") do (
  if not exist "%%~dpI" mkdir "%%~dpI" >nul 2>nul
)

echo instancia,capacidade,itens,melhor_valor,peso_total,itens_escolhidos>"%CSV_OUT%"

pushd "%ROOT_DIR%" || exit /b 1

call mvnw.cmd -q -DskipTests compile
if errorlevel 1 (
  popd
  exit /b 1
)

set "HAS_FILES=0"
for /f "delims=" %%F in ('dir /b /a-d /on "%SEARCH_DIR%"') do (
  set "HAS_FILES=1"
  set "FILE=%SEARCH_DIR%\%%F"
  set "SKIP=0"

  echo %%F | findstr /R /I "^README\(\..*\)\?$" >nul && set "SKIP=1"

  if "!SKIP!"=="0" (
    set "FIRST_LINE="
    for /f "usebackq delims=" %%L in (`powershell -NoProfile -Command "(Get-Content -LiteralPath '%SEARCH_DIR%\%%F' -TotalCount 1).Trim()"`) do set "FIRST_LINE=%%L"
    echo(!FIRST_LINE!| findstr /R "^[0-9][0-9]*$" >nul
    if errorlevel 1 set "SKIP=1"
  )

  if "!SKIP!"=="0" (
    echo ============================================================
    echo Instancia: !FILE!

    set "OUTPUT_FILE=%TEMP%\aco_output_!RANDOM!_!RANDOM!.txt"
    java -cp target\classes org.metaheuristicas.knapsack.ACOKnapsack "!FILE!" > "!OUTPUT_FILE!"
    if errorlevel 1 (
      del /q "!OUTPUT_FILE!" >nul 2>nul
      popd
      exit /b 1
    )

    type "!OUTPUT_FILE!"

    set "CAPACIDADE="
    set "ITENS="
    set "MELHOR_VALOR="
    set "PESO_TOTAL="
    set "ITENS_ESCOLHIDOS="

    for /f "usebackq tokens=1,* delims=:" %%A in ("!OUTPUT_FILE!") do (
      set "K=%%A"
      set "V=%%B"
      if defined V if "!V:~0,1!"==" " set "V=!V:~1!"

      if /I "!K!"=="Capacidade" set "CAPACIDADE=!V!"
      if /I "!K!"=="Itens" set "ITENS=!V!"
      if /I "!K!"=="Melhor valor" set "MELHOR_VALOR=!V!"
      if /I "!K!"=="Peso total" set "PESO_TOTAL=!V!"
    )

    for /f "usebackq tokens=* delims=" %%L in (`findstr /B /C:"Itens escolhidos (índices): " "!OUTPUT_FILE!"`) do (
      set "ITENS_ESCOLHIDOS=%%L"
      set "ITENS_ESCOLHIDOS=!ITENS_ESCOLHIDOS:Itens escolhidos (índices): =!"
    )

    >>"%CSV_OUT%" echo %%F,!CAPACIDADE!,!ITENS!,!MELHOR_VALOR!,!PESO_TOTAL!,"!ITENS_ESCOLHIDOS!"

    del /q "!OUTPUT_FILE!" >nul 2>nul
    echo.
  )
)

if "%HAS_FILES%"=="0" (
  echo Nenhuma instancia encontrada em "%SEARCH_DIR%"
)

echo CSV gerado em: "%CSV_OUT%"

popd
exit /b 0
