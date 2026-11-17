

1. Quando alguém da fazenda precisa comprar um produto, peça ou insumo, o pedido
chega ao escritório principalmente por WhatsApp. Esse pedido pode vir de Augusto,
de um gerente, de um agrônomo ou da equipe de campo. (Podemos transcrever texto, audio e imagens)
2. O escritório identifica o que está sendo pedido e tenta entender exatamente qual
produto, peça ou insumo precisa ser comprado. (Resumo com LLM, ou outra IA)
3. Se a informação ainda não estiver clara, Larissa ou o escritório pede mais detalhes
por WhatsApp, como foto, lista, confirmação ou especificação da peça. (Supervisão humana do que foi feito pela IA)
4. Com o pedido entendido, o escritório consulta primeiro os fornecedores habituais. ( Pesquisa automática na base de dados OpenSearchIA de pedidos anteriores semelhantes )
5. Se os fornecedores habituais não resolvem, o escritório procura alternativas fora da lista conhecida, usando cidade, internet ou indicação. ( Pesquisa web com IA apresentando melhores sugestões)
6. Quando há uma cotação viável, o escritório reúne a informação e pede autorização a Augusto ou ao responsável aplicável. (Podemos deixar algo já pré montado)
7. Depois da autorização, o escritório faz o pedido ao fornecedor. ( Apresentamos o que foi autorizado com todo o fluxo já percorrido)
8. O fornecedor entrega o item na fazenda. ( Pode ser recebido uma mensagem, foto ou audio da confirmação )
9. Quando a entrega acontece, o escritório é avisado. ( Pode ser recebido uma mensagem, foto ou audio da confirmação )
10. Com a entrega confirmada, o processo segue para pagamento e documentação. ( Geramos todo o pacote de documentação para pagamento)

## Fluxo de atendimento e oportunidades de melhoria

```mermaid
flowchart TD
    INICIO([Necessidade de compra na fazenda]) --> E1["1. Receber o pedido pelo WhatsApp"]
    E1 --> O1["Oportunidade: transcrever textos,<br/>áudios e imagens"]
    O1 --> E2["2. Identificar e interpretar o item solicitado"]
    E2 --> O2["Oportunidade: resumir a solicitação<br/>com LLM ou outra IA"]
    O2 --> E3["3. Solicitar detalhes adicionais pelo WhatsApp"]
    E3 --> O3["Oportunidade: supervisão humana<br/>do trabalho realizado pela IA"]
    O3 --> E4["4. Consultar fornecedores habituais"]
    E4 --> O4["Oportunidade: pesquisar automaticamente<br/>pedidos semelhantes no OpenSearch"]
    O4 --> E5["5. Pesquisar fornecedores alternativos"]
    E5 --> O5["Oportunidade: pesquisa web com IA<br/>e apresentação das melhores sugestões"]
    O5 --> E6["6. Reunir a cotação e solicitar autorização"]
    E6 --> O6["Oportunidade: deixar a solicitação<br/>de autorização pré-montada"]
    O6 --> E7["7. Fazer o pedido ao fornecedor"]
    E7 --> O7["Oportunidade: apresentar o que foi autorizado<br/>com todo o fluxo percorrido"]
    O7 --> E8["8. Receber o item na fazenda"]
    E8 --> O8["Oportunidade: receber a confirmação<br/>por mensagem, foto ou áudio"]
    O8 --> E9["9. Avisar o escritório sobre a entrega"]
    E9 --> O9["Oportunidade: receber a confirmação<br/>por mensagem, foto ou áudio"]
    O9 --> E10["10. Encaminhar pagamento e documentação"]
    E10 --> O10["Oportunidade: gerar todo o pacote<br/>de documentação para pagamento"]
    O10 --> FIM([Processo concluído])

    classDef etapa fill:#e8f1ff,stroke:#2563eb,color:#172554;
    classDef oportunidade fill:#ecfdf5,stroke:#059669,color:#064e3b;
    class E1,E2,E3,E4,E5,E6,E7,E8,E9,E10 etapa;
    class O1,O2,O3,O4,O5,O6,O7,O8,O9,O10 oportunidade;
```

## Diagrama de sequência proposto

O diagrama abaixo foi elaborado com apoio dos POPs 01 a 04 do documento
[Diagnóstico preliminar dos processos](./03_Diagnostico_Preliminar_Processos_POPs.pdf).
Ele mostra a interação entre as
pessoas, os sistemas e os fornecedores desde a solicitação da compra até o pagamento
e a conciliação. A automação apoia a operação, mas o escritório continua responsável
por validar o entendimento do pedido e conduzir as decisões.

