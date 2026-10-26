# Backup v0.16 – 26/10/2026

Estado final da TV (Xiaomi MiTV-MOOQ0, Android TV 10) depois das Fases 6 a 8, coletado por ADB, só leitura.
Sem número de série, IP ou contas.

| Arquivo | Conteúdo |
| --- | --- |
| `pacotes-todos.txt` | Todos os pacotes, com versionCode (126: os 125 de antes + `com.mihir.tvhome`) |
| `pacotes-ativos.txt` | Pacotes ativos (105) |
| `pacotes-desativados.txt` | Pacotes desativados (21) = `versoes/debloat-v1.txt` + YouTube, YouTube Music e `com.mitv.tvlock`, que já estavam desativados |
| `pacotes-terceiros.txt` | Pacotes instalados pelo usuário (7, com o launcher) |
| `launcher-atual.txt` | App do botão Home: `com.mihir.tvhome/.MainActivity` |
| `acessibilidade.txt` | Serviço de acessibilidade ativo: o Guard do launcher |

Teste de restauração (26/10/2026): `tools/restore-windows.sh versoes/debloat-v1.txt` deixou desativados só os 3 que já
estavam antes do projeto e voltou o Home para o launcher do Google; `tools/apply-windows.sh versoes/debloat-v1.txt`
reaplicou e o resultado bateu com este backup.
