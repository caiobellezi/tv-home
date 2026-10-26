# Backup v0.6 – 26/10/2026

Estado da TV (Xiaomi MiTV-MOOQ0, Android TV 10) depois da Fase 3 e antes de qualquer desativação por ADB.
Coletado por ADB, só leitura. Sem número de série, IP ou contas: esses arquivos (settings e getprop) ficam fora do Git.

| Arquivo | Conteúdo |
| --- | --- |
| `pacotes-todos.txt` | Todos os pacotes, com versionCode (125) |
| `pacotes-ativos.txt` | Pacotes ativos (122) |
| `pacotes-desativados.txt` | Pacotes desativados (3): `com.google.android.youtube.tv`, `com.google.android.youtube.tvmusic`, `com.mitv.tvlock` |
| `pacotes-sistema.txt` | Pacotes do sistema (119) |
| `pacotes-terceiros.txt` | Pacotes instalados pelo usuário (6) |
| `launcher-atual.txt` | App que responde ao botão Home: `com.google.android.tvlauncher` |
| `launchers-disponiveis.txt` | Todos os apps com HOME: tvlauncher, `com.duokan.factorytest` (launcher de teste de fábrica) e `com.android.tv.settings` (FallbackHome) |

Para voltar a este estado: reativar (`pm enable`) o que tiver sido desativado depois desta data, comparando com `pacotes-desativados.txt`.
