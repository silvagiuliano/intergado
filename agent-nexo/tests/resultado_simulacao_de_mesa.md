# Simulação de mesa — caso Augusto

**Tipo:** revisão manual das instruções e do cenário. Não executada em agente, WhatsApp, VPS ou Pi Durable.

**Fluxo de referência:** especificação revisada do Grupo Urias, seção 4 (contas e pagamentos), com o fluxo comum de entrada, decisão, revisão e acompanhamento. O teste percorre as etapas nessa ordem; a conversa no WhatsApp não substitui o registro do caso no dashboard.

## Caso-base

**09:02 — Larissa:** Recebi a NF da Sementes Alfa para a Fazenda Boa Vista. Total R$ 2.480, vencimento 20/11. Segue o documento `NF_Sementes_Alfa_2480.pdf`.

**Resposta esperada do Agente Nexo:** Registrei o rascunho CT-001: Sementes Alfa, R$ 2.480, Fazenda Boa Vista, vencimento 20/11. Documento vinculado à mensagem de Larissa, recebida às 09:02. Aguardando decisão de pagamento.

**Dashboard após a entrada:** caso criado automaticamente em rascunho; origem, remetente, horário e documento visíveis; campos extraídos identificados como sugeridos; fila mostra “aguardando decisão”.

**09:10 — Augusto:** Aprovado pagar essa nota da Sementes Alfa, R$ 2.480.

**Resposta esperada do Agente Nexo:** Identifiquei a aprovação de pagamento da conta CT-001 e vinculei a mensagem de Augusto, recebida às 09:10, como evidência. Não vou pedir a autorização novamente. O registro aguarda revisão dos dados e do vínculo da decisão.

**Dashboard após a decisão:** decisão “aprovada” registrada separadamente do status do registro e da execução; mensagem de Augusto preservada como evidência; revisão da versão aparece na fila, sem solicitar uma segunda autorização.

**09:13 — Larissa:** Conferi, está correto.

**Resposta esperada do Agente Nexo:** CT-001 validado e encaminhado para a rotina financeira. Lançamento no Realtec, pagamento, baixa e conciliação continuam sem confirmação.

**Estado final esperado:** um caso; registro validado; decisão aprovada; execução ainda não lançada; documento e mensagens de origem preservados. O próximo passo é o escritório executar a rotina externa e registrar confirmações específicas. O sistema não avança os estados de Realtec, pagamento, baixa ou conciliação sem evidência/confirmação de cada etapa.

## Verificação por etapa do fluxo

| Etapa do fluxo | O que observar no teste | Estado esperado |
|---|---|---|
| Receber | Mensagem, remetente, grupo, data/hora e anexo ficam ligados à entrada | Entrada recebida |
| Interpretar e relacionar | Campos extraídos são rastreáveis; caso certo criado/atualizado; possível duplicidade sinalizada | Rascunho ou pendência, nunca fato validado por inferência |
| Identificar decisão | A mensagem de aprovação aponta para a conta e ação; competência do remetente é checada contra a alçada de teste | Decisão aprovada ou pendente |
| Revisar | Pessoa confere a versão consolidada e a evidência no dashboard ou canal de teste | Registro validado ou devolvido para esclarecimento |
| Encaminhar | Dados e documentos ficam reunidos para a equipe financeira; faltas continuam visíveis | Lançamento externo ainda não informado |
| Acompanhar | Cada confirmação atualiza somente sua etapa | Lançado, pago, baixado e conciliado são estados independentes |

## Variações

### 1. Resposta ambígua

**Mensagem:** Augusto responde apenas “ok 👍”.

**Resposta esperada:** “Augusto, esse ‘ok’ aprova o pagamento da nota da Sementes Alfa, de R$ 2.480, para a Fazenda Boa Vista?”

**Estado esperado:** dashboard mostra “decisão pendente” e o motivo; não tratar silêncio como confirmação. A resposta deve atualizar o mesmo caso.

### 2. Reentrega técnica

**Situação:** o mesmo evento da mensagem chega duas vezes por repetição da conexão.

**Resposta esperada:** atualizar/confirmar a recepção do evento sem abrir outro caso e conservar a referência da mensagem.

**Estado esperado:** continua um único caso; evento técnico registrado sem duplicação; dashboard não aumenta a quantidade de contas recebidas.

### 3. Novo envio do mesmo documento

**Situação:** Larissa envia o mesmo PDF novamente numa nova mensagem.

**Resposta esperada:** “Recebi outro envio do documento que parece corresponder à conta CT-001. Mantive as duas mensagens de origem e marquei para conferência.”

**Estado esperado:** sem fusão ou exclusão automática; possível duplicidade visível na fila para revisão.

### 4. Recusa ou pedido de ajuste

**Mensagem:** Augusto responde “Não aprovo ainda; preciso confirmar o vencimento.”

**Resposta esperada:** registrar a decisão como não aprovada no momento e a justificativa; manter o caso pendente de confirmação do vencimento. Não encaminhar ao financeiro.

**Estado esperado:** estado de decisão e motivo registrados; histórico preservado; caso não segue para lançamento/pagamento.

### 5. Comprovante sem confirmação

**Situação:** chega uma imagem de comprovante PIX sem mensagem confirmando a execução.

**Resposta esperada:** vincular a imagem como evidência recebida e informar que o pagamento ainda precisa ser confirmado pelo responsável financeiro.

**Estado esperado:** documento recebido; situação de pagamento permanece não confirmada; baixa e conciliação continuam pendentes no dashboard.

## Resultado da revisão

As instruções cobrem os cinco comportamentos principais: aprovação inequívoca sem repetição, pergunta para ambiguidade, distinção entre reentrega técnica e novo envio, registro de recusa/ajuste e separação entre comprovante e confirmação de pagamento.

**Não validado nesta simulação:** qualidade real de leitura de PDF/imagem ou áudio; identificação de remetente e alçada pela integração; persistência após reinício; captura seletiva do grupo; controles de acesso; comportamento de um modelo em execução. Esses pontos dependem da implementação e de testes técnicos no ambiente.
