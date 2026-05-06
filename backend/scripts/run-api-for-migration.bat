@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "BACKEND_DIR=%SCRIPT_DIR%.."
set "ENV_FILE=%BACKEND_DIR%\.env"

if exist "%ENV_FILE%" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
    if not "%%A"=="" set "%%A=%%B"
  )
)

if not defined DB_USERNAME set "DB_USERNAME=root"
if not defined DB_PASSWORD set "DB_PASSWORD=root"
if not defined RABBITMQ_USERNAME set "RABBITMQ_USERNAME=guest"
if not defined RABBITMQ_PASSWORD set "RABBITMQ_PASSWORD=guest"
if not defined JWT_SECRET set "JWT_SECRET=butterfly-dev-secret-key-must-be-32chars!!"
if not defined AES_KEY set "AES_KEY=0123456789abcdef0123456789abcdef"
if not defined MAIL_FROM set "MAIL_FROM=local@butterfly.local"
set "SPRING_MAIN_LAZY_INITIALIZATION=true"

echo [1/2] Start local infrastructure.
pushd "%BACKEND_DIR%"
docker compose up -d mysql redis rabbitmq kafka
if errorlevel 1 (
  popd
  echo [ERROR] Failed to start docker compose services.
  exit /b 1
)

echo [2/2] Start butterfly-api with lazy initialization. Flyway will create DB tables during startup.
echo       When the API starts successfully, press Ctrl+C and run:
echo       scripts\reset-segment-dummy-data.bat
gradlew.bat :butterfly-api:bootRun -x test -x integrationTest
set "EXIT_CODE=%ERRORLEVEL%"
popd

exit /b %EXIT_CODE%
