<#
.SYNOPSIS
  Reset de la BD de tests (issue #28): borra manti_test para que la siguiente
  suite (./mvnw test) la vuelva a crear y a migrar desde cero con Flyway.

.DESCRIPTION
  Idempotente: puede ejecutarse tantas veces como se quiera. La recreacion la
  hace la propia suite (TestDatabaseBootstrap + Flyway), no hace falta ningun
  paso manual posterior. El rol manti_test se conserva.

  Por defecto usa el contenedor del docker compose (manti-postgres en el
  puerto 5433). Overrides por entorno:
    PG_CONTAINER  nombre del contenedor (default: manti-postgres)
    PG_USER       usuario admin (default: manti)

.EXAMPLE
  cd backend
  .\scripts\reset-test-db.ps1
#>
param(
    [string]$Container = $(if ($env:PG_CONTAINER) { $env:PG_CONTAINER } else { 'manti-postgres' }),
    [string]$User = $(if ($env:PG_USER) { $env:PG_USER } else { 'manti' }),
    [string]$Database = 'manti_test'
)
$ErrorActionPreference = 'Stop'

if ($Database -notmatch '^[a-z_][a-z0-9_]*$' -or $Database -in @('manti', 'postgres', 'template0', 'template1')) {
    throw "Base protegida o nombre inválido: '$Database' (sólo se admite la BD de tests, p.ej. manti_test)."
}

docker exec $Container psql -U $User -d postgres -v ON_ERROR_STOP=1 `
    -c "DROP DATABASE IF EXISTS $Database;"
if ($LASTEXITCODE -ne 0) {
    throw "No se pudo dropear '$Database' en el contenedor '$Container' (¿docker compose up -d?)."
}

Write-Host "OK: '$Database' borrada. La proxima suite (./mvnw test) la recrea y migra sola."