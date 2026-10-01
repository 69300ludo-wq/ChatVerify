# ChatVerify

**Analyse et vérification de captures de messages**

> Scannez. Analysez. Vérifiez.

ChatVerify est une application Android de démonstration qui permet :

- d'importer une capture d'écran de conversation ;
- de programmer une **capture spéciale** reconnue par empreinte SHA-256 exacte ;
- d'associer à cette capture des informations personnalisées (contact, date/heure, message, chronologie, notes) ;
- d'analyser les autres captures localement (OCR, liens, termes à risque, dimensions et cohérence générale) ;
- d'exporter le résultat au format PDF ;
- de consulter un historique local.

## Important

La capture programmée est toujours affichée avec la mention **MODE DÉMO**. Les informations personnalisées ne constituent pas une preuve d'authenticité. Pour les autres captures, ChatVerify fournit un **score de cohérence** et des indicateurs ; une capture d'écran seule ne permet pas de prouver qu'un message est authentique.

## Build Android

Le projet est construit avec Kotlin + Jetpack Compose.

```bash
gradle :app:assembleDebug
```

L'APK est générée dans :

```text
app/build/outputs/apk/debug/app-debug.apk
```

Un workflow GitHub Actions (`Build APK`) compile automatiquement l'APK à chaque push sur `main` et permet aussi un lancement manuel.

## Confidentialité

L'analyse est effectuée localement dans l'application. Les captures ne sont pas envoyées vers un serveur par ce projet.
