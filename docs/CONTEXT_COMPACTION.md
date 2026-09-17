# Compactación de continuidad

## Objetivo

No reenviar toda la historia ni depender de memoria implícita del proveedor.

`ContextCapsuleBuilder` conserva siempre:
1. identidad de sesión/perfil;
2. idioma objetivo/apoyo;
3. nivel y estilo de corrección;
4. propósito/contexto del usuario;
5. última sesión resumida;
6. próximo objetivo;
7. hasta 8 evidencias recientes.

Los campos narrativos se limitan en longitud y se convierten a una sola línea cuando conviene.

## Relación con Anthropic

La inspiración es el patrón moderno de compaction/context editing: cuando la conversación crece, reemplazar historia de bajo valor por un resumen estructurado que preserve estado, decisiones y continuación. Aleyon aplica el principio localmente porque Gemini App no expone a Bridge una API equivalente para gestionar el contexto interno de Live.

## Regla de autoridad

Compactar no significa borrar estado. El ledger completo permanece fuera de Gemini; sólo se compacta lo que se inyecta como contexto activo.
