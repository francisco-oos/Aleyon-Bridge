# ADR-0001 — Memoria local y un chat canónico por perfil

**Estado:** Aceptado  
**Fecha:** 2026-09-17

## Contexto

Bridge 0.3 utilizaba Gemini Notebook como memoria persistente. Para iniciar una sesión debía localizar notebook, sincronizar, separar chat, localizarlo de nuevo, iniciar Live y reinsertarlo al terminar. Era lento y ampliaba la superficie de rotura ante cambios de UI.

Idioma-Tutor demostró una separación más robusta: identidad/memoria/progreso pertenecen a la aplicación y el modelo sólo razona sobre una cápsula de continuidad.

Además, la experiencia física del usuario confirma que el modo temporal de Gemini no ofrece Live, por lo que no sirve como ruta principal.

## Decisión

- Aleyon conserva memoria local autoritativa.
- Cada perfil reutiliza un solo chat de Gemini.
- Live y Chat comparten el mismo chat y la misma cápsula.
- No se borra el chat automáticamente al cerrar.
- Notebooks y attach/detach quedan fuera del runtime.

## Consecuencias

Positivas:
- menos pasos de automatización;
- menor latencia esperada;
- menor fragilidad;
- continuidad de Gemini y Aleyon simultáneamente;
- aprovechamiento de funciones Pro/multimodales sin reimplementarlas.

Riesgos:
- el chat de Gemini puede crecer mucho;
- Accessibility puede ver sólo parte del historial;
- el título puede duplicarse manualmente.

Mitigaciones:
- compactación Aleyon independiente del historial;
- `SESSION_ID` y reporte acotado;
- fail closed ante duplicados exactos;
- QA físico de selectores.
