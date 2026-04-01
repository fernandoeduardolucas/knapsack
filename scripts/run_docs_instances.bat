@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM Script para executar todas as instâncias de docs/inst_test/instancias no Windows (cmd.exe)

set "ROOT_DIR=%~dp0.."
for %%I in ("%ROOT_DIR%") do set "ROOT_DIR=%%~fI"
set "SEARCH_DIR=%ROOT_DIR%\docs\inst_test\instancias"

REM Tenta configurar JAVA_HOME automaticamente a partir do java no PATH
if "%JAVA_HOME%"=="" (
  for /f "delims=" %%J in ('where java 2^>nul') do (
    set "JAVA_EXE=%%~fJ"
    goto :resolve_java_home
  )
)
goto :after_java_home

:resolve_java_home
for %%J in ("%JAVA_EXE%") do set "JAVA_BIN_DIR=%%~dpJ"
if defined JAVA_BIN_DIR (
  for %%J in ("%JAVA_BIN_DIR%..") do set "JAVA_HOME=%%~fJ"
)

:after_java_home
if "%HEURISTIC_NAME%"=="" set "HEURISTIC_NAME=aco"
if "%CSV_OUT%"=="" set "CSV_OUT=%ROOT_DIR%\results\docs_instances_%HEURISTIC_NAME%_results.csv"
if "%OPTIMAL_PROPS%"=="" set "OPTIMAL_PROPS=%ROOT_DIR%\src\main\resources\optimal-values.properties"
if "%INSTANCE_NAME_PROPS%"=="" set "INSTANCE_NAME_PROPS=%ROOT_DIR%\src\main\resources\instance-name-mapping.properties"

if not exist "%SEARCH_DIR%" (
  echo Pasta de instancias nao encontrada: "%SEARCH_DIR%"
  exit /b 1
)

for %%I in ("%CSV_OUT%") do (
  if not exist "%%~dpI" mkdir "%%~dpI" >nul 2>nul
)

echo instancia,n,c,g,f,eps,s,capacidade,itens,melhor_valor,peso_total,itens_escolhidos,valor_otimo,diferenca_para_otimo,leitura>"%CSV_OUT%"

pushd "%ROOT_DIR%" || exit /b 1

call mvnw.cmd -q -DskipTests compile
if errorlevel 1 (
  popd
  exit /b 1
)

