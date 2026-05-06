# DEVELOP

Guia para desarrollo y contribucion del proyecto.

## Requisitos

- JDK 25+
- Gradle Wrapper (`./gradlew`)

## Build

```bash
./gradlew clean
./gradlew build
```

## Ejecutar localmente

```bash
./gradlew run
```

Generar distribucion local:

```bash
./gradlew installDist
```

## Tests

```bash
./gradlew test
```

Test especifico:

```bash
./gradlew test --tests GradleGroovyDependencyParserTest
./gradlew test --tests ClassName.methodName
```

## Variables de entorno utiles

- `OSS_INDEX_TOKEN`: recomendado para evitar limite bajo de OSS Index.
- `NVD_API_KEY`: recomendado si pruebas `--use-nvd`.
- `DEPANALYZER_TRUSTED_CREDENTIAL_HOSTS`: hosts autorizados para enviar credenciales de repositorios.

## Estructura general

- `src/main/kotlin/com/depanalyzer/cli` - comandos CLI (`analyze`, `tui`, `update`)
- `src/main/kotlin/com/depanalyzer/core` - orquestacion de analisis
- `src/main/kotlin/com/depanalyzer/parser` - parseo Maven/Gradle
- `src/main/kotlin/com/depanalyzer/repository` - clientes OSS Index / NVD / metadata
- `src/main/kotlin/com/depanalyzer/report` - generacion y renderizado de reportes
- `src/main/kotlin/com/depanalyzer/update` - planificacion y aplicacion de actualizaciones

## Convenciones

- Usa Kotlin code style oficial.
- Evita commits con credenciales o tokens.
- Para cambios de CLI, actualiza tambien `README.md`.
