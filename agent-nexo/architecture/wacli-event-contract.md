# Contrato de entrada: eventos do wacli

## Finalidade

Definir o formato mínimo entre o wacli e o Agente Nexo para o teste do fluxo de contas. O evento representa uma mensagem recebida; sozinho, não representa uma conta validada, uma autorização ou uma confirmação financeira.

## Campos usados

| Campo do wacli | Uso no agente |
|---|---|
| `Chat` | Identificar o grupo/conversa e aplicar a lista permitida antes de processar o conteúdo |
| `ID` | Identificador da mensagem; deduplicação técnica junto com `Chat` |
| `SenderJID` | Identificar o remetente da mensagem no grupo; validar alçada com configuração externa |
| `Timestamp` | Data/hora de origem em RFC 3339/UTC |
| `FromMe` | Distinguir mensagens enviadas pela conta vinculada; não usar como identidade do autor humano |
| `Text` | Texto recebido, legenda ou texto vazio quando houver apenas mídia |
| `ChatName` | Nome resolvido localmente, se disponível; o JID continua sendo a chave estável |
| `Media` | Metadados da mídia, quando presentes; buscar o arquivo apenas por fluxo explícito e limitado |

## JSON Schema do evento consumido

O schema intencionalmente permite propriedades adicionais para compatibilidade com versões futuras do wacli. O receptor deve descartar ou colocar em quarentena eventos cujo `Chat` não esteja na lista permitida antes de enviar qualquer texto ou mídia ao modelo.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "WacliMessageEvent",
  "type": "object",
  "required": ["Chat", "ID", "SenderJID", "Timestamp", "FromMe", "Text"],
  "properties": {
    "Chat": { "type": "string", "minLength": 1 },
    "ID": { "type": "string", "minLength": 1 },
    "SenderJID": { "type": "string", "minLength": 1 },
    "Timestamp": { "type": "string", "format": "date-time" },
    "FromMe": { "type": "boolean" },
    "Text": { "type": "string" },
    "ChatName": { "type": "string" },
    "Media": {
      "oneOf": [
        { "type": "null" },
        {
          "type": "object",
          "properties": {
            "Type": { "type": "string" },
            "Caption": { "type": "string" },
            "Filename": { "type": "string" },
            "MimeType": { "type": "string" },
            "FileLength": { "type": "integer", "minimum": 0 }
          },
          "additionalProperties": true
        }
      ]
    }
  },
  "additionalProperties": true
}
```

## Regras de recepção

1. Validar assinatura HMAC do webhook antes de ler/processar o corpo.
2. Verificar `Chat` contra allowlist configurada no servidor; não aceitar o nome visível do grupo como controle de acesso.
3. Usar `Chat + ID` como chave de idempotência para reentregas técnicas.
4. Preservar payload bruto permitido e metadados de origem com retenção definida; não baixar mídia automaticamente no primeiro piloto.
5. Normalizar o evento sem apagar os dados originais; encaminhar para o caso somente depois da verificação de grupo.
6. Não converter `FromMe`, `SenderJID` ou encaminhamento em prova de autorização. A competência é validada pela matriz de alçadas e pelo conteúdo relacionado à conta.
7. Não encaminhar receipts ou presence ao modelo como mensagem operacional. O contrato de piloto aceita apenas eventos `message`.

## Exemplo fictício

```json
{
  "Chat": "120363000000000001@g.us",
  "ID": "MSG-TEST-001",
  "SenderJID": "5511999990000@s.whatsapp.net",
  "Timestamp": "2026-11-16T12:02:00Z",
  "FromMe": false,
  "Text": "Recebi a nota da Sementes Alfa, R$ 2.480, vencimento dia 20.",
  "ChatName": "Sandbox do LAB",
  "Media": null
}
```

Exemplo sintético. Os IDs e o conteúdo não correspondem a mensagens reais.

## Fonte técnica

Formato baseado na documentação de [webhooks do wacli](https://wacli.sh/sync.html#webhook-payloads). A documentação informa que a entrega do webhook é best effort; o receptor não deve tratar o webhook sozinho como prova de captura integral.
