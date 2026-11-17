GRUPO AM

Pesquisar
Ctrl K

Chats com IA


Início
5
Planejador
5
IA

Instalar

Convidar
Início
5Número de notificações não lidas
Caixa de entrada

Filtrar sua barra lateral

Caixa de entrada
5
Respostas
Comentários atribuídos
Posts
Habilidades
Artifacts
Reuniões
Minhas tarefas

Mais

Chats com IA

Mais ações
Pergunte, construa, crie

Canais

Mais ações

Criar um canal
Adicionar canal

Mensagens diretas

Mais ações

Nova mensagem
giuliano rodrigues zelatecnologia
Você
Nova mensagem

Espaços

Configurações do Spaces
Todas as tarefas - GRUPO AM
Compartilhado comigo

Faça mais em GRUPO AM

Veja todos os espaços públicos e listas
Colabore livremente entre as equipes
Automatize o trabalho com agentes de IA
Não deixe atualizações importantes passarem. Ative as notificações em tempo real.


Compartilhado comigo
/

Adicionar descrição
Alterar prioridade
Alterar datas de início e vencimento

Adicionar canal e iniciar o SyncUp

Mais opções
Overview
Lista
Quadro
Documento
Documento
PB





Fluxos de funcionamento e protocolos

Documento com os fluxos operacionais revisados do Grupo Urias. O PDF anexo reúne os fluxos de contas e pagamentos, ocorrências do rebanho e a proposta de compras e cotações para validação.

Mais ações

Mais ações


Lista com marcadores
Todas as listas
Negrito
Itálico
Sublinhado
Riscado
Código
Insira um link
Você não pode criar subpáginas aqui. Destaque apenas o texto para criar subpáginas.
Você não pode criar tarefas aqui. Destaque apenas o texto para criar tarefas.
Mais configurações



Recolher
MD



# Nexo - especificação textual dos fluxos

**Revisão de 05/11/2026. Proposta funcional para alinhamento, sem contrato de API fechado.**

## Objetivo e fronteira do protótipo

Transformar mensagens dos grupos autorizados em registros pesquisáveis e verificáveis de aprovação de pagamento e ocorrência do rebanho. WhatsApp é a interface cotidiana. O painel permite consulta e revisão; os dois canais mostram o mesmo registro e sua evidência.

Compras/cotações é a terceira frente proposta, ainda a confirmar. Lançamentos em Realtec/AgroHUB, pagamento e conciliação são atividades humanas da rotina atual. O protótipo organiza e encaminha informações; integração futura depende de definição própria.

## Caminho comum de entrada e decisão

1. Receber mensagem somente do escopo autorizado e persistir o evento com origem antes da interpretação. Mensagens alheias à operação não devem ser encaminhadas indiscriminadamente ao agente ou à nuvem. Encaminhamento do privado é manual e precisa de contexto.
2. Vincular texto, anexo e transcrição à mensagem; mostrar mídia indisponível ou falha de captura/processamento como pendência recuperável.
3. Identificar o caso e propor campos como rascunho. Preservar a data da mensagem, a data de captura e, separadamente, a data do fato quando conhecida.
4. Identificar uma decisão explícita do autorizador competente, ligada ao caso, valor e ação. Isso é diferente da revisão do registro: o revisor valida se a extração e o vínculo da evidência estão corretos. Registrar ambas as ações mesmo quando forem feitas pela mesma pessoa.
5. Dado insuficiente, decisão ambígua ou conflito mantém o caso pendente. Perguntar no WhatsApp ao responsável: Conta AP-012, fornecedor X, R$ Y: posso registrar o pagamento como autorizado?. Resposta só vale se o autor tiver competência para essa etapa. Silêncio e resumo sem confirmação explícita não aprovam itens.
6. Após revisão humana, publicar a versão validada na base compartilhada. Painel e agente consultam a mesma decisão, versão e evidência. Toda correção acrescenta histórico e, quando mudar valor ou escopo relevante, exige revalidação.

## Fluxo 1 - Aprovação de pagamento

**Entrada:** nota, boleto, foto, áudio, texto ou pedido de pagamento no grupo.

**Rascunho:** descrição, valor, favorecido e destino/base; vencimento e documento quando aplicáveis. Serviço informal sem nota pode seguir com descrição e evidência da conversa; falta de nota não é recusa automática.

**Decisão:** identificar a autorização da etapa. Referência documental: Augusto concentra a maioria das compras, Heloísa responde por obras e Seu Augusto conforme responsabilidade. A matriz deve ser validada antes de aplicada pelo sistema. Compra autorizada não significa pagamento autorizado.

**Saídas possíveis:** aguardando dados; aguardando autorização/esclarecimento; recusa registrada; aprovação identificada aguardando revisão; registro aprovado e revisado para encaminhamento ao escritório. Alteração do pedido após recusa ou aprovação cria versão com nova análise.

**Continuidade humana:** escritório lança no Realtec na rota normal; Heloísa paga, guarda comprovante, dá baixa e concilia mensalmente por base. Pagamento direto por Seu Augusto é exceção com comprovante/explicação e lançamento retroativo. Esses passos podem ser acompanhados se houver evidência e confirmação humana; não são executados automaticamente pelo protótipo.

## Fluxo 2 - Ocorrência do rebanho

**Entrada:** relato do campo/Augusto com evento, data, fazenda/local, lote e quantidade/peso quando aplicáveis.

**Tratamento:** agente propõe registro e relaciona evidências. Responsável confere; falta de dado, duplicidade possível ou conflito gera pergunta e mantém pendente.

