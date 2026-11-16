# Preparação da VPS e pareamento do WhatsApp

Este guia prepara o primeiro teste do agente. O Android já configurado continua sendo o aparelho principal; o pareamento será feito como dispositivo conectado depois que o serviço estiver pronto no servidor.

## Antes de parear

1. Confirmar qual VPS será usada, sistema operacional, endereço e usuário SSH.
2. Confirmar que o número dedicado será usado no piloto e adicionar somente contatos/grupo de teste. Remover da conta de teste conversas que não devam ser copiadas.
3. Preparar no servidor um usuário de serviço sem privilégios administrativos, diretório de dados acessível somente por esse usuário, volume persistente, firewall e rotina de backup protegida.
4. Instalar e validar o wacli no servidor, conferir `wacli doctor` e configurar limites de armazenamento antes do pareamento.
5. Preparar o receptor de eventos com segredo, permitir somente o grupo de teste e rejeitar os demais eventos antes de encaminhá-los ao agente. A filtragem no receptor não impede que o próprio wacli armazene as mensagens sincronizadas localmente.
6. Configurar o agente em modo simulado/read-only: sem envio de mensagens, sem escrita no Realtec ou banco e sem automações de pagamento.

## Parear pelo Android

1. Na VPS, abrir uma sessão de terminal segura e iniciar `wacli auth` no diretório de dados dedicado.
2. No Android: abrir WhatsApp, tocar em **⋮ → Dispositivos conectados → Conectar dispositivo**.
3. Escanear o QR que aparece no terminal da VPS e aprovar o novo dispositivo.
4. Manter o comando de autenticação ativo até a sincronização inicial ficar ociosa. Depois, conferir `wacli auth status`.
5. Iniciar a sincronização contínua somente após confirmar armazenamento, limites, grupo permitido, receptor e logs. No piloto, não habilitar download de mídia amplo.
6. Enviar uma mensagem fictícia no grupo de teste e confirmar que apenas ela chega ao agente, com remetente, grupo, horário e conteúdo; não responder automaticamente.

## O que pedir ao Rubens

- Endereço/IP ou alias SSH da VPS de teste e usuário.
- Acesso SSH autorizado para o computador que fará a implantação. Não compartilhar senha, chave privada ou QR do WhatsApp no chat.

## Configuração que faremos no projeto

Depois que o acesso à VPS estiver liberado, configurar por aqui o Pi Durable e os componentes necessários no Cloudflare, conectar o receptor seguro de eventos ao agente e validar o teste. Para isso, será necessário acesso autorizado à conta/zona Cloudflare e definir onde ficam as credenciais de execução. O grupo e o celular de teste já estão disponíveis com Paulo.

## Limite de escopo

O wacli vincula o número como dispositivo conectado e mantém mensagens numa base SQLite local. A documentação consultada não mostra filtro nativo de sincronização inicial por grupo; portanto, um receptor que filtra eventos protege o agente, mas não evita sozinho que outras conversas sejam armazenadas pelo wacli. A sincronização do histórico é descrita como best effort. Avaliar cuidadosamente a conta dedicada antes de escanear o QR.

Referências: [wacli Quickstart](https://wacli.sh/quickstart.html), [Auth e pareamento](https://wacli.sh/auth.html), [Sync, armazenamento e webhooks](https://wacli.sh/sync.html).
