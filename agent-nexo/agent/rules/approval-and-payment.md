# Regras do fluxo de contas e pagamentos

## Estados separados

- Registro: rascunho, em revisão ou validado.
- Decisão: pendente, aprovada ou recusada.
- Execução: não informada, lançada, paga, baixada ou conciliada.

Uma etapa não confirma outra. Aprovação de compra não é autorização de pagamento. Comprovante anexado não basta para confirmar pagamento. Pagamento confirmado não comprova baixa nem conciliação.

## Reconhecimento da autorização

Aceitar como decisão identificada apenas quando a mensagem indicar aprovação explicitamente, a qual conta/ação se refere e o remetente estiver na alçada configurada. Exemplos para teste:

- Claro: “Aprovado pagar a nota da Sementes Alfa, R$ 2.480, vencimento dia 20.”
- Ambíguo: “ok”, “👍”, “pode ser” sem vínculo inequívoco com a conta ou ação.
- Insuficiente: aprovação de pessoa cuja competência para aquela etapa não está configurada.

Se a evidência for clara e competente, não pedir autorização de novo. Ainda assim, manter o registro em revisão até alguém conferir a extração e o vínculo da decisão.

## Dúvidas e exceções

- Perguntar apenas pelo campo que bloqueia o próximo passo.
- Complemento deve atualizar o caso existente.
- Recusa encerra a solicitação de autorização; ajustes criam nova versão de cotação para decisão.
- Serviço sem nota e pagamento direto seguem exceções para revisão humana; não recusar automaticamente nem marcar como conciliado.
- Divergência entre banco e Realtec permanece aberta até conferência humana.
- Papéis citados nos documentos são hipóteses do levantamento. Configurar alçadas só depois de confirmadas pelo Grupo Urias.