```mermaid
sequenceDiagram
    autonumber
    actor SOL as Solicitante / Fazenda
    participant CANAL as WhatsApp / E-mail
    participant IA as Assistente de IA
    actor ESC as Larissa / Escritório
    participant BASE as OpenSearch / Histórico
    participant WEB as Pesquisa web
    actor APR as Augusto / Responsável
    actor FOR as Fornecedor
    participant REAL as Realtec
    actor FIN as Heloísa / Financeiro
    participant BANCO as Banco

    Note over IA,WEB: IA, OpenSearch e pesquisa assistida representam oportunidades propostas

    rect rgb(232, 241, 255)
    Note over SOL,ESC: Entrada e entendimento do pedido — POP 01
    SOL->>CANAL: Envia pedido por WhatsApp (texto, áudio ou imagem)
    CANAL->>IA: Encaminha o conteúdo recebido
    IA->>IA: Transcreve, extrai dados e resume o pedido
    IA-->>ESC: Apresenta solicitação estruturada para revisão
    ESC->>ESC: Valida o entendimento da IA

    loop Enquanto o pedido precisar de detalhes
        ESC->>CANAL: Solicita foto, lista, especificação ou confirmação
        CANAL->>SOL: Entrega a solicitação de detalhes
        SOL->>CANAL: Envia as informações complementares
        CANAL->>IA: Encaminha o novo conteúdo
        IA-->>ESC: Atualiza a solicitação estruturada
        ESC->>ESC: Revisa novamente o pedido
    end
    end

    rect rgb(236, 253, 245)
    Note over ESC,FOR: Cotação e autorização da compra — POP 01
    ESC->>BASE: Pesquisa compras e fornecedores semelhantes
    BASE-->>ESC: Retorna histórico e fornecedores habituais
    ESC->>FOR: Solicita cotação
    FOR-->>ESC: Informa disponibilidade, preço e prazo

    alt Fornecedor habitual não atende
        ESC->>IA: Solicita pesquisa de alternativas
        IA->>WEB: Pesquisa por cidade, internet ou indicação
        WEB-->>IA: Retorna possíveis fornecedores
        IA-->>ESC: Organiza e compara as sugestões
        ESC->>FOR: Solicita cotação alternativa
        FOR-->>ESC: Informa disponibilidade, preço e prazo
    else Existe cotação viável
        ESC->>ESC: Consolida a cotação recebida
    end

    IA-->>ESC: Gera solicitação de autorização pré-montada
    ESC->>APR: Envia cotação revisada para decisão

    alt Compra autorizada
        APR-->>ESC: Aprova a compra
        ESC->>FOR: Confirma o pedido autorizado
        FOR-->>ESC: Confirma o pedido e a previsão de entrega
    else Ajustes solicitados ou compra recusada
        APR-->>ESC: Informa a decisão e a justificativa
        ESC->>ESC: Registra a decisão e revisa a cotação, se necessário
    end
    end

    opt Compra autorizada e pedido confirmado
        rect rgb(255, 247, 237)
        Note over ESC,FOR: Entrega e confirmação — POP 01
        FOR->>SOL: Entrega o item na fazenda
        SOL->>CANAL: Avisa que a entrega aconteceu
        Note over SOL,CANAL: Responsável e canal da confirmação ainda devem ser validados
        CANAL->>IA: Encaminha texto, foto ou áudio da confirmação
        IA-->>ESC: Organiza a evidência e sinaliza a entrega
        end

        rect rgb(245, 243, 255)
        Note over FOR,FIN: Documentação e lançamento — POPs 02 e 03
        alt Documento enviado pelo fornecedor
            FOR->>CANAL: Envia nota, boleto ou solicitação de pagamento
        else Documento enviado pela fazenda ou responsável
            SOL->>CANAL: Envia recibo, comprovante, PIX ou solicitação
        end
        CANAL->>IA: Encaminha a documentação recebida
        IA-->>ESC: Identifica o tipo e extrai os dados do documento
        ESC->>ESC: Valida a documentação disponível
        alt Existe nota, boleto ou comprovante formal
            ESC->>ESC: Imprime e guarda o documento físico
        else Não existe documento formal
            ESC->>ESC: Separa o PIX, recibo ou pedido recebido
        end
        ESC->>REAL: Registra a compra ou o pagamento
        opt Lançamento envolve produto, peça, combustível, máquina ou bem
            REAL->>REAL: Atualiza o controle de estoque ou bens
        end
        opt Custo precisa ser separado por fazenda, cultura, pivô ou área
            ESC->>REAL: Informa o centro de custo adequado
        end
        REAL-->>ESC: Disponibiliza o registro para controle e baixa
        IA-->>ESC: Gera pacote documental pré-montado
        end

        rect rgb(254, 242, 242)
        Note over ESC,BANCO: Autorização do pagamento, baixa e conciliação — POP 04
        ESC->>APR: Encaminha pagamento preparado para autorização
        alt Responsável está no escritório
            APR-->>ESC: Autoriza com assinatura física
        else Responsável está fora do escritório
            APR-->>CANAL: Autoriza por WhatsApp
            CANAL-->>ESC: Entrega a autorização
        end
        ESC->>FIN: Encaminha pagamento autorizado
        FIN->>BANCO: Realiza o pagamento
        BANCO-->>FIN: Disponibiliza o comprovante
        FIN->>REAL: Faz a baixa do pagamento
        FIN->>BANCO: Consulta as movimentações bancárias
        BANCO-->>FIN: Retorna pagamentos, recebimentos e tarifas
        FIN->>REAL: Confere movimentos do banco com os registros
        alt Banco e Realtec estão consistentes
            FIN->>FIN: Conclui a conciliação
        else Existe divergência
            FIN->>FIN: Encaminha o caso para conferência manual
        end
        end
    end
```

> Pontos do diagnóstico que ainda exigem validação: quem pode autorizar além de
> Augusto; se existe uma lista formal de fornecedores habituais; quem avisa o
> escritório sobre a entrega e por qual canal; e se Heloísa é a responsável financeira
> definitiva por toda a rotina.
