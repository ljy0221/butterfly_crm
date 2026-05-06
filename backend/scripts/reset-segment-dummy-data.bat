@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "SQL_FILE=%SCRIPT_DIR%dev-segment-dummy-data.sql"

if not defined MYSQL_CONTAINER set "MYSQL_CONTAINER=butterfly-mysql"
if not defined MYSQL_DATABASE set "MYSQL_DATABASE=butterfly"
if not defined MYSQL_USER set "MYSQL_USER=root"
if not defined MYSQL_PASSWORD set "MYSQL_PASSWORD=root"

if not exist "%SQL_FILE%" (
  echo [ERROR] SQL file not found: %SQL_FILE%
  exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Docker is not running.
  exit /b 1
)

docker inspect "%MYSQL_CONTAINER%" >nul 2>&1
if errorlevel 1 (
  echo [ERROR] MySQL container not found: %MYSQL_CONTAINER%
  echo         Run first from backend: docker compose up -d mysql
  exit /b 1
)

echo [1/3] Copy SQL into MySQL container.
docker cp "%SQL_FILE%" "%MYSQL_CONTAINER%:/tmp/dev-segment-dummy-data.sql"
if errorlevel 1 (
  echo [ERROR] Failed to copy SQL file.
  exit /b 1
)

docker exec "%MYSQL_CONTAINER%" sh -c "mysql --default-character-set=utf8mb4 -h127.0.0.1 -P3306 -u%MYSQL_USER% -p%MYSQL_PASSWORD% -N -B -e \"SHOW TABLES LIKE 'segment_members'\" %MYSQL_DATABASE% | grep -q segment_members"
if errorlevel 1 (
  echo [ERROR] Required table 'segment_members' does not exist.
  echo         Run API once so Flyway creates V1-V6 tables:
  echo         run-api-for-migration.bat
  echo.
  echo         After the API starts successfully, stop it with Ctrl+C and run this BAT again.
  exit /b 1
)

echo [2/3] Reset and seed segment dummy data for project_id=9001.
docker exec "%MYSQL_CONTAINER%" sh -c "mysql --default-character-set=utf8mb4 -h127.0.0.1 -P3306 -u%MYSQL_USER% -p%MYSQL_PASSWORD% %MYSQL_DATABASE% < /tmp/dev-segment-dummy-data.sql"
if errorlevel 1 (
  echo [ERROR] Failed to seed dummy data.
  echo         If this is Access denied, the existing mysql-data volume may use a different account.
  echo         Example: set MYSQL_USER=your_user ^&^& set MYSQL_PASSWORD=your_password ^&^& backend\scripts\reset-segment-dummy-data.bat
  exit /b 1
)

echo [3/3] Done.
echo Project ID: 9001
echo Main segment ID: 901001 ^(expected 2 users after rebuild^)
echo Segment users API: GET /api/v1/projects/9001/segments/901001/users
echo Rebuild API: POST /api/v1/projects/9001/segments/901001/rebuild with X-Member-Id: 9001

endlocal
