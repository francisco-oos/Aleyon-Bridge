# Aleyon Bridge 0.5.0-alpha10 — Pedagogical State v2

## Alcance

Alpha10 modifica sólo la capa pedagógica/local. El árbol de transporte validado en alpha9 se mantiene conceptualmente intacto: Artemis, navegación Gemini, inicio Live, salida por X, CLOSE_SESSION, cross-chat guard y notificaciones no reciben nuevas responsabilidades.

## Problema

Alpha9 ya enviaba un contexto razonable, pero todavía incluía un resumen de sesión relativamente grande y hasta seis eventos crudos. Eso mezcla memoria histórica con estado útil y puede crecer innecesariamente.

Además, `Avance` y `A reforzar` eran eventos locales, pero no existía una capa explícita que distinguiera:

- perfil declarado por el alumno;
- observación pedagógica reciente;
- patrón repetido;
- estado relevante para la siguiente sesión.

## Solución

Se añaden:

- `PedagogicalState`
- `PedagogicalStateBuilder`

El estado se deriva localmente del `LearningLedger`. No requiere una nueva llamada a Gemini, no añade mensajes al cierre y no crea una segunda memoria autoritativa.

### Reglas

- máximo 2 avances recientes;
- máximo 2 elementos a reforzar;
- máximo 4 elementos de vocabulario sólo si existe evidencia explícita;
- nivel estimado sólo si existe un evento explícito compatible;
- un próximo objetivo;
- una sola observación de refuerzo = **observación**;
- evidencia equivalente repetida al menos dos veces = **patrón confirmado**.

El perfil declarado nunca se sobrescribe.

## Cápsula

`ContextCapsuleBuilder` deja de iterar eventos crudos. Envía:

1. perfil esencial;
2. política pedagógica compacta;
3. Pedagogical State v2;
4. instrucción de inicio natural.

Presupuesto duro:

- `MAX_CAPSULE_CHARS = 3400`
- `MAX_STATE = 760`

La política recupera sólo los principios de alto valor del tutor antiguo:

- conversar primero, enseñar en segundo plano;
- máximo una pregunta principal, sin obligación de terminar preguntando;
- corrección por gravedad;
- escalera gradual ante bloqueo;
- adaptación a desempeño real;
- no promover una observación aislada a debilidad.

## UI

El menú de perfil añade **Estado aprendido**, una vista de sólo lectura. Permite inspeccionar qué cree Aleyon que está aprendiendo sin mezclarlo con los campos que el usuario declaró.

## Compatibilidad

Los ledgers existentes siguen siendo válidos. El estado v2 se deriva de los eventos históricos disponibles; no requiere migración destructiva.

## Gate

- CI completo.
- Stub compile y Gradle APK.
- Core test de promoción observación → patrón confirmado.
- Presupuesto del estado <= 760.
- Static QA asegura que `ContextCapsuleBuilder` no itera `ledger.snapshot()`.
- Prueba física comparativa Samsung/Nubia para verificar que el inicio no se vuelve perceptiblemente más lento.
