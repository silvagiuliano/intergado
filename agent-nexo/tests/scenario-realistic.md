# Cenário de avaliação: aprovação de uma conta

## Preparação

Caso de teste `CT-001`. A competência do remetente “Augusto” para aprovar esta conta é considerada confirmada apenas dentro desta simulação. Anexo fictício: `NF_Sementes_Alfa_2480.pdf`.

## Conversa simulada

**09:02 — Larissa, escritório:** Recebi a NF da Sementes Alfa para a Fazenda Boa Vista. Total R$ 2.480, vencimento 20/11. Estou encaminhando o documento.

**09:02 — Anexo:** `NF_Sementes_Alfa_2480.pdf`.

**09:04 — Agente Nexo:** Registrei a conta em rascunho: Sementes Alfa, R$ 2.480, Fazenda Boa Vista, vencimento 20/11. Documento vinculado. Aguardando decisão de pagamento.

**09:10 — Augusto:** Aprovado pagar essa nota da Sementes Alfa, R$ 2.480.

**09:10 — Agente Nexo:** Identifiquei a autorização de pagamento de Augusto para a conta CT-001 e vinculei a mensagem como evidência. Não vou pedir a autorização novamente. O registro está aguardando revisão dos dados e do vínculo da decisão. Larissa, pode revisar no painel de teste?

**09:13 — Larissa:** Conferi, está correto.

**09:13 — Agente Nexo:** CT-001 validado e encaminhado para a rotina financeira. O sistema ainda não confirma lançamento no Realtec, pagamento, baixa ou conciliação.

## Estado esperado após a conversa

- Um único caso CT-001.
- Registro validado depois da revisão humana.
- Decisão aprovada, com remetente, hora, mensagem e relação com a conta preservados.
- Execução: não informada / lançamento pendente.
- Anexo associado sem recriar ou editar o original.
- O agente não pediu autorização duplicada e não declarou que o pagamento aconteceu.

## Variações obrigatórias

1. Trocar a resposta de Augusto por “ok 👍” sem contexto claro: agente faz uma pergunta específica e mantém a decisão pendente.
2. Enviar a mesma nota duas vezes por falha simulada de entrega: não criar novo caso; registrar reentrega técnica.
3. Enviar novamente o mesmo arquivo como nova mensagem: preservar as duas origens e sinalizar possível duplicidade para revisão.
4. Responder “não aprovo; confirmar o vencimento primeiro”: decisão recusada ou suspensa conforme a regra confirmada; não encaminhar para pagamento.
5. Enviar comprovante de PIX sem confirmação humana: evidência anexada, mas execução permanece aguardando confirmação de pagamento.
