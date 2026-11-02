#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

usage() {
  cat <<'EOF'
Uso: ./scripts/install-host-caddy.sh [--print]

Gera o site do Intergado para o Caddy geral do host a partir do .env
(CADDY_DOMAIN, WEB_PORT) e o instala em /etc/caddy/sites/intergado.caddy.
Valida a configuração completa antes de recarregar; se a validação falhar,
restaura o arquivo anterior e não recarrega.

  --print   Só mostra o site gerado, sem instalar (não precisa de sudo).

Pré-requisitos: Caddy instalado no host (caddy.service) e, no /etc/caddy/Caddyfile:
  import /etc/caddy/sites/*.caddy
EOF
}

print_only=false
for arg in "$@"; do
  case "$arg" in
    --print) print_only=true ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

log() { printf '[host-caddy] %s\n' "$*" >&2; }
fail() { printf '[host-caddy] ERRO: %s\n' "$*" >&2; exit 1; }

# Mesmo leitor do deploy-compose.sh: não executa o .env, vale a última ocorrência.
env_value() {
  local key="$1" default="${2:-}" line value=""
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[[:space:]]*${key}[[:space:]]*=(.*)$ ]] || continue
    value="${BASH_REMATCH[1]}"
  done < .env
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  if [[ "$value" == \"*\" || "$value" == \'*\' ]]; then value="${value:1:${#value}-2}"; fi
  printf '%s' "${value:-$default}"
}

[[ -f .env ]] || fail "arquivo .env ausente; copie .env.example e revise CADDY_DOMAIN e as portas"
template=caddy/host/intergado.caddy.template
[[ -s "$template" ]] || fail "modelo ausente: $template"

domain="$(env_value CADDY_DOMAIN intergado.localhost)"
web_port="$(env_value WEB_PORT 3000)"

# No Caddy geral, "localhost" capturaria o endereço de todos os projetos do host.
[[ "$domain" != localhost && "$domain" != 127.0.0.1 ]] \
  || fail "CADDY_DOMAIN=$domain é compartilhado por todos os projetos do host; use um nome próprio (ex.: intergado.localhost) ou o domínio real"
[[ "$domain" =~ ^[A-Za-z0-9.-]+$ ]] || fail "CADDY_DOMAIN inválido: $domain"
for port in "$web_port"; do
  [[ "$port" =~ ^[0-9]+$ ]] && (( port > 0 && port < 65536 )) || fail "porta inválida no .env: $port"
done

rendered="$(sed -e "s/@@DOMAIN@@/${domain}/g" \
                -e "s/@@WEB_PORT@@/${web_port}/g" "$template")"

if "$print_only"; then
  printf '%s\n' "$rendered"
  exit 0
fi

command -v caddy >/dev/null 2>&1 || fail "caddy não encontrado no host; instale pelo repositório oficial (docs/DEPLOY.md, seção 1.1)"
command -v systemctl >/dev/null 2>&1 || fail "systemctl indisponível; este script espera o caddy.service do pacote oficial"
SUDO=""
(( EUID == 0 )) || SUDO=sudo

main=/etc/caddy/Caddyfile
sites=/etc/caddy/sites
target="$sites/intergado.caddy"
[[ -f "$main" ]] || fail "$main não encontrado"
grep -Eq '^[[:space:]]*import[[:space:]]+/etc/caddy/sites/\*\.caddy' "$main" \
  || fail "$main não importa os sites; acrescente a linha: import /etc/caddy/sites/*.caddy"

$SUDO install -d -m 0755 "$sites"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
printf '%s\n' "$rendered" > "$tmp"

backup=""
if $SUDO test -f "$target"; then
  backup="${target}.bak"
  $SUDO cp -p "$target" "$backup"
fi
$SUDO install -m 0644 "$tmp" "$target"
$SUDO caddy fmt --overwrite "$target" >/dev/null

# Valida como o usuário do serviço para não criar arquivos de root em /var/lib/caddy.
if ! $SUDO -u caddy caddy validate --config "$main" --adapter caddyfile >/dev/null; then
  if [[ -n "$backup" ]]; then $SUDO cp -p "$backup" "$target"; else $SUDO rm -f "$target"; fi
  $SUDO -u caddy caddy validate --config "$main" --adapter caddyfile || true
  fail "configuração inválida; $target restaurado e o Caddy não foi recarregado"
fi

$SUDO systemctl reload caddy
log "Site instalado em $target e Caddy recarregado (domínio: $domain; web 127.0.0.1:$web_port)"

tls_flags=(--resolve "${domain}:443:127.0.0.1")
[[ "$domain" == *.localhost ]] && tls_flags+=(-k)
for _ in $(seq 1 10); do
  if curl -fsS --max-time 5 "${tls_flags[@]}" -o /dev/null "https://${domain}/caddy-health" 2>/dev/null; then
    log "https://${domain}/caddy-health respondeu"
    exit 0
  fi
  sleep 3
done
log "AVISO: https://${domain}/caddy-health ainda não respondeu; veja: journalctl -u caddy -n 100 (DNS, portas 80/443)"
