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
if not defined KAFKA_BOOTSTRAP_SERVERS set "KAFKA_BOOTSTRAP_SERVERS=localhost:9092"
if not defined SPRING_MAIL_HOST set "SPRING_MAIL_HOST=localhost"
if not defined SPRING_MAIL_PORT set "SPRING_MAIL_PORT=1025"
if not defined SPRING_MAIL_USERNAME set "SPRING_MAIL_USERNAME=local@butterfly.local"
if not defined MAIL_FROM set "MAIL_FROM=local@butterfly.local"
set "SPRING_MAIN_LAZY_INITIALIZATION=true"

echo Start butterfly-worker for segment rebuild Kafka messages.
pushd "%BACKEND_DIR%"
gradlew.bat :butterfly-worker:bootRun -x test -x integrationTest
set "EXIT_CODE=%ERRORLEVEL%"
popd

exit /b %EXIT_CODE%
