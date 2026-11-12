# Feed da TV (Noite Geek): YouTube + Crunchyroll

Roda no PC e entrega pra TV, só na rede de casa:
- **YouTube:** recomendados, novidades das inscrições e histórico (canal escolhido em `yt_channel`).
- **Crunchyroll:** "Continuar assistindo" (com quanto já viu) e a sua lista.

A TV busca a cada 15 minutos; o PC atualiza a cada 40 minutos. Inicia sozinho com o Windows
(um atalho pro `iniciar-feed.vbs` na pasta Inicializar: Win+R → `shell:startup`).

## Comando `noite-feed` (em qualquer terminal)
| Comando | O que faz |
|---|---|
| `noite-feed` | mostra se está rodando e o fim do log |
| `noite-feed iniciar` / `parar` | inicia escondido / para |
| `noite-feed reiniciar` | atualiza as listas agora |
| `noite-feed canal @seu_canal` | troca o canal do YouTube e reinicia |
| `noite-feed log` | log de hoje |
| `noite-feed pasta` | abre esta pasta |

## Renovar os cookies (quando o log disser "cookies vencidos" ou "login recusado")
Janela anônima do Chrome (a extensão "Get cookies.txt LOCALLY" precisa estar liberada no modo anônimo)
→ entre no site → Export → salve nesta pasta → **feche a janela anônima na hora** → `noite-feed reiniciar`.
- YouTube: `cookies.txt`
- Crunchyroll: `cookies_crunch.txt`

## Segredos
`cookies.txt`, `cookies_crunch.txt` e `config.json` (token da TV) dão acesso às suas contas.
Não mande pra ninguém. A porta 8787 só responde pra 192.168.1.x.