**Saída:** ocorrência revisada e validada para encaminhamento aos controles existentes. Capataz registra compras, vendas, movimentações e pesagens no AgroHUB; Augusto registra mortes/consumo e mantém planilha. Atualizações externas são manuais neste recorte.

**Limite:** evento validado não prova estoque conciliado. Diferenças entre físico, AgroHUB e planilha precisam de apuração antes de corrigir saldos.

## Fluxo 3 - Compras/cotações (confirmar inclusão)

Pedido com item, quantidade, destino e prazo ? escritório completa dados ? consulta habituais e pesquisa alternativas se necessário ? compara item, fornecedor, preço, prazo e condições ? autorizador escolhe ? escritório emite pedido e acompanha entrega. Sem decisão, manter pendente; recusa encerra ou retorna à cotação. Pagamento segue o Fluxo 1. Recebimento exige confirmação de item/marca, quantidade, recebedor e evidência; acompanhar entregas parciais e saldo. Validar se é necessária autorização antes de cotar.

## Modelo e estados sugeridos para apoiar esses fluxos

- **Mensagem/evento de origem:** conta, conversa, ID externo, tipo de evento, remetente, datas e conteúdo disponível. Encaminhador e autor original são campos distintos quando houver informação.
- **Anexo/transcrição:** vínculo à origem, localização, disponibilidade e versão; hash de arquivo é proposta técnica.
- **Caso:** tipo, campos, responsável, versão e vínculos a todas as mensagens relacionadas.
- **Decisão de negócio:** autor, ação, escopo/valor, resultado e evidência. Falta de decisão é pendência; não é uma aprovação ou recusa.
- **Revisão do registro:** quem conferiu a interpretação, quando e qual versão validou.
- **Histórico:** alterações, decisões anteriores, motivos e eventos de correção.

Estados abaixo são sugestões, não requisitos de nomes de tabelas ou endpoints:

- Registro: rascunho ? aguardando dados/esclarecimento ? pronto para revisão ? validado, com retorno à revisão após correção.
- Decisão de autorização: não identificada / pendente / aprovada / recusada / cancelada. Reversão registra novo evento e nova versão.
- Execução financeira: não informada / lançada / paga / baixada / conciliada, somente quando confirmada por evidência e responsável. Pagamento direto pode ocorrer antes do lançamento: não impor uma sequência rígida que impeça essa exceção.
- Duplicidade, divergência e falha de mídia são sinalizações próprias; não substituem o status de autorização ou de revisão.

## Duplicidade e recuperação - proposta a validar

Separar reentrega técnica do mesmo evento de um novo envio real. Idempotência impede que a mesma reentrega produza outro registro. Uma atualização/edição/exclusão recebida para o mesmo ID deve acrescentar histórico conforme a política definida, não ser descartada por compartilhar o ID.

Novo envio de arquivo igual conserva mensagem, data e remetente. Hash igual identifica conteúdo igual; não prova mesma despesa, transação ou rateio. Proposta inicial: marcar possíveis duplicidades e pedir revisão, sem excluir envios nem fundir casos automaticamente. Conservar os vínculos se o revisor confirmar que vários envios sustentam um mesmo caso.

Checkpoint e retentativa devem ser desenhados com o responsável pelo conector: avançar a leitura somente após persistência durável e permitir retomada sem duplicação. Não tratar recebimento de webhook isolado como prova de captura completa. Essa é uma condição a verificar no piloto, não capacidade já comprovada.

## Fonte comum e funcionamento da interface

Base operacional compartilhada para mensagens, casos, decisões, revisões e evidências. SQL foi sugerido na reunião; produto/arquitetura serão definidos pelo time. A memória de sessão do agente/Pi não equivale automaticamente à base de decisões. O painel e as respostas do agente precisam usar a versão vigente do mesmo registro, com permissões de acesso.

Sugestão para o painel: fila de rascunhos/pendências, lista de registros validados, detalhe do caso com origem e histórico, filtros por tipo e motivo da pendência. Não impor login no painel a todos: esclarecimentos e consultas devem poder ocorrer pelo WhatsApp. Consultas à equipe são alternativa. Resumos diários/semanais e horários são propostas a validar.

WACLI foi escolhido internamente; a conta dedicada no Android foi preparada. Cloudflare com Pi Durable é direção provável, não implantação confirmada. Conector, armazenamento operacional e memória do agente têm responsabilidades distintas; arquitetura e segurança de acesso precisam de definição técnica.

## Aceite proposto para o piloto

1. Toda decisão publicada como validada tem autorizador competente, caso/ação/valor quando aplicável, evidência e revisão humana registrada.
2. Dúvida, silêncio ou resposta sem vínculo suficiente mantém pendência; uma consulta mostra por que o caso está pendente.
3. Reprocessar eventos não duplica registros; novos envios reais e correções continuam rastreáveis.
4. Agente e painel exibem o mesmo caso, versão, decisão e evidência. Quando a base estiver indisponível, sinalizar a falha em vez de apresentar memória como estado confirmado.
5. Exceções de pagamento direto e serviços sem nota são representáveis sem falsa autorização automática.
6. Falhas de captura/mídia ficam visíveis, com recuperação ou revisão. Testar com amostra histórica pequena antes da captura contínua.

**A validar:** grupos/participantes, matriz de alçadas, campos por evento, política de duplicidades e de edição/exclusão, acesso/retenção, responsáveis e frequência das perguntas/resumos, terceiro fluxo e contratos das APIs existentes. Alinhar estes requisitos com as telas e APIs iniciais mencionadas na reunião, sem presumir que já os atendam.