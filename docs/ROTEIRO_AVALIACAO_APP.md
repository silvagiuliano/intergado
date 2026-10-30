# Roteiro para iniciar e avaliar o Intergado

O painel mostra **fazendas e solicitações de compra**. Os quatro saldos iniciais das fazendas foram cadastrados a partir do resumo de `01_Controle_gado.xlsx`; as linhas completas das planilhas de gado, manejo, ração e aplicação ainda não foram importadas. Veja a avaliação em [AVALIACAO_PLANILHAS_DASHBOARDS.md](AVALIACAO_PLANILHAS_DASHBOARDS.md).

## 1. Iniciar

No terminal, a partir da raiz do projeto:

```bash
cd /opt/devel/app/projeto_augusto
cp -n .env.example .env   # preencha AUTH_ALLOWED_EMAILS e RESEND_API_KEY
docker compose up --build -d
docker compose ps
```

O resultado esperado de `docker compose ps` é `purchases-api` e `analytics-api` como **healthy**, e `web` como **Up**. A primeira execução pode demorar porque baixa imagens e dependências. O SQLite é um arquivo compartilhado pelos serviços; não há contêiner nem porta de banco de dados.

Abra **https://localhost** (Caddy, com as portas `CADDY_HTTPS_PORT` do `.env`) ou **http://localhost:3000** (Nginx direto) no navegador da máquina que executa o Docker. Com o certificado interno do Caddy, o navegador mostra um aviso até a CA local ser confiável; veja [HTTPS com Caddy](../README.md#https-com-caddy). Se o projeto roda em um servidor remoto, encaminhe a porta 3000 por SSH ou acesse o endereço desse servidor conforme sua rede permitir.

