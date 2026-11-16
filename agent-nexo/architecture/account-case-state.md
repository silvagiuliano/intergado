# Caso de conta: estados e transições

Este desenho converte o fluxo revisado do Grupo Urias em estados verificáveis para o Agente Nexo. A aprovação do negócio, a revisão do registro e a execução financeira são dimensões distintas.

## Estrutura mínima do caso

```json
{
  "case_id": "CT-001",
  "case_type": "payment",
  "record_status": "draft",
  "decision_status": "pending",
  "execution_status": "not_reported",
  "fields": {
    "payee": null,
    "amount": null,
    "due_date": null,
    "farm_or_cost_center": null,
    "description": null
  },
  "evidence_refs": [],
  "pending_questions": [],
  "review": null,
  "history": []
}
```

Os nomes acima são rótulos conceituais para o piloto; o modelo definitivo será alinhado à implementação do dashboard.

## Dimensões de status

| Dimensão | Valores do piloto | Regra |
|---|---|---|
| Registro | `draft`, `in_review`, `validated`, `needs_clarification` | Só revisão humana publica a versão como validada |
| Decisão | `pending`, `approved`, `rejected`, `adjustment_requested` | Requer mensagem ligada à conta e pessoa com alçada configurada |
| Execução | `not_reported`, `entered_in_realtec`, `paid`, `written_off`, `reconciled`, `discrepancy` | Cada passagem exige evidência/confirmação própria; não inferir etapa seguinte |

## Transições principais

1. **Mensagem autorizada recebida:** criar ou atualizar um rascunho; adicionar referência de evidência; deixar campos inferidos como sugestões.
2. **Campo essencial ausente/conflitante:** adicionar pergunta ao mesmo caso e marcar `needs_clarification`; resposta complementa o caso.
3. **Autorização explícita de pessoa competente:** atualizar `decision_status` para `approved` e registrar a evidência; manter `record_status=in_review` até revisão humana. Não pedir a mesma autorização de novo.
4. **Resposta ambígua ou remetente sem alçada confirmada:** manter `decision_status=pending`, explicitar motivo e pedir esclarecimento ao responsável.
5. **Recusa:** atualizar `decision_status=rejected`, preservar o motivo e não encaminhar para pagamento.
6. **Revisão humana aprovada:** registrar revisor, horário, versão e resultado; `record_status=validated`.
7. **Confirmação do escritório/financeiro:** atualizar somente a etapa executada (lançamento, pagamento, baixa ou conciliação) e anexar evidência.
8. **Divergência:** manter execução em `discrepancy`, com pendência/responsável; fechar somente após conferência humana registrada.

## Invariantes para os evals

- A mesma mensagem entregue de novo não cria caso novo (`Chat + ID`).
- Uma mensagem nova semelhante mantém as duas origens e sinaliza possível duplicidade; sem fusão automática.
- O agente não marca registro validado sem revisão humana.
- O agente não chama valor inferido de confirmado nem inventa dado ausente.
- Aprovação de compra não substitui autorização de pagamento.
- Comprovante recebido não confirma sozinho pagamento; pagamento não confirma baixa; baixa não confirma conciliação.
- O agente e o dashboard devem ler e escrever a mesma versão do caso quando a integração existir.

## Casos de avaliação vinculados ao fluxo

| ID | Entrada | Resultado esperado |
|---|---|---|
| EVAL-01 | Nota + autorização explícita e remetente autorizado | Criar/atualizar caso, preservar origem, registrar decisão, pedir revisão uma vez |
| EVAL-02 | “ok” sem referência clara | Perguntar qual conta/ação; manter decisão pendente |
| EVAL-03 | Reentrega com mesmo Chat e ID | Idempotente; um caso e uma referência de mensagem |
| EVAL-04 | Novo envio com conteúdo/anexo semelhante | Preservar ambos e sinalizar possível duplicidade |
| EVAL-05 | Recusa ou pedido de ajuste | Registrar decisão e motivo; não avançar para financeiro |
| EVAL-06 | Comprovante sem confirmação do responsável | Anexar evidência; execução ainda não confirmada |
| EVAL-07 | Divergência banco/Realtec | Manter pendência para conferência manual |
| EVAL-08 | Remetente sem alçada mapeada | Não aceitar como autorização; pedir confirmação ao responsável |

Fonte de processo: fluxo revisado do Grupo Urias, especialmente as seções de estados separados, autorização, revisão e execução financeira. A implementação deve preservar essas distinções.