set "HAS_FILES=0"
for /f "delims=" %%F in ('dir /b /a-d /on "%SEARCH_DIR%"') do (
  set "HAS_FILES=1"
  set "NAME=%%~nF"
  set "FILE=%SEARCH_DIR%\%%F"
  set "SKIP=0"
  set "N_PARAM="
  set "C_PARAM="
  set "G_PARAM="
  set "F_PARAM="
  set "EPS_PARAM="
  set "S_PARAM="
  set "OPTIMAL_CSV="
  set "DIFF_CSV="
  set "LEITURA_CSV="

  echo %%F | findstr /R /I "^README\(\..*\)\?$" >nul && set "SKIP=1"

  if "!SKIP!"=="0" (
    set "FIRST_LINE="
    for /f "usebackq delims=" %%L in (`powershell -NoProfile -Command "(Get-Content -LiteralPath '%SEARCH_DIR%\%%F' -TotalCount 1).Trim()"`) do set "FIRST_LINE=%%L"
    echo(!FIRST_LINE!| findstr /R "^[0-9][0-9]*$" >nul
    if errorlevel 1 set "SKIP=1"
  )

  if "!SKIP!"=="0" (
    call :map_instance_name "!NAME!" NAME_PARSE
    call :parse_metadata "!NAME_PARSE!" N_PARAM C_PARAM G_PARAM F_PARAM EPS_PARAM S_PARAM

    echo ============================================================
    echo Instancia: !FILE!

    set "OUTPUT_FILE=%TEMP%\aco_output_!RANDOM!_!RANDOM!.txt"
    if defined S_PARAM (
      java -cp target\classes org.metaheuristicas.knapsack.ACOKnapsack "!FILE!" --seed !S_PARAM! > "!OUTPUT_FILE!"
    ) else (
      java -cp target\classes org.metaheuristicas.knapsack.ACOKnapsack "!FILE!" > "!OUTPUT_FILE!"
    )
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

    echo "!NAME!" | findstr /R "^n_[0-9][0-9]*_[0-9][0-9]*$" >nul
    if not errorlevel 1 (
      if exist "!OPTIMAL_PROPS!" (
        call :get_property "!OPTIMAL_PROPS!" "!NAME!" OPTIMAL_CSV
        if defined OPTIMAL_CSV (
          call :is_integer "!OPTIMAL_CSV!" IS_OPTIMAL_INT
          call :is_integer "!MELHOR_VALOR!" IS_BEST_INT
          if "!IS_OPTIMAL_INT!"=="1" if "!IS_BEST_INT!"=="1" (
            set /a DIFF_CSV=!OPTIMAL_CSV!-!MELHOR_VALOR!
            if !DIFF_CSV! LSS 0 (
              set "LEITURA_CSV=inconsistente"
            ) else if !DIFF_CSV! LEQ 1000 (
              set "LEITURA_CSV=praticamente ótimo"
            ) else if !DIFF_CSV! LEQ 20000 (
              set "LEITURA_CSV=muito perto do ótimo"
            ) else (
              set "LEITURA_CSV=abaixo do ótimo"
            )
          )
        )
      )
    )

    >>"%CSV_OUT%" echo %%F,!N_PARAM!,!C_PARAM!,!G_PARAM!,!F_PARAM!,!EPS_PARAM!,!S_PARAM!,!CAPACIDADE!,!ITENS!,!MELHOR_VALOR!,!PESO_TOTAL!,"!ITENS_ESCOLHIDOS!",!OPTIMAL_CSV!,!DIFF_CSV!,!LEITURA_CSV!

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

:get_property
setlocal EnableDelayedExpansion
set "FILE=%~1"
set "KEY=%~2"
set "VALUE="
if exist "!FILE!" (
  for /f "usebackq tokens=1,* delims==" %%A in (`findstr /B /L /C:"!KEY!=" "!FILE!"`) do (
    set "VALUE=%%B"
    goto :get_property_done
  )
)
:get_property_done
endlocal & set "%~3=%VALUE%"
exit /b 0

:map_instance_name
setlocal EnableDelayedExpansion
set "ORIGINAL=%~1"
set "MAPPED="
if exist "!INSTANCE_NAME_PROPS!" (
  call :get_property "!INSTANCE_NAME_PROPS!" "!ORIGINAL!" MAPPED
)
if not defined MAPPED set "MAPPED=!ORIGINAL!"
endlocal & set "%~2=%MAPPED%"
exit /b 0

:parse_metadata
setlocal EnableDelayedExpansion
set "TEXT=%~1"
set "N="
set "C="
set "G="
set "F="
set "EPS="
set "S="
for /f "tokens=1-12 delims=_" %%A in ("!TEXT!") do (
  if /I "%%A"=="n" if /I "%%C"=="c" if /I "%%E"=="g" if /I "%%G"=="f" if /I "%%I"=="eps" if /I "%%K"=="s" (
    set "N=%%B"
    set "C=%%D"
    set "G=%%F"
    set "F=%%H"
    set "EPS=%%J"
    set "S=%%L"
  )
)
endlocal & (
  set "%~2=%N%"
  set "%~3=%C%"
  set "%~4=%G%"
  set "%~5=%F%"
  set "%~6=%EPS%"
  set "%~7=%S%"
)
exit /b 0

:is_integer
setlocal EnableDelayedExpansion
set "VALUE=%~1"
set "RESULT=0"
echo(!VALUE!| findstr /R "^[0-9][0-9]*$" >nul && set "RESULT=1"
endlocal & set "%~2=%RESULT%"
exit /b 0