Para executar os três serviços diretamente em **modo de desenvolvimento**, siga os comandos do [README](../README.md#desenvolvimento-local). Os endereços de escuta e portas ficam no `.env` da raiz; com os valores padrão, a interface fica em `http://IP_DA_MAQUINA:5173`.

## 2. Entrar

A primeira tela pede um e-mail. Informe um endereço listado em `AUTH_ALLOWED_EMAILS`, clique em **Enviar código** e digite o código de 6 dígitos recebido. Sem `RESEND_API_KEY`, o código aparece no log: `docker compose logs purchases-api | grep "Código de acesso"`. O e-mail logado e o botão **Sair** ficam no topo do painel.

## 3. Conferir a tela inicial

Depois do login, o painel deve abrir com o título **Visão das fazendas** e quatro cartões: Santa Cruz, Ilha Verde, Novo Mundo e Campo Verde. Cada cartão mostra a data e o saldo histórico da própria fazenda. Abaixo aparecem quatro indicadores de compras, gráfico de atividade, categorias e uma tabela de solicitações recentes. As compras iniciais são identificadas como **Exemplo**.

No banco de demonstração recém-criado, os valores esperados são:

| Indicador | Valor inicial |
| --- | ---: |
| Solicitações | 7 |
| Em andamento | 5 |
| Aguardando aprovação | 1 |
| Valor estimado | R$ 54.650,00 |

Se você já criou solicitações, os valores serão diferentes. O botão de atualização no topo recarrega os dados.

## 4. Avaliar o fluxo principal

1. Na tabela, busque por **pivô**. A solicitação “Peças para pivô central” deve aparecer. Limpe a busca e experimente o filtro **Aguardando aprovação**.
2. Clique em **Nova solicitação**. Cadastre um item de teste, por exemplo: título `Mangueira de irrigação`, fazenda `Santa Cruz`, categoria `Manutenção`, solicitante `Teste de avaliação`, valor `250,00`. O campo fornecedor pode ficar vazio.
3. Confirme que a nova linha aparece com status **Recebido** e que o total de solicitações aumenta em 1.
4. Na coluna **Atualizar**, mude o status dessa linha para **Em cotação**. Confirme que o selo muda. Experimente o filtro **Em cotação**.
5. Atualize a página do navegador. O registro e o status devem continuar lá, pois ficam salvos no arquivo SQLite.
6. Volte ao topo e confira se os cartões e a distribuição por categoria refletem o item criado.

O cadastro de teste permanece no banco. Esta versão não tem exclusão pela interface; use um título que deixe claro que se trata de teste.

## 5. Conferir as APIs e o Swagger

As rotas `/api/*` exigem sessão. Pelo terminal, peça o código, confirme-o guardando o cookie e use-o nas chamadas:

```bash
curl --noproxy '*' -fsS -X POST -H 'Content-Type: application/json' -d '{"email":"seu@email.com"}' http://localhost:3000/api/auth/request-code
curl --noproxy '*' -fsS -c /tmp/intergado.cookies -X POST -H 'Content-Type: application/json' -d '{"email":"seu@email.com","code":"123456"}' http://localhost:3000/api/auth/verify
curl --noproxy '*' -fsS -b /tmp/intergado.cookies http://localhost:3000/api/purchases
curl --noproxy '*' -fsS -b /tmp/intergado.cookies http://localhost:3000/api/farms
curl --noproxy '*' -fsS -b /tmp/intergado.cookies http://localhost:3000/api/analytics/overview
```

No navegador, faça login no painel antes de usar o Swagger: ele aproveita o mesmo cookie. Abra `http://localhost:3000/swagger-ui/index.html` para cadastrar ou editar fazendas e compras. Abra `http://localhost:3000/q/swagger-ui` para consultar a documentação da API de indicadores. As APIs Java ficam atrás do Nginx, por isso essas URLs usam a mesma porta 3000 do site. Para testar uma edição sem criar dados extras, consulte primeiro o ID da fazenda e envie todos os campos no `PUT /api/farms/{id}`.

## 6. Integrações e WhatsApp (opcional)

Abra **Integrações** no menu lateral. Confira a origem de cada valor (salvo na tela, do `.env` ou padrão), altere um campo e salve. Para testar o envio pelo WhatsApp, suba também o Evolution API e siga o passo a passo em [EVOLUTION_API.md](EVOLUTION_API.md#como-testar).

## 7. Diagnosticar problemas

```bash
docker compose ps
docker compose logs --tail=100 purchases-api analytics-api web
```

- **Tela não abre:** verifique se `web` está `Up` e se a porta 3000 está livre.
- **Código não chega:** procure `Falha ao enviar` em `docker compose logs purchases-api`. Confira `RESEND_API_KEY` e se o remetente usa um domínio verificado; `onboarding@resend.dev` só entrega para o dono da conta Resend. Novo código só é gerado 60 segundos após o anterior.
- **E-mail correto, mas nada acontece:** ele precisa estar em `AUTH_ALLOWED_EMAILS`, e o serviço precisa ser reiniciado depois de mudar a lista (`docker compose up -d`).
- **Tela abre sem dados:** confira os logs de `purchases-api` e `analytics-api`.
- **Dados iniciais não batem:** o volume SQLite persiste entre reinicializações. Verifique se já houve cadastros anteriores.

## 8. Parar e reiniciar

```bash
docker compose down
docker compose up -d
```

Esses comandos mantêm os dados. **Não use `docker compose down -v` se quiser preservá-los**, pois `-v` remove o volume que contém o arquivo SQLite.

## O que avaliar nesta versão

Observe clareza dos indicadores, nomes das categorias e status, facilidade de localizar uma compra, dados necessários no cadastro e fluxo real de aprovação da fazenda. Registre também quais campos precisam identificar fazenda, pasto, produto e responsável para integrar futuramente as planilhas.

IA, OpenSearch, Realtec, perfis de permissão, recebimento de mensagens de WhatsApp e a importação completa das planilhas não fazem parte desta versão. O WhatsApp tem apenas envio de teste pelo Evolution API.
