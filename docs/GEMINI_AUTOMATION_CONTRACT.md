# Contrato de automatización Gemini

## Inicio

Bridge envía una cápsula con:

```text
ALEYON_CONTEXT_V3
SESSION_ID=...
PROFILE_ID=...
SCHEMA_VERSION=3
...
```

La cápsula NO contiene la línea exacta esperada de ACK para evitar falsos positivos al leer la propia entrada del usuario. Gemini debe responder:

```text
ALEYON_SESSION_READY SESSION_ID=<actual> PROFILE_ID=<actual> SCHEMA_VERSION=3
```

Sólo entonces se inicia Live o se declara Chat activo.

## Cierre

Después de terminar Live y dejar estabilizar la transcripción, Bridge solicita un informe delimitado:

```text
ALEYON_REPORT_BEGIN ...
SUMMARY|...
NEXT|...
FEEDBACK|...
EVENT|categoria|habilidad|estado|evidencia exacta
ALEYON_REPORT_END ...
```

El prompt usa placeholders de marker en vez de insertar la línea exacta esperada, evitando que la propia solicitud satisfaga el parser.

## Evidencia

`SessionTextDelta` resta best-effort las líneas visibles antes de la sesión de las líneas visibles al cerrar. Los `EVENT` sólo se guardan cuando su evidencia aparece en ese delta. Si el árbol accesible no contiene suficiente transcripción, Bridge guarda resumen/feedback pero rechaza el evento.
