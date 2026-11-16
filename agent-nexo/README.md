# Agente Nexo — protótipo WhatsApp do Grupo Urias

Rascunho funcional para iniciar testes do **Agente Nexo**. A estrutura aplica a ideia de agente organizado em arquivos: identidade e instruções, regras operacionais, ferramentas permitidas e cenários de avaliação.

## Estado

- Escopo do teste: simulação de aprovação de contas e pagamentos, com revisão humana.
- Canal: começar por conversa simulada; ligar ao WhatsApp somente após validar a conta/número, grupos autorizados e mecanismo de captura.
- Persistência: cada conversa/caso deve manter histórico e estado recuperável. Pi Durable pode apoiar sessões duráveis; isso não configura por si só integração WhatsApp, autenticação, interface de revisão ou modelo de dados operacional.
- Sistemas externos: Realtec, banco e dashboard ficam fora da execução automática deste teste. O agente apenas organiza dados e indica a próxima ação humana.
- Ambiente: este pacote é especificação e massa de teste; não contém credenciais nem integração executável.

## Arquivos

- `agent/instructions.md`: comportamento base do agente.
- `agent/rules/approval-and-payment.md`: interpretação e limites do processo.
- `agent/tools/allowed-tools.md`: permissões e ações vedadas no protótipo.
- `tests/scenario-realistic.md`: conversa simulada e resultado esperado.
- `tests/DoD.md`: definição de pronto para avaliar a demonstração.

## Proposta de demonstração

Executar uma conversa curta em que o solicitante envia uma conta, alguém autorizado aprova com mensagem inequívoca e o agente registra a evidência e pede revisão dos dados, sem pedir a mesma autorização outra vez. Depois repetir com “ok” ambíguo, recusa e documento reenviado. Conferir em cada rodada o que foi extraído, o estado do caso, a origem e a justificativa.

Base funcional: fluxos do Grupo Urias revisados em 06/11/2026. Os papéis e alçadas descritos precisam de validação operacional antes de configurar um agente real.
