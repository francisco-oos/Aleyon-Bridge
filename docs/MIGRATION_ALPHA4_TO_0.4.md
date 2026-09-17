# Migración alpha4 → 0.4

## Se conserva
- package id Android;
- SharedPreferences de perfiles;
- `PROFILE_ID`;
- `chatName` existente;
- UI WebView local;
- AccessibilityService;
- burbuja;
- fix Android 16 por ventanas;
- automatización semántica sin coordenadas.

## Se ignora al leer perfiles legacy
- `notebookName`;
- ownership de notebook;
- markers de sync/attach/detach.

`ProfileSpec.fromJson` tolera el JSON alpha4 y usa los campos actuales. El primer inicio 0.4 localizará el `chatName` existente. Si no existe, creará uno nuevo.

## No migrado automáticamente

El contenido pedagógico que sólo vivía en Notebook no puede considerarse memoria local verificada. El usuario puede conservar esos notebooks como archivo histórico; 0.4 comienza a construir su `LearningStore` con evidencia nueva.
