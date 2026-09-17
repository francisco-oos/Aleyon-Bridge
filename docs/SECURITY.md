# Seguridad y privacidad

- AccessibilityService sólo declara/acepta el paquete Gemini para automatización.
- Cada raíz se vuelve a verificar antes de actuar.
- No se usan coordenadas ni gesture injection.
- Bridge no solicita `RECORD_AUDIO`; no graba Live.
- No guarda credenciales de Google/Gemini.
- No borra chats de Gemini automáticamente.
- El reporte del modelo no muta progreso sin evidence gate.
- La memoria local alpha1 está en SharedPreferences; cifrado y backup controlado son pendientes explícitos antes de tratar datos sensibles o distribución amplia.
